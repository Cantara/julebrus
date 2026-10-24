#!/usr/bin/env bash
# Run on the OPERATOR's laptop after the stack is deployed: sets the two repository variables the release workflow
# reads, from the stack outputs. Until they are set, releases go to GitHub only. Needs the gh CLI, logged in with
# permission to write Actions variables on the repository.
set -euo pipefail
PROFILE="${PROFILE:-${AWS_PROFILE:-}}"; [ -n "$PROFILE" ] || { echo "Set PROFILE (or AWS_PROFILE) to the AWS CLI profile to use."; exit 1; }
REGION="${JULEBRUS_REGION:-eu-north-1}"; STACK="${JULEBRUS_STACK:-julebrus}"; REPO="${JULEBRUS_REPO:-Cantara/julebrus}"
if [[ "$STACK" != julebrus* ]]; then
  read -r -p "The stack name '$STACK' does not start with 'julebrus'. Type OTHERSTACK to use it anyway: " w </dev/tty; [ "$w" = OTHERSTACK ] || { echo "Cancelled."; exit 1; }
fi
out() { aws --profile "$PROFILE" --region "$REGION" cloudformation describe-stacks --stack-name "$STACK" --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text; }
role="$(out ReleaseUploaderRoleArn)"; bucket="$(out ArtifactsBucketName)"
echo "Will set on $REPO:  AWS_RELEASE_ROLE_ARN=$role  AWS_ARTIFACTS_BUCKET=$bucket"
read -r -p "Type VARIABLES to set them: " ANSWER </dev/tty
[ "$ANSWER" = VARIABLES ] || { echo "Cancelled."; exit 0; }
gh variable set AWS_RELEASE_ROLE_ARN --repo "$REPO" --body "$role"
gh variable set AWS_ARTIFACTS_BUCKET --repo "$REPO" --body "$bucket"
gh variable list --repo "$REPO"
