#!/bin/bash -e
# Script to automatically create/update the Shrinkwrap and Yarn lockfiles for an NPM project.

if [[ $# -ne 1 ]]; then
    echo "Usage: update-npm-lockfiles.sh [path-to-NPM-project]"
    exit 1
fi

pushd $1

# Create/Update Shrinkwrap lockfile
npm install
npm shrinkwrap --dev
rm -r node_modules

# Create/Update Yarn lockfile
yarn install
rm -r node_modules

popd

