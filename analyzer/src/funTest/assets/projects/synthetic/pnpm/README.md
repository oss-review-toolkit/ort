# pnpm

This repository contains multiple PNPM projects for testing. Each directory contains the same package.json plus the
additional files described below.

## no-lockfile

Contains no lockfile at all. This makes the build unstable because dependency versions could change at any time.

## node-modules

Contains a node_modules directory. This is bad practice as it is unclear if the node_modules directory and the lockfile
are in sync.

## pnpm-lock

Contains a PNPM lockfilem, pnpm-lock.yaml.
