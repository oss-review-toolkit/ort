This directory contains test assets for Yocto Linux SPDX files and tools to manage them.

The [input](./input) directory contains [SPDX files](https://docs.yoctoproject.org/dev-manual/sbom.html) as created by Yocto's `bitbake` tool in JSON format.
The [output](./output) directory contains the expected ORT project analyzer results in YAML format.

In order to conveniently (re-)create SPDX files, this directory contains a [Dockerfile](./Dockerfile) to set up a build environment for Yocto's "Poky" reference distribution.
A [script](./create_spdx_input.sh) builds the Docker image and copies the deployed SPDX files to the `input` directory.
