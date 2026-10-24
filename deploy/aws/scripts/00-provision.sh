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
if [ "$MODE" = cleanup ]; then
  # No override at all for this mode: it only ever works on the stack literally named julebrus.
  if [ -n "${JULEBRUS_STACK:-}" ] && [ "$JULEBRUS_STACK" != julebrus ]; then echo "cleanup-leftovers works on the stack 'julebrus' only; unset JULEBRUS_STACK."; exit 1; fi
  STACK=julebrus
elif [[ "$STACK" != julebrus* ]]; then
  read -r -p "The stack name '$STACK' does not start with 'julebrus'. Type OTHERSTACK to use it anyway: " w </dev/tty; [ "$w" = OTHERSTACK ] || { echo "Cancelled."; exit 1; }
fi
[ -z "$ALARM_NEW" ] || [[ "$ALARM_NEW" =~ ^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]] || { echo "'$ALARM_NEW' is not a plain e-mail address."; exit 1; }
[ -z "$EIP" ] || [ "$EIP" = true ] || [ "$EIP" = false ] || { echo "--elastic-ip must be true or false"; exit 1; }
[ -z "${GITHUB_SUBJECT_PREFIX:-}" ] || [[ "$GITHUB_SUBJECT_PREFIX" =~ ^repo:[A-Za-z0-9._@/-]+$ ]] || { echo "GITHUB_SUBJECT_PREFIX must look like repo:Owner/name (letters, digits . _ @ / -)"; exit 1; }
[ -z "$ITYPE" ] || [[ "$ITYPE" =~ ^[a-z0-9]+\.[a-z0-9]+$ ]] || { echo "INSTANCE_TYPE must look like t4g.small"; exit 1; }
[ -z "${DB_DELETION_PROTECTION:-}" ] || [ "$DB_DELETION_PROTECTION" = true ] || [ "$DB_DELETION_PROTECTION" = false ] || { echo "DB_DELETION_PROTECTION must be true or false"; exit 1; }
[ -z "${NEW_AMI:-}" ] || [ "$NEW_AMI" = true ] || { echo "NEW_AMI must be true or unset"; exit 1; }
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
HERE="$(cd "$(dirname "$0")" && pwd)"; TEMPLATE="$HERE/../template.yaml"
A=(aws --profile "$PROFILE" --region "$REGION")
ACCT="$("${A[@]}" sts get-caller-identity --query Account --output text)"
echo "Account $ACCT, region $REGION, stack $STACK"

# probe PATTERN CMD...: runs CMD. Returns 0 if it succeeded (stdout in PROBE_OUT), 1 if it failed with an error matching PATTERN
# (= "this thing does not exist"), 2 for ANY other error (AccessDenied, throttling, expired token, network...), which is
# printed. Callers must treat 2 as "unknown" and stop; unknown is never "absent".
probe() {
  local pat="$1" o rc; shift
  o="$("$@" 2>"$TMP/probe.err")" && { PROBE_OUT="$o"; return 0; }
  rc=$?; PROBE_OUT=""
  if grep -Eq "$pat" "$TMP/probe.err"; then return 1; fi
  echo "  ABORT: could not tell whether this exists (exit $rc): $(head -c 300 "$TMP/probe.err" | tr '\n' ' ')" >&2; return 2
}
# stack_status [NAME]: prints the status; empty = the stack does not exist; any other error aborts (never mistaken for "no stack").
stack_status() {
  local name="${1:-$STACK}" rc=0
  probe "does not exist" "${A[@]}" cloudformation describe-stacks --stack-name "$name" --query 'Stacks[0].StackStatus' --output text || rc=$?
  if [ "$rc" = 0 ]; then echo "$PROBE_OUT"; elif [ "$rc" = 1 ]; then echo ""; else exit 1; fi
}

