#!/usr/bin/env bash
# Provision (or update) the Julebrus stack. Run on the OPERATOR's laptop with an AWS profile that may create IAM, RDS, EC2,
# S3, Secrets Manager, SNS and CloudWatch resources in the account. Nothing is created until you type DEPLOY.
#   PROFILE=<aws-profile> bash 00-provision.sh <alarm-email> [UseElasticIp=true|false]
# The profile must be given (PROFILE or AWS_PROFILE); there is no default.
# Optional environment:
#   GITHUB_SUBJECT_PREFIX=<prefix>   GitHub OIDC subject prefix if the organisation uses the immutable format (default repo:Cantara/julebrus)
#   INSTANCE_TYPE=t4g.small          instance type (create), or a deliberate change (update)
#   DB_DELETION_PROTECTION=true      last runbook step: turn on RDS deletion protection through an update
#   NEW_AMI=true                     update only: resolve the latest AL2023 AMI again and pass it (this REPLACES the instance, see README)
# UseElasticIp (second argument) counts for the first create only; on an update the previous value is kept.
# Steps: stack state -> preflight (read-only) -> change set -> shows exactly what will change -> confirm -> deploy -> outputs.
set -euo pipefail
ALARM_EMAIL="${1:?usage: PROFILE=<aws-profile> 00-provision.sh <alarm-email> [true|false]}"; EIP="${2:-true}"
PROFILE="${PROFILE:-${AWS_PROFILE:-}}"; [ -n "$PROFILE" ] || { echo "Set PROFILE (or AWS_PROFILE) to the AWS CLI profile to use."; exit 1; }
REGION="${REGION:-eu-north-1}"; STACK="${STACK:-julebrus}"; ITYPE="${INSTANCE_TYPE:-}"
[[ "$ALARM_EMAIL" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo "'$ALARM_EMAIL' is not a plain e-mail address."; exit 1; }
[[ "$EIP" = true || "$EIP" = false ]] || { echo "UseElasticIp must be true or false"; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"; TEMPLATE="$HERE/../template.yaml"
A=(aws --profile "$PROFILE" --region "$REGION")
ACCT="$("${A[@]}" sts get-caller-identity --query Account --output text)"
echo "Account $ACCT, region $REGION, stack $STACK"
STATUS="$("${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].StackStatus' --output text 2>/dev/null || true)"
[ "$STATUS" != None ] || STATUS=""
echo "== stack state: ${STATUS:-does not exist}"
TYPE=UPDATE
delete_stack() {
  read -r -p "Type DELETESTACK to delete the stack '$STACK' (it holds nothing that was kept; retained resources stay): " ANSWER </dev/tty
  [ "$ANSWER" = DELETESTACK ] || { echo "Cancelled."; exit 0; }
  "${A[@]}" cloudformation delete-stack --stack-name "$STACK"; "${A[@]}" cloudformation wait stack-delete-complete --stack-name "$STACK"
}
case "$STATUS" in
  "") TYPE=CREATE ;;
  REVIEW_IN_PROGRESS) TYPE=CREATE; echo "  an earlier run left the stack waiting for its first change set; a new change set is created"
    read -r -p "  Type DELETESTACK to delete it and start clean, or just press Enter to continue: " ANSWER </dev/tty
    if [ "$ANSWER" = DELETESTACK ]; then "${A[@]}" cloudformation delete-stack --stack-name "$STACK"; "${A[@]}" cloudformation wait stack-delete-complete --stack-name "$STACK"; fi ;;
  ROLLBACK_COMPLETE) echo "  The first create failed and was rolled back; the stack holds no resources and cannot be updated. Look at why:"
    echo "    aws --profile $PROFILE --region $REGION cloudformation describe-stack-events --stack-name $STACK --query \"StackEvents[?ResourceStatus=='CREATE_FAILED'].[LogicalResourceId,ResourceStatusReason]\" --output text"
    delete_stack; echo "Deleted. Fix the cause, then run this script again."; exit 0 ;;
  CREATE_FAILED) echo "  CREATE_FAILED: the create stopped without rolling back. Read the events (describe-stack-events), then delete the stack in the console or CLI and run this script again."; exit 1 ;;
  ROLLBACK_FAILED|DELETE_FAILED|UPDATE_ROLLBACK_FAILED) echo "  $STATUS needs a human: open the stack's events in the console, fix or skip the resources that cannot be changed (ContinueUpdateRollback / retry delete), then run this script again."; exit 1 ;;
  *_IN_PROGRESS) echo "  $STATUS: wait for it to finish, then run this script again."; exit 1 ;;
  CREATE_COMPLETE|UPDATE_COMPLETE|UPDATE_ROLLBACK_COMPLETE) ;;
  *) echo "  unexpected state $STATUS: not touching it."; exit 1 ;;
