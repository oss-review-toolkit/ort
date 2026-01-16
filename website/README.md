# ORT Project Website

This directory contains the ORT project website, which is built using [Docusaurus](https://docusaurus.io/) and published to the `gh-pages` branch.

## Prerequisites

### Install npm

To build the website, you need to have [npm](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm) installed on your system.

### Generate Plugin Documentation

Before you can build the project, you'll need to generate the documentation for various ORT plugins by running:

```shell
./gradlew generatePluginDocs
```

## Installation

To install all the dependencies required for building the ORT website, run:

```shell
npm install
```

## Local Development

To start a local development server, run the below command. It will open up your default web browser,
allowing you to see any changes you make without needing to reload.

```shell
npm start
```

## Build

To generate a static version of the ORT website in the `build` directory, execute the following command.

```
npm run build
```

## Deployment

To deploy the website over SSH, run:

```
USE_SSH=true npm run deploy
```

Alternatively, to deploy the website to GitHub Pages via the `gh-pages` branch, run:

```
GIT_USER=<Your GitHub Username> npm deploy
```