# ---- cleanup-leftovers ------------------------------------------------------------------------------------------------
# Two independent proofs of ownership before anything is deleted: (1) the Project=julebrus tag, (2) the CloudFormation tag
# aws:cloudformation:stack-name, which must name a stack that is gone. Plus: the literal stack 'julebrus' is gone and the
# database julebrus-db is really absent (only DBInstanceNotFound counts). Run before the listing and AGAIN after the typed word.
CL_BUCKET="julebrus-artifacts-$ACCT"; CL_SECRET="julebrus/db"; CL_DB="julebrus-db"
tag_of() { # tag_of bucket|secret KEY -> prints the value, or nothing
  case "$1" in
    bucket) "${A[@]}" s3api get-bucket-tagging --bucket "$CL_BUCKET" --query "TagSet[?Key=='$2'].Value" --output text 2>/dev/null ;;
    secret) "${A[@]}" secretsmanager describe-secret --secret-id "$CL_SECRET" --query "Tags[?Key=='$2'].Value" --output text 2>/dev/null ;;
  esac
}
check_ownership() { # check_ownership bucket|secret -> 0 ours and its stack is gone, 1 stop
  local what="$1" proj cfn rc
  proj="$(tag_of "$what" Project)" || proj=""
  cfn="$(tag_of "$what" aws:cloudformation:stack-name)" || cfn=""
  if [ "$proj" != julebrus ]; then echo "  STOPPED: the $what is not tagged Project=julebrus, so it is not ours to delete."; return 1; fi
  if [ -z "$cfn" ] || [ "$cfn" = None ]; then echo "  STOPPED: the $what has no aws:cloudformation:stack-name tag, so I cannot prove which stack it belonged to. No override: delete it by hand if you are sure."; return 1; fi
  rc=0; probe "does not exist" "${A[@]}" cloudformation describe-stacks --stack-name "$cfn" --query 'Stacks[0].StackStatus' --output text || rc=$?
  if [ "$rc" = 0 ]; then echo "  STOPPED: the $what belongs to stack '$cfn', which exists (status $PROBE_OUT). Nothing is removed while that stack exists."; return 1; fi
  if [ "$rc" = 2 ]; then return 1; fi
  echo "  ok: the $what is tagged Project=julebrus and belonged to stack '$cfn', which is gone"; return 0
}
CL_DEL_BUCKET=0; CL_DEL_SECRET=0
cleanup_preconditions() { # prints what it finds; sets CL_DEL_BUCKET / CL_DEL_SECRET; returns 1 to stop
  local rc
  CL_DEL_BUCKET=0; CL_DEL_SECRET=0
  rc=0; probe "does not exist" "${A[@]}" cloudformation describe-stacks --stack-name julebrus --query 'Stacks[0].StackStatus' --output text || rc=$?
  if [ "$rc" = 0 ]; then echo "  STOPPED: the stack julebrus exists ($PROBE_OUT). Nothing is removed while it exists."; return 1; fi
  [ "$rc" = 1 ] || return 1
  echo "  ok: stack julebrus does not exist"
  rc=0; probe "DBInstanceNotFound" "${A[@]}" rds describe-db-instances --db-instance-identifier "$CL_DB" || rc=$?
  if [ "$rc" = 0 ]; then echo "  STOPPED: the database $CL_DB exists. Its password is the secret $CL_SECRET; nothing is removed."; return 1; fi
  [ "$rc" = 1 ] || return 1
  echo "  ok: database $CL_DB does not exist (DBInstanceNotFound)"
  rc=0; probe "404|NoSuchBucket|Not Found" "${A[@]}" s3api head-bucket --bucket "$CL_BUCKET" || rc=$?
  if [ "$rc" = 0 ]; then check_ownership bucket || return 1; CL_DEL_BUCKET=1; echo "  found: bucket $CL_BUCKET"
  elif [ "$rc" = 1 ]; then echo "  none: bucket $CL_BUCKET is not there"; else return 1; fi
  rc=0; probe "ResourceNotFoundException" "${A[@]}" secretsmanager describe-secret --secret-id "$CL_SECRET" --query Name --output text || rc=$?
  if [ "$rc" = 0 ]; then check_ownership secret || return 1; CL_DEL_SECRET=1; echo "  found: secret $CL_SECRET"
  elif [ "$rc" = 1 ]; then echo "  none: secret $CL_SECRET is not there"; else return 1; fi
  return 0
}
# Empties a versioned bucket. Fails on: a list error, per-key errors in a 200 delete-objects response, or a pass that made no progress.
empty_bucket() {
  local prev="" cur n errs
  while :; do
    "${A[@]}" s3api list-object-versions --bucket "$CL_BUCKET" --max-items 1000 --output json > "$TMP/list.json" 2> "$TMP/list.err" \
      || { echo "  ABORT: could not list the bucket: $(head -c 300 "$TMP/list.err" | tr '\n' ' ')"; return 1; }
    if [ ! -s "$TMP/list.json" ]; then break; fi
    jq -c '{Objects: ([(.Versions//[])[],(.DeleteMarkers//[])[]] | map({Key,VersionId}) | .[:1000]), Quiet: true}' "$TMP/list.json" > "$TMP/del.json" \
      || { echo "  ABORT: could not read the bucket listing"; return 1; }
    n="$(jq '.Objects|length' "$TMP/del.json")" || { echo "  ABORT: could not read the bucket listing"; return 1; }
    [ "$n" -gt 0 ] || break
    cur="$(sha256sum < "$TMP/del.json")"
    if [ "$cur" = "$prev" ]; then echo "  ABORT: no progress: the same $n object versions are still there after a delete pass"; return 1; fi
    prev="$cur"
    "${A[@]}" s3api delete-objects --bucket "$CL_BUCKET" --delete "file://$TMP/del.json" --output json > "$TMP/del.resp" 2> "$TMP/del.err" \
      || { echo "  ABORT: delete-objects failed: $(head -c 300 "$TMP/del.err" | tr '\n' ' ')"; return 1; }
    if [ -s "$TMP/del.resp" ]; then
      errs="$(jq '(.Errors//[])|length' "$TMP/del.resp")" || { echo "  ABORT: could not read the delete response"; return 1; }
      if [ "$errs" -gt 0 ]; then
        echo "  ABORT: $errs of $n deletes were refused, first error: $(jq -r '.Errors[0].Code' "$TMP/del.resp") (access denied, object lock?). Nothing more is deleted."; return 1
      fi
    fi
  done
  return 0
}
cleanup_leftovers() {
  local snaps
  echo "== leftovers check (read-only; operates on the literal stack name 'julebrus' only)"
  cleanup_preconditions || return 1
  snaps="$("${A[@]}" rds describe-db-snapshots --query "DBSnapshots[?starts_with(DBInstanceIdentifier,'$CL_DB')].DBSnapshotIdentifier" --output text 2>/dev/null || true)"
  [ -z "$snaps" ] || echo "  note: database snapshots exist (${snaps}); they are NOT deleted here (a rolled-back create may have left one). Review: aws rds describe-db-snapshots; delete with aws rds delete-db-snapshot if unwanted."
  if [ "$CL_DEL_BUCKET" = 0 ] && [ "$CL_DEL_SECRET" = 0 ]; then echo "  nothing to clean up."; return 0; fi
  echo "This will PERMANENTLY delete:"
  [ "$CL_DEL_BUCKET" = 0 ] || echo "  - S3 bucket $CL_BUCKET with every object version and delete marker in it"
  [ "$CL_DEL_SECRET" = 0 ] || echo "  - Secrets Manager secret $CL_SECRET, immediately, without recovery window"
  read -r -p "Type CLEANUP to delete exactly these (anything else cancels): " ANSWER </dev/tty
  [ "$ANSWER" = CLEANUP ] || { echo "Cancelled; nothing was deleted."; return 0; }
  echo "== re-checking every precondition before the first delete"
  cleanup_preconditions || { echo "  STOPPED at the re-check: the situation changed. Nothing was deleted."; return 1; }
  if [ "$CL_DEL_BUCKET" = 1 ]; then
    empty_bucket || return 1
    "${A[@]}" s3api delete-bucket --bucket "$CL_BUCKET" || { echo "  FAILED deleting bucket $CL_BUCKET"; return 1; }
    echo "  deleted bucket $CL_BUCKET"
  fi
  if [ "$CL_DEL_SECRET" = 1 ]; then
    "${A[@]}" secretsmanager delete-secret --secret-id "$CL_SECRET" --force-delete-without-recovery >/dev/null || { echo "  FAILED deleting secret $CL_SECRET"; return 1; }
    echo "  deleted secret $CL_SECRET"
  fi
  echo "Leftovers removed. A new first create can now run."
}

if [ "$MODE" = cleanup ]; then cleanup_leftovers; exit $?; fi

STATUS="$(stack_status)"
echo "== stack state: ${STATUS:-does not exist}"
TYPE=UPDATE
delete_stack() {
  local proj
  proj="$("${A[@]}" cloudformation describe-stacks --stack-name "$STACK" --query "Stacks[0].Tags[?Key=='Project'].Value" --output text)" || { echo "  could not read the stack's tags"; exit 1; }
  if [ "$proj" != julebrus ]; then echo "  REFUSING to delete '$STACK': it is not tagged Project=julebrus, so it does not look like ours."; exit 1; fi
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
PARAMS="$TMP/params.json"
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
  --capabilities CAPABILITY_NAMED_IAM --tags Key=Project,Value=julebrus --parameters "file://$PARAMS" >/dev/null
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
