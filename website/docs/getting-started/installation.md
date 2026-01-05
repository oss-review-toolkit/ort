# Installation

If you want to run ORT in a CI pipeline, see [CI Integrations](ci-integrations.md) instead.

## From Docker

The easiest way to run ORT is to use the official Docker images. See [Docker](docker.md) for details.

## From binaries

A binary distribution of ORT can be downloaded from the [latest GitHub release](https://github.com/oss-review-toolkit/ort/releases/latest).
The `ort-[version].zip` file contains binaries to run ORT in the `bin` folder.

For Linux:

```shell
bin/ort [command]
```

For Windows:

```batch
bin\ort.bat [command]
```

## From sources

Install the following basic prerequisites:

* Git (any recent version will do).

Then clone this repository.

```shell
git clone https://github.com/oss-review-toolkit/ort
# If you intend to run tests, you have to clone the submodules too.
cd ort
git submodule update --init --recursive
```

### Build using Docker

Install the following basic prerequisites:

* Docker 18.09 or later (make sure that the Docker daemon is running).
* Enable [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/#to-enable-buildkit-builds) for Docker.

Change into the directory with ORT's source code and run `docker build -t ort .`.
Alternatively, use the script at `scripts/docker_build.sh` which also sets the ORT version from the Git revision.

### Build natively

Install these additional prerequisites:

* Java Development Kit (JDK) version 21 or later; also remember to set the `JAVA_HOME` environment variable accordingly.

Change into the directory with ORT's source code and run `./gradlew installDist` (on the first run this will bootstrap Gradle and download all required dependencies).

## Basic usage

Depending on how ORT was installed, it can be run in the following ways:

* If the Docker image was built, use

  ```shell
  docker run ort --help
  ```

  You can find further hints for using ORT with Docker in the [documentation](../tutorials/docker.md).

* If the ORT distribution was built from sources, use

  ```shell
  ./cli/build/install/ort/bin/ort --help
  ```

* If running directly from sources via Gradle, use

  ```shell
  ./gradlew cli:run --args="--help"
  ```

  Note that in this case the working directory used by ORT is that of the `cli` project, not the directory `gradlew` is located in (see https://github.com/gradle/gradle/issues/6074).

For simplicity of the following usage examples, the above ORT invocations are unified to just `ort --help`.
