#!/bin/sh

PROFILE=${1:-david}

AWS_ACCESS_KEY_ID=$(aws --profile "$PROFILE" configure get aws_access_key_id)

AWS_SECRET_ACCESS_KEY=$(aws --profile "$PROFILE" configure get aws_secret_access_key)

AWS_SESSION_TOKEN=$(aws --profile "$PROFILE" configure get aws_session_token)

jq -n \
  --arg aws_access_key_id "$AWS_ACCESS_KEY_ID" \
  --arg aws_secret_access_key "$AWS_SECRET_ACCESS_KEY" \
  --arg aws_session_token "$AWS_SESSION_TOKEN" \
  '{"aws-access-key-id":$aws_access_key_id, "aws-secret-access-key":$aws_secret_access_key, "aws-session-token":$aws_session_token}'