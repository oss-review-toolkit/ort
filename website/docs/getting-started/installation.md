---
sidebar_position: 1
---

# Installation

## From Docker

The easiest way to run ORT is to use the Docker images available from the GitHub container registry.
There are two images available:
[`ort`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort/versions) and [`ort-minimal`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort-minimal/versions).

The difference between those two images is that `ort` contains installations of all supported package managers while `ort-minimal` contains only the most commonly used package managers to reduce the image size.
For example, for release `13.0.0` the size of the [`ort`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort/168323397?tag=13.0.0) image is ~7 GB and the size of the [`ort-minimal`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort-minimal/168320821?tag=13.0.0) image is ~3 GB.
The examples below use the `ort` image.
To run them with the `ort-minimal` image replace `ort` with `ort-minimal`.

To run the Docker image for the latest ORT release use:

```shell
$ docker run ghcr.io/oss-review-toolkit/ort --version
13.0.0
```

To run a specific version, for example `12.0.0`, use:

```shell
$ docker run ghcr.io/oss-review-toolkit/ort:12.0.0 --version
12.0.0
```

To show the command line help, run the image with the `--help` option:

```shell
docker run ghcr.io/oss-review-toolkit/ort --help
```

To show which versions of the required tools are installed run the image with the `requirements` command:

```shell
docker run ghcr.io/oss-review-toolkit/ort requirements
```

The above commands always create a new Docker container.
To avoid that they pile up Docker can be run with the `--rm` flag to automatically remove the container when the command has finished:

```shell
docker run --rm ghcr.io/oss-review-toolkit/ort [command]
```

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

* Java Development Kit (JDK) version 11 or later; also remember to set the `JAVA_HOME` environment variable accordingly.

Change into the directory with ORT's source code and run `./gradlew installDist` (on the first run this will bootstrap Gradle and download all required dependencies).

## Basic usage

Depending on how ORT was installed, it can be run in the following ways:

* If the Docker image was built, use

  ```shell
  docker run ort --help
  ```

  You can find further hints for using ORT with Docker in the [documentation](../guides/docker.md).

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
