#!/bin/bash
set -euo pipefail
ACCOUNT_ID=${1:-$(aws sts get-caller-identity --query Account --output text)}
REGION=${2:-ap-south-1}
REPO="vaultcache"
IMAGE_TAG=$(git rev-parse --short HEAD)
ECR_URI="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com/$REPO"

aws ecr get-login-password --region "$REGION" | \
  docker login --username AWS --password-stdin "$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"

aws ecr describe-repositories --repository-names "$REPO" --region "$REGION" 2>/dev/null || \
  aws ecr create-repository --repository-name "$REPO" --region "$REGION"

docker build -t "$REPO:$IMAGE_TAG" .
docker tag "$REPO:$IMAGE_TAG" "$ECR_URI:$IMAGE_TAG"
docker tag "$REPO:$IMAGE_TAG" "$ECR_URI:latest"
docker push "$ECR_URI:$IMAGE_TAG"
docker push "$ECR_URI:latest"

echo "SUCCESS → $ECR_URI:$IMAGE_TAG"
echo "Deploy: aws ecs update-service --cluster vaultcache --service vaultcache-svc --force-new-deployment --region $REGION"
