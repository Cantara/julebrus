#!/usr/bin/env bash
# Provision (or update) the Julebrus stack, or clean up after a failed first create. Run on the OPERATOR's laptop with an
# AWS profile that may create IAM, RDS, EC2, S3, Secrets Manager, SNS and CloudWatch resources in the account.
# Nothing is created or deleted until you type a confirmation word (DEPLOY, DELETESTACK, CLEANUP).
#   PROFILE=<aws-profile> bash 00-provision.sh --alarm-email <addr> [--elastic-ip true|false]   first create
#   PROFILE=<aws-profile> bash 00-provision.sh                                                    update (previous values are kept)
#   PROFILE=<aws-profile> bash 00-provision.sh --alarm-email <addr>                               update and CHANGE the alarm address
#   PROFILE=<aws-profile> bash 00-provision.sh cleanup-leftovers                                  after a failed first create, see below
# The profile must be given (PROFILE or AWS_PROFILE); there is no default.
# Environment: JULEBRUS_STACK (default julebrus), JULEBRUS_REGION (default eu-north-1),
#   GITHUB_SUBJECT_PREFIX=<prefix>   GitHub OIDC subject prefix if the organisation uses the immutable format (default repo:Cantara/julebrus)
#   INSTANCE_TYPE=t4g.small          instance type (create), or a deliberate change (update)
#   DB_DELETION_PROTECTION=true      last runbook step: turn on RDS deletion protection through an update
#   NEW_AMI=true                     update only: resolve the latest AL2023 AMI again (this REPLACES the instance, see README)
# --elastic-ip counts for the first create only; on an update the previous value is kept.
# Why cleanup-leftovers: a failed first create rolls back, but the artifacts bucket and the julebrus/db secret are
# deliberately retained, and the next create would collide with their names. cleanup-leftovers deletes exactly those two
# (bucket: only if tagged Project=julebrus, with all object versions; secret: without recovery window), only if the stack
# and the julebrus-db database no longer exist, after listing them and asking for CLEANUP.
set -euo pipefail
usage() { sed -n 2,9p "$0"; exit 1; }
MODE=provision; ALARM_NEW=""; EIP=""
while [ $# -gt 0 ]; do
  case "$1" in
    cleanup-leftovers) MODE=cleanup ;;
    --alarm-email) ALARM_NEW="${2:-}"; [ -n "$ALARM_NEW" ] || usage; shift ;;
    --elastic-ip) EIP="${2:-}"; [ -n "$EIP" ] || usage; shift ;;
    *) usage ;;
  esac; shift
done
PROFILE="${PROFILE:-${AWS_PROFILE:-}}"; [ -n "$PROFILE" ] || { echo "Set PROFILE (or AWS_PROFILE) to the AWS CLI profile to use."; exit 1; }
REGION="${JULEBRUS_REGION:-eu-north-1}"; STACK="${JULEBRUS_STACK:-julebrus}"; ITYPE="${INSTANCE_TYPE:-}"
if [[ "$STACK" != julebrus* ]]; then
  read -r -p "The stack name '$STACK' does not start with 'julebrus'. Type OTHERSTACK to use it anyway: " w </dev/tty; [ "$w" = OTHERSTACK ] || { echo "Cancelled."; exit 1; }
