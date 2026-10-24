#!/usr/bin/env bash
# Run on the OPERATOR's laptop after the stack is deployed: sets the two repository variables the release workflow
# reads, from the stack outputs. Until they are set, releases go to GitHub only. Needs the gh CLI, logged in with
# permission to write Actions variables on the repository.
set -euo pipefail
PROFILE="${PROFILE:-${AWS_PROFILE:-default}}"; REGION="${REGION:-eu-north-1}"; STACK="${STACK:-julebrus}"; REPO="${REPO:-Cantara/julebrus}"
out() { aws --profile "$PROFILE" --region "$REGION" cloudformation describe-stacks --stack-name "$STACK" --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text; }
role="$(out ReleaseUploaderRoleArn)"; bucket="$(out ArtifactsBucketName)"
echo "Will set on $REPO:  AWS_RELEASE_ROLE_ARN=$role  AWS_ARTIFACTS_BUCKET=$bucket"
read -r -p "Type VARIABLES to set them: " ANSWER </dev/tty
[ "$ANSWER" = VARIABLES ] || { echo "Cancelled."; exit 0; }
gh variable set AWS_RELEASE_ROLE_ARN --repo "$REPO" --body "$role"
gh variable set AWS_ARTIFACTS_BUCKET --repo "$REPO" --body "$bucket"
gh variable list --repo "$REPO"
