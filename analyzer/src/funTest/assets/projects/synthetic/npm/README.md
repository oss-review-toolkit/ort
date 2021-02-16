# npm

This repository contains multiple NPM projects for testing. Each directory contains an equivalent package.json plus the
additional files described below.

## no-lockfile

Contains no lockfile at all. This makes the build unstable because dependency versions could change at any time.

## node-modules

Contains a node_modules directory. This is bad practice as it is unclear if the node_modules directory and the lockfile
are in sync.

## package-lock

Contains an [official NPM lockfile](https://docs.npmjs.com/files/package-lock.json).

## shrinkwrap

Contains a [Shrinkwrap lockfile](https://docs.npmjs.com/files/shrinkwrap.json).

## yarn

Contains a [Yarn lockfile](https://yarnpkg.com/lang/en/docs/yarn-lock/).