fi
[ -z "$ALARM_NEW" ] || [[ "$ALARM_NEW" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo "'$ALARM_NEW' is not a plain e-mail address."; exit 1; }
[ -z "$EIP" ] || [ "$EIP" = true ] || [ "$EIP" = false ] || { echo "--elastic-ip must be true or false"; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"; TEMPLATE="$HERE/../template.yaml"
A=(aws --profile "$PROFILE" --region "$REGION")
ACCT="$("${A[@]}" sts get-caller-identity --query Account --output text)"
echo "Account $ACCT, region $REGION, stack $STACK"

# Empty string = the stack does not exist. Any other error aborts (never mistaken for "no stack").
stack_status() {
  local o
  if o="$("${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query 'Stacks[0].StackStatus' --output text 2>&1)"; then echo "$o"
  elif grep -q "does not exist" <<<"$o"; then echo ""
  else echo "Could not read the stack state: $o" >&2; exit 1; fi
}

cleanup_leftovers() {
  local BUCKET="julebrus-artifacts-$ACCT" SECRET="julebrus/db" DBID="julebrus-db" st tagp n doc snaps
  local del_bucket=0 del_secret=0
  echo "== leftovers check (read-only)"
  st="$(stack_status)" || return 1   # explicit: set -e is off inside a function called with || true
  if [ -n "$st" ]; then echo "  STOPPED: the stack $STACK still exists ($st). Delete it first; nothing is removed while it exists."; return 1; fi
  echo "  ok: stack $STACK does not exist"
  if "${A[@]}" rds describe-db-instances --db-instance-identifier "$DBID" >/dev/null 2>&1; then
    echo "  STOPPED: the database $DBID exists. Its password is the secret $SECRET; nothing is removed."; return 1; fi
  echo "  ok: database $DBID does not exist"
  if "${A[@]}" s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
    tagp="$("${A[@]}" s3api get-bucket-tagging --bucket "$BUCKET" --query "TagSet[?Key=='Project'].Value" --output text 2>/dev/null || true)"
    if [ "$tagp" != julebrus ]; then echo "  STOPPED: bucket $BUCKET is not tagged Project=julebrus, so it is not ours to delete."; return 1; fi
    n="$("${A[@]}" s3api list-object-versions --bucket "$BUCKET" --max-items 1000 --output json | jq '[(.Versions//[])[],(.DeleteMarkers//[])[]]|length' 2>/dev/null || echo 0)"
    echo "  found: bucket $BUCKET (tagged Project=julebrus; ${n:-0} object versions/delete markers in the first page)"; del_bucket=1
  else echo "  none: bucket $BUCKET is not there"; fi
  if "${A[@]}" secretsmanager describe-secret --secret-id "$SECRET" >/dev/null 2>&1; then
    tagp="$("${A[@]}" secretsmanager describe-secret --secret-id "$SECRET" --query "Tags[?Key=='Project'].Value" --output text 2>/dev/null || true)"
    if [ "$tagp" != julebrus ]; then echo "  STOPPED: secret $SECRET is not tagged Project=julebrus, so it is not ours to delete."; return 1; fi
    echo "  found: secret $SECRET (tagged Project=julebrus)"; del_secret=1
  else echo "  none: secret $SECRET is not there"; fi
  snaps="$("${A[@]}" rds describe-db-snapshots --query "DBSnapshots[?starts_with(DBInstanceIdentifier,'$DBID')].DBSnapshotIdentifier" --output text 2>/dev/null || true)"
  [ -z "$snaps" ] || echo "  note: database snapshots exist (${snaps}); they are NOT deleted here (a rolled-back create may have left one). Review them: aws rds describe-db-snapshots, and delete with aws rds delete-db-snapshot if unwanted."
  if [ "$del_bucket" = 0 ] && [ "$del_secret" = 0 ]; then echo "  nothing to clean up."; return 0; fi
  echo "This will PERMANENTLY delete:"
  [ "$del_bucket" = 0 ] || echo "  - S3 bucket $BUCKET with every object version and delete marker in it"
  [ "$del_secret" = 0 ] || echo "  - Secrets Manager secret $SECRET, immediately, without recovery window"
  read -r -p "Type CLEANUP to delete exactly these (anything else cancels): " ANSWER </dev/tty
  [ "$ANSWER" = CLEANUP ] || { echo "Cancelled; nothing was deleted."; return 0; }
  if [ "$del_bucket" = 1 ]; then
    while :; do
      doc="$("${A[@]}" s3api list-object-versions --bucket "$BUCKET" --max-items 1000 --output json | jq -c '{Objects: ([(.Versions//[])[],(.DeleteMarkers//[])[]] | map({Key,VersionId}) | .[:1000]), Quiet: true}' || true)"
      [ -n "$doc" ] && [ "$(jq '.Objects|length' <<<"$doc")" -gt 0 ] || break
      "${A[@]}" s3api delete-objects --bucket "$BUCKET" --delete "$doc" >/dev/null || { echo "  FAILED deleting objects in $BUCKET"; return 1; }
    done
    "${A[@]}" s3api delete-bucket --bucket "$BUCKET" || { echo "  FAILED deleting bucket $BUCKET (is it empty?)"; return 1; }
    echo "  deleted bucket $BUCKET"
  fi
  if [ "$del_secret" = 1 ]; then
    "${A[@]}" secretsmanager delete-secret --secret-id "$SECRET" --force-delete-without-recovery >/dev/null || { echo "  FAILED deleting secret $SECRET"; return 1; }
    echo "  deleted secret $SECRET"
  fi
  echo "Leftovers removed. A new first create can now run."
}

if [ "$MODE" = cleanup ]; then cleanup_leftovers; exit $?; fi

STATUS="$(stack_status)"
echo "== stack state: ${STATUS:-does not exist}"
TYPE=UPDATE
delete_stack() {
  echo "  Deleting the stack removes what is left of the failed create. It does NOT delete what the template retains on purpose:"
  echo "  the artifacts bucket and the julebrus/db secret (the next step lists and offers to remove them), and a database"
  echo "  snapshot, if a database had been created, stays for you to review."
  read -r -p "Type DELETESTACK to delete the stack '$STACK': " ANSWER </dev/tty
  [ "$ANSWER" = DELETESTACK ] || { echo "Cancelled."; exit 0; }
  "${A[@]}" cloudformation delete-stack --stack-name "$STACK"; "${A[@]}" cloudformation wait stack-delete-complete --stack-name "$STACK"
}
case "$STATUS" in
  "") TYPE=CREATE ;;
  REVIEW_IN_PROGRESS) TYPE=CREATE; echo "  an earlier run left the stack waiting for its first change set; a new change set is created"
    read -r -p "  Type DELETESTACK to delete it and start clean, or just press Enter to continue: " ANSWER </dev/tty
    if [ "$ANSWER" = DELETESTACK ]; then "${A[@]}" cloudformation delete-stack --stack-name "$STACK"; "${A[@]}" cloudformation wait stack-delete-complete --stack-name "$STACK"; fi ;;
  ROLLBACK_COMPLETE) echo "  The first create failed and was rolled back; the stack cannot be updated. Look at why:"
    echo "    aws --profile $PROFILE --region $REGION cloudformation describe-stack-events --stack-name $STACK --query \"StackEvents[?ResourceStatus=='CREATE_FAILED'].[LogicalResourceId,ResourceStatusReason]\" --output text"
    delete_stack; cleanup_leftovers || true
    echo "Fix the cause, then run this script again for a first create."; exit 0 ;;
  CREATE_FAILED) echo "  CREATE_FAILED: the create stopped without rolling back. Read the events (describe-stack-events), then delete the stack in the console or CLI, run 'cleanup-leftovers' and then this script again."; exit 1 ;;
  ROLLBACK_FAILED|DELETE_FAILED|UPDATE_ROLLBACK_FAILED) echo "  $STATUS needs a human: open the stack's events in the console, fix or skip the resources that cannot be changed (ContinueUpdateRollback / retry delete), then run this script again."; exit 1 ;;
  *_IN_PROGRESS) echo "  $STATUS: wait for it to finish, then run this script again."; exit 1 ;;
  CREATE_COMPLETE|UPDATE_COMPLETE|UPDATE_ROLLBACK_COMPLETE) ;;
  *) echo "  unexpected state $STATUS: not touching it."; exit 1 ;;
