# Docker

The easiest way to run ORT is to use the Docker images available from the GitHub container registry.
There are two images available:
[`ort`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort/versions) and [`ort-minimal`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort-minimal/versions).

The difference between those two images is that `ort` contains installations of all supported package managers while `ort-minimal` contains only the most commonly used package managers to reduce the image size.
For example, for release `76.0.0` the size of the [`ort`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort) image is ~9 GB and the size of the [`ort-minimal`](https://github.com/oss-review-toolkit/ort/pkgs/container/ort-minimal) image is ~3 GB.
Both images are available for amd64 and arm64 architectures.
The examples below use the `ort` image.
To run them with the `ort-minimal` image replace `ort` with `ort-minimal`.

## Running the Docker image

To run the Docker image for the latest ORT release use:

```shell
$ docker run ghcr.io/oss-review-toolkit/ort --version
76.0.0
```

To run a specific version, for example `76.0.0`, use:

```shell
$ docker run ghcr.io/oss-review-toolkit/ort:76.0.0 --version
76.0.0
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

## Mounting directories

To analyze a project, mount your working directory into the container:

```shell
docker run \
  -v $PWD/:/project \ # Mount current working directory into /project to use as input.
  ort --info \
  -c /project/ort/config.yml \ # Use file from "<workingdirectory>/ort" as config.
  analyze -i /project \ # Analyze the current working directory using the alias (set earlier in the -v option).
  -o /project \ # Output goes into the current working directory.
  [...] # Insert further arguments for the command.
```

If only a subproject shall be analyzed, change the input path `-i /project` to `-i /project/subproject`.
Note that still the projects root directory needs to be mounted to Docker for ORT to detect VCS information.

**Note:**
The single forward slash `/` between the environment variable `$PWD` and the `:` is required for PowerShell compatibility, as PowerShell otherwise interprets `:` as part of the environment variable.

## Building the Docker image

To build the Docker image from source, clone the ORT repository and run:

```shell
docker build -t ort .
```

Alternatively, use the script at `scripts/docker_build.sh` which also sets the ORT version from the Git revision.

### Prerequisites

* Docker 18.09 or later (make sure that the Docker daemon is running).
* Enable [BuildKit](https://docs.docker.com/develop/develop-images/build_enhancements/#to-enable-buildkit-builds) for Docker.

### Setting custom certificates

It is possible to install custom certificates when building the image so that they are installed into various keystores.
To do this, specify `--build-arg CRT_FILES=<path>` when running `docker build`.
Note that this requires the directory or certificate file to be inside the Docker build context (usually the directory where the build is run, i.e., probably the directory the Dockerfile resides in).
Otherwise, the directories cannot be copied into the Docker image.

## Common issues

### Docker build fails with an "SSL Handshake" error

Some web proxies, such as from Blue Coat (Symantec) [do not support TLSv1.3](https://en.wikipedia.org/wiki/Transport_Layer_Security#TLS_1.3), which leads to errors when Docker tries to establish a connection through them.
The following steps allow forcing a specific TLS version to be used:

1. Insert `ENV JAVA_OPTS="-Djdk.tls.client.protocols=TLSv1.2"` in the Dockerfile, below the `FROM` line to force a specific TLS version.
2. Run the build again, it should succeed now.

### Authenticating with a private Git repository fails

See [how to authenticate with private repositories](../how-to-guides/how-to-authenticate-with-private-repositories.md) for instructions on configuring `.netrc` or `.gitconfig` for use with Docker.
