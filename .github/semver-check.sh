#!/bin/bash
#
# This script checks that the version number of the release is an expected one, and avoid erroneous releases which don't follow semver
set -eo pipefail

git fetch --tags --quiet
VERSION="$1"
PREV_VERSION=$(git tag --sort=-creatordate | head -1)
PREV_VERSION=${PREV_VERSION#v}
PREV_MAJOR="${PREV_VERSION%%.*}"
PREV_VERSION="${PREV_VERSION#*.}"
PREV_MINOR="${PREV_VERSION%%.*}"
PREV_PATCH="${PREV_VERSION#*.}"
if [[ "$PREV_VERSION" == "$PREV_PATCH" ]]; then
   PREV_PATCH="0"
fi
echo "Verifying that $VERSION is one of the following valid versions"
echo "$PREV_MAJOR.$PREV_MINOR.$((PREV_PATCH + 1))"
echo "$PREV_MAJOR.$((PREV_MINOR + 1))"
echo "$((PREV_MAJOR + 1)).0"
[[ "$VERSION" == "$PREV_MAJOR.$PREV_MINOR.$((PREV_PATCH + 1))"?(.0) || "$VERSION" == "$PREV_MAJOR.$((PREV_MINOR + 1))"?(.0) || "$VERSION" == "$((PREV_MAJOR + 1)).0"?(.0) ]]