esac
echo "  change set type: $TYPE"
if [ "$TYPE" = CREATE ] && [ -z "$ALARM_NEW" ]; then echo "A first create needs --alarm-email <addr>."; exit 1; fi
if [ "$TYPE" = UPDATE ] && [ -n "$EIP" ]; then echo "  note: --elastic-ip counts for the first create only and is ignored on an update."; fi
echo "== preflight (read-only)"
bad=0
# Names that would collide with a NEW stack, whatever the status of an earlier one (retained resources survive a deleted stack).
if [ "$TYPE" = CREATE ]; then
  leftovers=0
  for chk in \
    "bucket julebrus-artifacts-$ACCT|1|${A[*]} s3api head-bucket --bucket julebrus-artifacts-$ACCT" \
    "secret julebrus/db|1|${A[*]} secretsmanager describe-secret --secret-id julebrus/db" \
    "role julebrus-instance-role|0|${A[*]} iam get-role --role-name julebrus-instance-role" \
    "role julebrus-release-uploader|0|${A[*]} iam get-role --role-name julebrus-release-uploader" \
    "database julebrus-db|0|${A[*]} rds describe-db-instances --db-instance-identifier julebrus-db"; do
    name="${chk%%|*}"; rest="${chk#*|}"; ours="${rest%%|*}"; cmd="${rest#*|}"
    if $cmd >/dev/null 2>&1; then
      bad=1
      if [ "$ours" = 1 ]; then echo "  already exists: $name (a retained leftover of an earlier failed create?)"; leftovers=1
      else echo "  already exists: $name (not something this script removes; look at it in the console)"; fi
    else echo "  free: $name"; fi
  done
  [ "$leftovers" = 0 ] || echo "  NEXT: PROFILE=$PROFILE bash $0 cleanup-leftovers   (checks every precondition, lists what it would delete, asks for CLEANUP)"
