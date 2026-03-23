# syntax=docker/dockerfile:1.22.0

FROM crops/yocto:ubuntu-24.04-builder AS builder

ARG YOCTO_BRANCH

RUN git clone --branch $YOCTO_BRANCH --depth 1 https://git.yoctoproject.org/poky

ARG SPDX_VERSION

RUN cat <<EOF > spdx.conf
# See https://docs.yoctoproject.org/dev-manual/sbom.html.
INHERIT:remove = "create-spdx"
INHERIT += "create-spdx-$SPDX_VERSION"

# See https://docs.yoctoproject.org/ref-manual/variables.html#term-SPDX_ARCHIVE_PACKAGED.
SPDX_INCLUDE_SOURCES = "0"
SPDX_INCLUDE_COMPILED_SOURCES = "0"
SPDX_ARCHIVE_SOURCES = "0"

SPDX_PRETTY = "1"
EOF

WORKDIR /home/yoctouser/poky

ARG BUILD_TARGET

ENV BB_NUMBER_THREADS="4"
ENV PARALLEL_MAKE="-j 4"

RUN --mount=type=cache,target=/home/yoctouser/poky/build,sharing=locked,uid=1001,gid=1001 <<EOT
    cp /home/yoctouser/spdx.conf build/

    # Note that sourcing `oe-init-build-env` changes to the "build" directory.
    . ./oe-init-build-env

    bitbake -R spdx.conf $BUILD_TARGET
    find tmp/deploy -type f -name "*.spdx.json" | tar --zstd -cf /home/yoctouser/spdx-json-files.tar.zst -T -
EOT

FROM scratch
COPY --from=builder /home/yoctouser/spdx-json-files.tar.zst /
