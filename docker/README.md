# OSS Review Toolkit Docker Files

This directory contains several Docker files that ease building and / or running ORT without the need to install any
prerequisites except for Docker. See below for descriptions of the different files and their purposes.

## build/Dockerfile

The Docker file in the `build` directory is for *building* ORT locally on a machine with direct Internet access, i.e. no
proxy setup is required. It is not meant to be used directly, but via the `build.sh` script, which builds the image and
runs it *as the current user* with the user's `$HOME` directory mounted into the container. It writes build output back
to the host just as if you had built ORT locally as the current user.

Example usage:

    $ docker/build.sh

## run/Dockerfile

The Docker file in the `run` directory is for *running* ORT locally on a machine with direct Internet access, i.e. no
proxy setup is required. It is not meant to be used directly, but via the `run.sh` script, which first builds ORT via
the `build.sh` script (if not already done so), and then runs ORT *as the current user* with the first (quoted) argument
being passed to `docker run` and all remaining arguments being passed to ORT. The first argument is usually used to
mount the directory of the project to analyze into the Docker container.

Example usages:

    $ docker/run.sh "-v path/to/project:/project" analyze -f JSON -i /project/source -o /project/ort/analyzer
    $ docker/run.sh "-v path/to/project:/project" scan -f JSON -i /project/ort/analyzer/analyzer-result.yml -o /project/ort/scanner
    $ docker/run.sh "-v path/to/project:/project" report -f StaticHTML -i /project/ort/scanner/scan-result.yml -o /project/ort/reporter