fi
OIDC_ARN="arn:aws:iam::$ACCT:oidc-provider/token.actions.githubusercontent.com"
if "${A[@]}" iam get-open-id-connect-provider --open-id-connect-provider-arn "$OIDC_ARN" >/dev/null 2>&1; then
  echo "  GitHub OIDC provider present"
else echo "  MISSING: GitHub OIDC provider $OIDC_ARN (see README, 'If the GitHub OIDC provider is missing')"; bad=1; fi
AMI=""
if [ "$TYPE" = CREATE ] || [ "${NEW_AMI:-}" = true ]; then
  VPC="$("${A[@]}" ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)"
  [ -n "$VPC" ] && [ "$VPC" != None ] || { echo "  no default VPC in $REGION"; exit 1; }
  echo "  default VPC: $VPC"
  AMI="$("${A[@]}" ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 --query Parameter.Value --output text)"
  echo "  AMI (latest AL2023 arm64 today): $AMI"
fi
if [ "$TYPE" = CREATE ]; then
  EIP="${EIP:-true}"; ITYPE="${ITYPE:-t4g.small}"
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
    if [ "$used" -ge "$limit" ]; then echo "  WARNING: at or over the limit; the allocation may be refused. Re-run with --elastic-ip false to use the instance's own IP, or raise the quota first."; fi
  fi
fi
[ "$bad" = 0 ] || { echo "STOPPED: see the lines above. Nothing was created."; exit 1; }
if [ "$TYPE" = UPDATE ] && [ -z "$ALARM_NEW" ]; then
  EFFECTIVE_ALARM="$("${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query "Stacks[0].Parameters[?ParameterKey=='AlarmEmail'].ParameterValue" --output text)"
  echo "  alarm address (unchanged): $EFFECTIVE_ALARM"
else EFFECTIVE_ALARM="$ALARM_NEW"; echo "  alarm address: $EFFECTIVE_ALARM"; fi
echo "== change set"
CS="julebrus-$(date +%Y%m%d%H%M%S)"
PARAMS="$(mktemp)"; trap 'rm -f "$PARAMS"' EXIT
P() { printf '{"ParameterKey":"%s","ParameterValue":"%s"}' "$1" "$2"; }   # values were validated above or come from the account itself
U() { printf '{"ParameterKey":"%s","UsePreviousValue":true}' "$1"; }
{
  printf '['
  if [ "$TYPE" = CREATE ]; then
    P AlarmEmail "$ALARM_NEW"; printf ','
    P UseElasticIp "$EIP"; printf ','; P InstanceType "$ITYPE"; printf ','; P AmiId "$AMI"; printf ','
    P DbDeletionProtection "${DB_DELETION_PROTECTION:-false}"; printf ','
    P VpcId "$VPC"; printf ','; P AppSubnetId "$APP_SUBNET"; printf ','; P DbSubnetIds "$DB_SUBNETS"; printf ','
    P GitHubOidcProviderArn "$OIDC_ARN"
    [ -z "${GITHUB_SUBJECT_PREFIX:-}" ] || { printf ','; P GitHubSubjectPrefix "$GITHUB_SUBJECT_PREFIX"; }
  else
    # Never move or replace an existing instance, address, database or name by accident: previous values unless asked.
    if [ -n "$ALARM_NEW" ]; then P AlarmEmail "$ALARM_NEW"; else U AlarmEmail; fi; printf ','
    for k in ProjectName AppHostName GitHubEnvironment DbInstanceClass DbAllocatedStorageGb DbBackupRetentionDays UseElasticIp VpcId AppSubnetId DbSubnetIds GitHubOidcProviderArn; do U "$k"; printf ','; done
    if [ -n "$ITYPE" ]; then P InstanceType "$ITYPE"; else U InstanceType; fi; printf ','
    if [ -n "$AMI" ]; then P AmiId "$AMI"; else U AmiId; fi; printf ','
    if [ -n "${DB_DELETION_PROTECTION:-}" ]; then P DbDeletionProtection "$DB_DELETION_PROTECTION"; else U DbDeletionProtection; fi; printf ','
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

Next (see README, runbook): set-github-variables.sh, the DNS A record (ElasticIp above), confirm the SNS e-mail sent to $EFFECTIVE_ALARM.
Last runbook step, after the first release works:  DB_DELETION_PROTECTION=true PROFILE=$PROFILE bash 00-provision.sh
NEXT