esac
echo "  change set type: $TYPE"
echo "== preflight (read-only)"
bad=0
# Names that would collide with a NEW stack, whatever the status of an earlier one (retained buckets and secrets survive a deleted stack).
if [ "$TYPE" = CREATE ]; then
  for chk in \
    "bucket julebrus-artifacts-$ACCT|${A[*]} s3api head-bucket --bucket julebrus-artifacts-$ACCT" \
    "role julebrus-instance-role|${A[*]} iam get-role --role-name julebrus-instance-role" \
    "role julebrus-release-uploader|${A[*]} iam get-role --role-name julebrus-release-uploader" \
    "database julebrus-db|${A[*]} rds describe-db-instances --db-instance-identifier julebrus-db" \
    "secret julebrus/db|${A[*]} secretsmanager describe-secret --secret-id julebrus/db"; do
    name="${chk%%|*}"; cmd="${chk#*|}"
    if $cmd >/dev/null 2>&1; then echo "  already exists: $name"; bad=1; else echo "  free: $name"; fi
  done
fi
OIDC_ARN="arn:aws:iam::$ACCT:oidc-provider/token.actions.githubusercontent.com"
if "${A[@]}" iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" >/dev/null 2>&1; then
  echo "  GitHub OIDC provider present"
else echo "  MISSING: GitHub OIDC provider $OIDC_ARN (create it first)"; bad=1; fi
AMI=""
if [ "$TYPE" = CREATE ] || [ "${NEW_AMI:-}" = true ]; then
  VPC="$("${A[@]}" ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"
  [ -n "$VPC" ] && [ "$VPC" != None ] || { echo "  no default VPC in $REGION"; exit 1; }
  echo "  default VPC: $VPC"
  AMI="$("${A[@]}" ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 --query Parameter.Value --output text)"
  echo "  AMI (latest AL2023 arm64 today): $AMI"
