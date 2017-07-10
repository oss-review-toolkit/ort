#!/bin/bash -e
# A script to automatically create the various NPM / Yarn lock files for a Node project.

if [ $# -ne 1 ]; then
    echo "Usage: $(basename $0) <path-to-node-project>"
    exit 1
fi

pushd $1

# Delete any NPM 5 lock file.
rm -f package-lock.json

# Delete any "npm shrinkwrap" lock file.
rm -f npm-shrinkwrap.json

# Delete any Yarn lock file.
rm -f yarn.lock

# Create an NPM 5 lock file.
rm -fr node_modules
npm install

# Create a shrinkwrap lock file, ensuring that shrinkwrap does not just rename package-lock.json.
mv package-lock.json package-lock.json.bak
npm shrinkwrap --dev
mv package-lock.json.bak package-lock.json

# Create a Yarn lock file.
rm -fr node_modules
yarn install

# Clean up.
rm -fr node_modules

popd
