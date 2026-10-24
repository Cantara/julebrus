#!/usr/bin/env bash
# Provision the Julebrus stack. Run on the OPERATOR's laptop with an AWS profile that may create IAM, RDS, EC2, S3,
# SNS and CloudWatch resources in the account. Nothing is created until you type DEPLOY at the confirmation prompt.
#   PROFILE=neuron bash 00-provision.sh <alarm-email> [UseElasticIp=true|false]
# Optional: GITHUB_SUBJECT_PREFIX=<prefix>   the GitHub OIDC subject prefix if the organisation uses the immutable
#           format (see the template parameter); default repo:Cantara/julebrus.
#           INSTANCE_TYPE=t4g.small
# Steps: preflight (read-only) -> change set -> shows exactly what will be created -> confirm -> deploy -> outputs and next steps.
set -euo pipefail
ALARM_EMAIL="${1:?usage: 00-provision.sh <alarm-email> [true|false]}"; EIP="${2:-true}"
PROFILE="${PROFILE:-${AWS_PROFILE:-neuron}}"; REGION="${REGION:-eu-north-1}"; STACK="${STACK:-julebrus}"
ITYPE="${INSTANCE_TYPE:-t4g.small}"
HERE="$(cd "$(dirname "$0")" && pwd)"; TEMPLATE="$HERE/../template.yaml"
A=(aws --profile "$PROFILE" --region "$REGION")
ACCT="$("${A[@]}" sts get-caller-identity --query Account --output text)"
echo "Account $ACCT, region $REGION, stack $STACK"
echo "== preflight (read-only)"
bad=0
STATUS="$("${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || true)"
echo "  stack status: ${STATUS:-none}"
# The "name already exists" check only makes sense for a NEW stack; for an existing one the change set shows what changes.
if [ -z "$STATUS" ]; then
  for chk in \
    "bucket julebrus-artifacts-$ACCT|${A[*]} s3api head-bucket --bucket julebrus-artifacts-$ACCT" \
    "role julebrus-instance-role|${A[*]} iam get-role --role-name julebrus-instance-role" \
    "role julebrus-release-uploader|${A[*]} iam get-role --role-name julebrus-release-uploader" \
    "database julebrus-db|${A[*]} rds describe-db-instances --db-instance-identifier julebrus-db"; do
    name="${chk%%|*}"; cmd="${chk#*|}"
    if $cmd >/dev/null 2>&1; then echo "  already exists: $name"; bad=1; else echo "  free: $name"; fi
  done
fi
OIDC_ARN="arn:aws:iam::$ACCT:oidc-provider/token.actions.githubusercontent.com"
if "${A[@]}" iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" >/dev/null 2>&1; then
  echo "  GitHub OIDC provider present"
else echo "  MISSING: GitHub OIDC provider $OIDC_ARN (create it first, or pass its ARN by editing the parameters)"; bad=1; fi
VPC="$("${A[@]}" ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"
[ -n "$VPC" ] && [ "$VPC" != None ] || { echo "  no default VPC in $REGION"; exit 1; }
echo "  default VPC: $VPC"
OFFERED="$("${A[@]}" ec2 describe-instance-type-offerings --location-type availability-zone --filters "Name=instance-type,Values=$ITYPE" --query 'InstanceTypeOfferings[].Location' --output text)"
echo "  $ITYPE is offered in: ${OFFERED:-nowhere}"
APP_SUBNET=""; DB_SUBNETS=""
while read -r sid az; do
  [ -n "$sid" ] || continue
  DB_SUBNETS="${DB_SUBNETS:+$DB_SUBNETS,}$sid"
  if [ -z "$APP_SUBNET" ] && grep -qw "$az" <<<"$OFFERED"; then APP_SUBNET="$sid"; echo "  app subnet: $sid ($az)"; fi