fi
if [ "$TYPE" = CREATE ]; then
  ITYPE="${ITYPE:-t4g.small}"
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
fi
[ "$bad" = 0 ] || { echo "STOPPED: see the lines above. Nothing was created."; exit 1; }
echo "== change set"
CS="julebrus-$(date +%Y%m%d%H%M%S)"
PARAMS="$(mktemp)"; trap 'rm -f "$PARAMS"' EXIT
P() { printf '{"ParameterKey":"%s","ParameterValue":"%s"}' "$1" "$2"; }   # values were validated above or come from the account itself
U() { printf '{"ParameterKey":"%s","UsePreviousValue":true}' "$1"; }
{
  printf '['
  P AlarmEmail "$ALARM_EMAIL"; printf ','
  if [ "$TYPE" = CREATE ]; then
    P UseElasticIp "$EIP"; printf ','; P InstanceType "$ITYPE"; printf ','; P AmiId "$AMI"; printf ','
    P DbDeletionProtection "${DB_DELETION_PROTECTION:-false}"; printf ','
    P VpcId "$VPC"; printf ','; P AppSubnetId "$APP_SUBNET"; printf ','; P DbSubnetIds "$DB_SUBNETS"; printf ','
    P GitHubOidcProviderArn "$OIDC_ARN"
    [ -z "${GITHUB_SUBJECT_PREFIX:-}" ] || { printf ','; P GitHubSubjectPrefix "$GITHUB_SUBJECT_PREFIX"; }
  else
    # Never move or replace an existing instance, address or database by accident: previous values unless asked.
    U UseElasticIp; printf ','
    if [ -n "$ITYPE" ]; then P InstanceType "$ITYPE"; else U InstanceType; fi; printf ','
    if [ -n "$AMI" ]; then P AmiId "$AMI"; else U AmiId; fi; printf ','
    if [ -n "${DB_DELETION_PROTECTION:-}" ]; then P DbDeletionProtection "$DB_DELETION_PROTECTION"; else U DbDeletionProtection; fi; printf ','
    U VpcId; printf ','; U AppSubnetId; printf ','; U DbSubnetIds; printf ','; U GitHubOidcProviderArn; printf ','
    if [ -n "${GITHUB_SUBJECT_PREFIX:-}" ]; then P GitHubSubjectPrefix "$GITHUB_SUBJECT_PREFIX"; else U GitHubSubjectPrefix; fi
  fi
  printf ']'
} > "$PARAMS"
"${A[@]}" cloudformation create-change-set --stack-name "$STACK" --change-set-name "$CS" --change-set-type "$TYPE" --template-body "file://$TEMPLATE" \
  --capabilities CAPABILITY_NAMED_IAM --parameters "file://$PARAMS" >/dev/null
if ! "${A[@]}" cloudformation wait change-set-create-complete --stack-name "$STACK" --change-set-name "$CS" 2>/dev/null; then
  reason="$("${A[@]}" cloudformation describe-change-set --stack-name "$STACK" --change-set-name "$CS" --query StatusReason --output text 2>/dev/null || true)"
  if grep -qiE "didn't contain changes|No updates" <<<"$reason"; then
    "${A[@]}" cloudformation delete-change-set --stack-name "$STACK" --change-set-name "$CS" || true
    echo "No changes: the stack already matches the template and parameters."; exit 0
  fi
  echo "The change set failed: $reason"; exit 1
fi
echo "This would create / change (Replacement True = the resource is destroyed and recreated):"
"${A[@]}" cloudformation describe-change-set --stack-name "$STACK" --change-set-name "$CS" --query 'Changes[].ResourceChange.[Action,LogicalResourceId,ResourceType,Replacement]' --output text | column -t
read -r -p "Type DEPLOY to execute this change set (anything else cancels and deletes the change set): " ANSWER </dev/tty
if [ "$ANSWER" != DEPLOY ]; then
  "${A[@]}" cloudformation delete-change-set --stack-name "$STACK" --change-set-name "$CS"
  echo "Cancelled; nothing was changed. (A new stack stays in REVIEW_IN_PROGRESS; the next run handles that.)"; exit 0
fi
"${A[@]}" cloudformation execute-change-set --stack-name "$STACK" --change-set-name "$CS"
echo "Deploying (a new RDS instance takes about 10 minutes)..."
if [ "$TYPE" = CREATE ]; then "${A[@]}" cloudformation wait stack-create-complete --stack-name "$STACK"; else "${A[@]}" cloudformation wait stack-update-complete --stack-name "$STACK"; fi
echo "== outputs"
"${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].Outputs[].[OutputKey,OutputValue]' --output text | column -t
cat <<NEXT

Next (see README, runbook): set-github-variables.sh, the DNS A record (ElasticIp above), confirm the SNS e-mail sent to $ALARM_EMAIL.
Last runbook step, after the first release works:  DB_DELETION_PROTECTION=true PROFILE=$PROFILE bash 00-provision.sh $ALARM_EMAIL
NEXT
