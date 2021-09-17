# Installation and Basic Usage

This article explains different ways to install ORT and demonstrates its basic usage.

## Installation

### From binaries

Preliminary binary artifacts for ORT are currently available via
[JitPack](https://jitpack.io/#oss-review-toolkit/ort). Please note that due to limitations with the JitPack build
environment, the reporter is not able to create the Web App report.

### From sources

Install the following basic prerequisites:

* Git (any recent version will do).

Then clone this repository. If you intend to run tests, you need to clone with submodules by running
`git clone --recurse-submodules`. If you have already cloned non-recursively, you can initialize submodules afterwards
by running `git submodule update --init --recursive`.

#### Build using Docker

Install the following basic prerequisites:

* Docker 18.09 or later (and ensure its daemon is running).
* Enable [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/) for Docker.

Change into the directory with ORT's source code and run `docker build -t ort .`.

#### Build natively

Install these additional prerequisites:

* Java Development Kit (JDK) version 11 or later; also remember to set the `JAVA_HOME` environment variable accordingly.

Change into the directory with ORT's source code and run `./gradlew installDist` (on the first run this will bootstrap
Gradle and download all required dependencies).

## Basic usage

ORT can now be run from the command line as follows:

    ./cli/build/install/ort/bin/ort --help

Note that if you make any changes to ORT's source code, you must rebuild the distribution using the steps
above.

To avoid that, you can also build and run ORT in one go (if you have the prerequisites from the
[Build natively](#build-natively) section installed):

    ./gradlew cli:run --args="--help"

Note that in this case the working directory used by ORT is that of the `cli` project, not the directory `gradlew` is
located in (see https://github.com/gradle/gradle/issues/6074).

## Running the tools

When you build ORT from sources, you have the option to run it from a Docker image (which comes with all runtime
dependencies) or to run ORT natively (in which case some additional requirements need to be fulfilled).

### Run using Docker

After you have built the image as [described above](#build-using-docker), simply run
`docker run <DOCKER_ARGS> ort <ORT_ARGS>`. You typically use `<DOCKER_ARGS>` to mount the project directory to analyze
into the container for ORT to access it, like:

    docker run -v /workspace:/project ort --info analyze -f JSON -i /project -o /project/ort/analyzer

You can find further hints for using ORT with Docker in the [documentation](./docs/hints-for-use-with-docker.md).

### Run natively

First of all, make sure that the locale of your system is set to `en_US.UTF-8` as using other locales might lead to
issues with parsing the output of some external tools.

Then install any missing external command line tools as listed by the command:

    ./cli/build/install/ort/bin/ort requirements

or

    ./gradlew cli:run --args="requirements"

When you have intalled the external tools, run ORT:

    ./cli/build/install/ort/bin/ort --info analyze -f JSON -i /project -o /project/ort/analyzer

or

    ./gradlew cli:run --args="--info analyze -f JSON -i /project -o /project/ort/analyzer"

### Running on CI

A basic ORT pipeline (using the _analyzer_, _scanner_ and _reporter_) can easily be run on
[Jenkins CI](https://jenkins.io/) by using the [Jenkinsfile](./integrations/Jenkinsfile) in a (declarative)
[pipeline](https://jenkins.io/doc/book/pipeline/) job. Please see the [Jenkinsfile](./integrations/Jenkinsfile) itself
for documentation of the required Jenkins plugins. The job accepts various parameters that are translated to ORT command
line arguments. Additionally, one can trigger a downstream job which e.g. further processes scan results. Note that it
is the downstream job's responsibility to copy any artifacts it needs from the upstream job.

A demo instance of a Jenkins pipeline for ORT will soon be

![Fosshost Logo](../logos/fosshost.png)
