# syntax=devthefuture/dockerfile-x

# The above opts-in for an extended syntax that supports e.g. INCLUDE statements, see
# https://codeberg.org/devthefuture/dockerfile-x

INCLUDE docker/Dockerfile.arg.versions

##
## The base image for everthing.
##
## Even if not all uses require a JDK, use an Ubuntu base image that includes the JDK to reduces the total number of
## different Docker images used.
##

FROM eclipse-temurin:$JAVA_VERSION-jdk-jammy AS base

# See https://docs.docker.com/build/cache/#use-the-dedicated-run-cache
RUN --mount=type=cache,target=/var/cache/apt \
    # Install essential tools.
    apt-get update && \
    apt-get install -y --no-install-recommends \
        build-essential \
        git \
        git-lfs \
        mercurial \
        unzip

RUN mkdir -p /opt/ort-tools/git-repo && \
    curl -s https://storage.googleapis.com/git-repo-downloads/repo > /opt/ort-tools/git-repo/repo && \
    chmod a+rx /opt/ort-tools/git-repo/repo

ENV PATH=/opt/ort-tools/git-repo:$PATH

##
## The mise image (see https://github.com/jdx/mise).
##

FROM base AS mise

RUN --mount=type=cache,target=/var/cache/apt \
    # Install tools required to install mise.
    apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        gnupg \
    && \
    # Install mise (see https://mise.jdx.dev/getting-started.html#apt).
    install -dm 755 /etc/apt/keyrings && \
    curl -s https://mise.jdx.dev/gpg-key.pub | gpg --dearmor > /etc/apt/keyrings/mise-archive-keyring.gpg && \
    echo "deb [signed-by=/etc/apt/keyrings/mise-archive-keyring.gpg arch=amd64] https://mise.jdx.dev/deb stable main" > /etc/apt/sources.list.d/mise.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends mise

# Ease debugging of mise errors in derived images.
ENV MISE_LOG_LEVEL=debug

##
## The images that install the various required tools.
## By convention, each stage must put its tool into `/opt/ort-tools/<tool>`.
##

# Scanners
INCLUDE docker/Dockerfile.stage.askalono
INCLUDE docker/Dockerfile.stage.boyterlc
INCLUDE docker/Dockerfile.stage.licensee

# Package managers
INCLUDE docker/Dockerfile.stage.node
INCLUDE docker/Dockerfile.stage.python

##
## The image that combines all tools from previous stages.
##

FROM base AS ort-tools

COPY --from=askalono --link /opt/ort-tools/askalono /opt/ort-tools/askalono
ENV PATH=/opt/ort-tools/askalono:$PATH

COPY --from=boyterlc --link /opt/ort-tools/boyterlc /opt/ort-tools/boyterlc
ENV PATH=/opt/ort-tools/boyterlc:$PATH

COPY --from=licensee --link /opt/ort-tools/licensee /opt/ort-tools/licensee
ENV PATH=/opt/ort-tools/licensee:$PATH

COPY --from=node --link /opt/ort-tools/node /opt/ort-tools/node
ENV PATH=/opt/ort-tools/node/node_modules/.bin:/opt/ort-tools/node/latest/bin:$PATH

COPY --from=python --link /opt/ort-tools/python /opt/ort-tools/python
ENV PATH=/opt/ort-tools/python/latest/bin:$PATH

##
## The image that provides ORT.
##

FROM base AS ort

# Repeat the global argument names for use in this stage.
ARG ORT_VERSION

RUN mkdir -p /opt/ort && \
    curl -sL https://github.com/oss-review-toolkit/ort/releases/download/$ORT_VERSION/ort-$ORT_VERSION.tgz | \
        tar -xz --strip-components=1 -C /opt/ort

##
## The image that combines ORT with its required tools.
##

FROM ort-tools

COPY --from=ort --link /opt/ort /opt/ort

ENTRYPOINT ["/opt/ort/bin/ort"]
