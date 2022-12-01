# How to add support for a new package manager

The sections below outline the steps needed to add support for a new package manager to OSS Review Toolkit.

## 1. Initial Questions / Requirements

Before you start any implementation work, determine whether the package manager meets the minimal requirements to
provide the data needed for OSS Review Toolkit to work.

Answers to the questions below should help you. 

1. How can you detect a project uses this specific package manager?
2. How can you get the declared license for a package?
3. How can one get the dependency tree including package names, versions?
4. How can one obtain the source code for a dependency?
5. How can one separate code dependencies from build/test ones?
6. Can you provide example projects that can be used test implementation?

*Let's assume you want to implement support for `pnpm` then*

__1. How can you detect a project is using pnpm?__

The presence of a [pnpm-lock.yaml](https://github.com/pnpm/pnpm/blob/master/pnpm-lock.yaml) file 
can uniquely identify that a project is using pnpm.
You can't say the same for `package.json` as there are multiple package managers using a `package.json`.

__2. How can you get the declared license for a pnpm package?__

The pnpm package manager is part of Node.js ecosystem, so it has package.json and
uses npmjs.com as its central package repository.

__3. How can one get the dependency tree including package names, versions?__

https://pnpm.js.org/en/cli/list

__4. How can one obtain the source code for a dependency of a pnpm-based project?__

Same as npm, pnpm queries registry.npmjs.com?

__5. How can one separate code dependencies from build/test ones in pnpm?__

Npmne.com has same scopes as `npm` and `yarn` - `dependencies` amd `devDependencies` 

__6. Can you provide some pnpm projects to test implementation?__

> https://github.com/zkochan/packages/tree/2cb8e0072168869e86d8a81206330352455746cd/rename-overwrite

## 2. Adding a new Analyzer module

Coming soon...

## 3. Implementing a new Analyzer Module

## 4. Testing a new Analyzer Module

## 5. Updating Documentation