done < <("${A[@]}" ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC" Name=default-for-az,Values=true --query 'Subnets[].[SubnetId,AvailabilityZone]' --output text)
[ -n "$APP_SUBNET" ] || { echo "  no default subnet in a zone that offers $ITYPE"; bad=1; }
[ "$(tr ',' '\n' <<<"$DB_SUBNETS" | grep -c .)" -ge 2 ] || { echo "  fewer than two default subnets: RDS needs two zones"; bad=1; }
if [ "$EIP" = true ]; then
  used="$("${A[@]}" ec2 describe-addresses --query 'length(Addresses)' --output text)"
  limit="$("${A[@]}" ec2 describe-account-attributes --attribute-names vpc-max-elastic-ips --query 'AccountAttributes[0].AttributeValues[0].AttributeValue' --output text)"
  echo "  Elastic IPs: $used in use, limit $limit"
  if [ "$used" -ge "$limit" ]; then echo "  WARNING: at or over the limit; the allocation may be refused. Re-run with UseElasticIp=false to use the instance's own IP, or raise the quota first."; fi
fi
[ "$bad" = 0 ] || { echo "STOPPED: see the lines above. Nothing was created."; exit 1; }
echo "== change set"
CS="julebrus-$(date +%Y%m%d%H%M%S)"; TYPE=CREATE; if [ -n "$STATUS" ] && [ "$STATUS" != None ]; then TYPE=UPDATE; fi
PARAMS="$(mktemp)"; trap 'rm -f "$PARAMS"' EXIT
{
  printf '['
  printf '{"ParameterKey":"AlarmEmail","ParameterValue":"%s"},' "$ALARM_EMAIL"
  printf '{"ParameterKey":"UseElasticIp","ParameterValue":"%s"},' "$EIP"
  printf '{"ParameterKey":"InstanceType","ParameterValue":"%s"},' "$ITYPE"
  if [ "$TYPE" = CREATE ]; then
    printf '{"ParameterKey":"VpcId","ParameterValue":"%s"},' "$VPC"
    printf '{"ParameterKey":"AppSubnetId","ParameterValue":"%s"},' "$APP_SUBNET"
    printf '{"ParameterKey":"DbSubnetIds","ParameterValue":"%s"},' "$DB_SUBNETS"
    printf '{"ParameterKey":"GitHubOidcProviderArn","ParameterValue":"%s"}' "$OIDC_ARN"
    [ -z "${GITHUB_SUBJECT_PREFIX:-}" ] || printf ',{"ParameterKey":"GitHubSubjectPrefix","ParameterValue":"%s"}' "$GITHUB_SUBJECT_PREFIX"
  else
    # Never move an existing instance or database to other subnets by accident.
    printf '{"ParameterKey":"VpcId","UsePreviousValue":true},{"ParameterKey":"AppSubnetId","UsePreviousValue":true},'
    printf '{"ParameterKey":"DbSubnetIds","UsePreviousValue":true},{"ParameterKey":"GitHubOidcProviderArn","UsePreviousValue":true}'
    if [ -n "${GITHUB_SUBJECT_PREFIX:-}" ]; then printf ',{"ParameterKey":"GitHubSubjectPrefix","ParameterValue":"%s"}' "$GITHUB_SUBJECT_PREFIX"
    else printf ',{"ParameterKey":"GitHubSubjectPrefix","UsePreviousValue":true}'; fi
  fi
  printf ']'
} > "$PARAMS"
"${A[@]}" cloudformation create-change-set --stack-name "$STACK" --change-set-name "$CS" --change-set-type "$TYPE" --template-body "file://$TEMPLATE" \
  --capabilities CAPABILITY_NAMED_IAM --parameters "file://$PARAMS" >/dev/null
"${A[@]}" cloudformation wait change-set-create-complete --stack-name "$STACK" --change-set-name "$CS"
echo "This would create / change (Replacement True = the resource is destroyed and recreated):"
"${A[@]}" cloudformation describe-change-set --stack-name "$STACK" --change-set-name "$CS" --query 'Changes[].ResourceChange.[Action,LogicalResourceId,ResourceType,Replacement]' --output text | column -t
read -r -p "Type DEPLOY to create these resources (anything else cancels and deletes the change set): " ANSWER </dev/tty
if [ "$ANSWER" != DEPLOY ]; then
  "${A[@]}" cloudformation delete-change-set --stack-name "$STACK" --change-set-name "$CS"
  if [ "$TYPE" = CREATE ]; then "${A[@]}" cloudformation delete-stack --stack-name "$STACK"; fi
  echo "Cancelled; nothing was created."; exit 0
fi
"${A[@]}" cloudformation execute-change-set --stack-name "$STACK" --change-set-name "$CS"
echo "Deploying (the RDS instance takes about 10 minutes)..."
if [ "$TYPE" = CREATE ]; then "${A[@]}" cloudformation wait stack-create-complete --stack-name "$STACK"; else "${A[@]}" cloudformation wait stack-update-complete --stack-name "$STACK"; fi
echo "== outputs"
"${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].Outputs[].[OutputKey,OutputValue]' --output text | column -t
cat <<NEXT

Next (see README, runbook step 3 onwards):
  bash set-github-variables.sh        # repo variables for the release workflow
  DNS: the A record xmas.cantara.no -> ElasticIp above
  confirm the SNS subscription e-mail sent to $ALARM_EMAIL
NEXT
