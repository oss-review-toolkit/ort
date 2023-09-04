# syntax=docker/dockerfile:1.4

# Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

# Set this to the Java version to use in the base image (and to build and run ORT with).
ARG JAVA_VERSION=17
ARG UBUNTU_VERSION=jammy

# Use OpenJDK Eclipe Temurin Ubuntu LTS
FROM eclipse-temurin:$JAVA_VERSION-jdk-$UBUNTU_VERSION as ort-base-image

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Check and set apt proxy
COPY scripts/set_apt_proxy.sh /etc/scripts/set_apt_proxy.sh
RUN /etc/scripts/set_apt_proxy.sh

# Base package set
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    coreutils \
    curl \
    dirmngr \
    gcc \
    git \
    g++ \
    gnupg2 \
    iproute2 \
    libarchive-tools \
    libffi-dev \
    libgmp-dev \
    libz-dev \
    locales \
    lzma \
    make \
    netbase \
    openssh-client \
    openssl \
    procps \
    rsync \
    sudo \
    tzdata \
    uuid-dev \
    unzip \
    wget \
    xz-utils \
    && rm -rf /var/lib/apt/lists/*

RUN echo $LANG > /etc/locale.gen \
    && locale-gen $LANG \
    && update-locale LANG=$LANG

ARG USERNAME=ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID
ARG HOMEDIR=/home/ort
ENV HOME=$HOMEDIR
ENV USER=$USERNAME

# Non privileged user
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd \
    --uid $USER_ID \
    --gid $USER_GID \
    --shell /bin/bash \
    --home-dir $HOMEDIR \
    --create-home $USERNAME

RUN chgrp $USER /opt \
    && chmod g+wx /opt

# sudo support
RUN echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Copy certificates scripts only.
COPY scripts/*_certificates.sh /etc/scripts/

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES="*.crt"
COPY "$CRT_FILES" /tmp/certificates/

RUN /etc/scripts/export_proxy_certificates.sh /tmp/certificates/ \
    &&  /etc/scripts/import_certificates.sh /tmp/certificates/

# Add syft to use as primary spdx docker scanner
# Create docs dir to store future spdxs
RUN curl -sSfL https://raw.githubusercontent.com/anchore/syft/main/install.sh | sudo sh -s -- -b /usr/local/bin \
    && mkdir -p /usr/share/doc/ort \
    && chown $USER:$USER /usr/share/doc/ort

USER $USER
WORKDIR $HOME

ENTRYPOINT [ "/bin/bash" ]

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM ort-base-image AS pythonbuild

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    libreadline-dev \
    libgdbm-dev \
    libsqlite3-dev \
    libssl-dev \
    libbz2-dev \
    liblzma-dev \
    tk-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG PYTHON_VERSION=3.10.13
ARG PYENV_GIT_TAG=v2.3.25

ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:$PYENV_ROOT/shims:$PYENV_ROOT/bin
RUN curl -kSs https://pyenv.run | bash \
    && pyenv install -v $PYTHON_VERSION \
    && pyenv global $PYTHON_VERSION

ARG CONAN_VERSION=1.57.0
ARG PYTHON_INSPECTOR_VERSION=0.9.8
ARG PYTHON_PIPENV_VERSION=2022.9.24
ARG PYTHON_POETRY_VERSION=1.6.1
ARG PIPTOOL_VERSION=22.2.2
ARG SCANCODE_VERSION=32.0.6

RUN pip install --no-cache-dir -U \
    pip=="$PIPTOOL_VERSION" \
    wheel \
    && pip install --no-cache-dir -U \
    Mercurial \
    conan=="$CONAN_VERSION" \
    pip \
    pipenv=="$PYTHON_PIPENV_VERSION" \
    poetry=="$PYTHON_POETRY_VERSION" \
    python-inspector=="$PYTHON_INSPECTOR_VERSION"

RUN ARCH=$(arch | sed s/aarch64/arm64/) \
    &&  if [ "$ARCH" == "arm64" ]; then \
    pip install -U scancode-toolkit-mini==$SCANCODE_VERSION; \
    else \
    curl -Os https://raw.githubusercontent.com/nexB/scancode-toolkit/v$SCANCODE_VERSION/requirements.txt; \
    pip install -U --constraint requirements.txt scancode-toolkit==$SCANCODE_VERSION; \
    rm requirements.txt; \
    fi

FROM scratch AS python
COPY --from=pythonbuild /opt/python /opt/python

#------------------------------------------------------------------------
# NODEJS - Build NodeJS as a separate component with nvm
FROM ort-base-image AS nodejsbuild

ARG BOWER_VERSION=1.8.12
ARG NODEJS_VERSION=18.14.2
ARG NPM_VERSION=8.15.1
ARG PNPM_VERSION=7.8.0
ARG YARN_VERSION=1.22.17

ENV NVM_DIR=/opt/nvm
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin

RUN git clone --depth 1 https://github.com/nvm-sh/nvm.git $NVM_DIR
RUN . $NVM_DIR/nvm.sh \
    && nvm install "$NODEJS_VERSION" \
    && nvm alias default "$NODEJS_VERSION" \
    && nvm use default \
    && npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION pnpm@$PNPM_VERSION yarn@$YARN_VERSION

FROM scratch AS nodejs
COPY --from=nodejsbuild /opt/nvm /opt/nvm

#------------------------------------------------------------------------
# RUBY - Build Ruby as a separate component with rbenv
FROM ort-base-image AS rubybuild

# hadolint ignore=DL3004
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    libreadline6-dev \
    libssl-dev \
    libz-dev \
    make \
    xvfb \
    zlib1g-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG COCOAPODS_VERSION=1.11.2
ARG RUBY_VERSION=3.1.2

ENV RBENV_ROOT=/opt/rbenv
ENV PATH=$RBENV_ROOT/bin:$RBENV_ROOT/shims/:$RBENV_ROOT/plugins/ruby-build/bin:$PATH

RUN git clone --depth 1 https://github.com/rbenv/rbenv.git $RBENV_ROOT
RUN git clone --depth 1 https://github.com/rbenv/ruby-build.git "$(rbenv root)"/plugins/ruby-build
WORKDIR $RBENV_ROOT
RUN src/configure \
    && make -C src
RUN rbenv install $RUBY_VERSION -v \
    && rbenv global $RUBY_VERSION \
    && gem install bundler cocoapods:$COCOAPODS_VERSION

FROM scratch AS ruby
COPY --from=rubybuild /opt/rbenv /opt/rbenv

#------------------------------------------------------------------------
# RUST - Build as a separate component
FROM ort-base-image AS rustbuild

ARG RUST_VERSION=1.72.0

ENV RUST_HOME=/opt/rust
ENV CARGO_HOME=$RUST_HOME/cargo
ENV RUSTUP_HOME=$RUST_HOME/rustup
RUN curl -ksSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain $RUST_VERSION

FROM scratch AS rust
COPY --from=rustbuild /opt/rust /opt/rust

#------------------------------------------------------------------------
# GOLANG - Build as a separate component
FROM ort-base-image AS gobuild

ARG GO_DEP_VERSION=0.5.4
ARG GO_VERSION=1.20.5
ENV GOBIN=/opt/go/bin
ENV PATH=$PATH:/opt/go/bin

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN ARCH=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/amd64/) \
    && curl -L https://dl.google.com/go/go$GO_VERSION.linux-$ARCH.tar.gz | tar -C /opt -xz \
    && curl -ksS https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | bash

FROM scratch AS golang
COPY --from=gobuild /opt/go /opt/go

#------------------------------------------------------------------------
# HASKELL STACK
FROM ort-base-image AS haskellbuild

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    zlib1g-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG HASKELL_STACK_VERSION=2.7.5

ENV HASKELL_HOME=/opt/haskell
ENV PATH=$PATH:$HASKELL_HOME/bin

RUN curl -sSL https://get.haskellstack.org/ | bash -s -- -d $HASKELL_HOME/bin

FROM scratch AS haskell
COPY --from=haskellbuild /opt/haskell /opt/haskell

#------------------------------------------------------------------------
# REPO / ANDROID SDK
FROM ort-base-image AS androidbuild

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    unzip \
    && sudo rm -rf /var/lib/apt/lists/*

ARG ANDROID_CMD_VERSION=9477386
ENV ANDROID_HOME=/opt/android-sdk

RUN --mount=type=tmpfs,target=/android \
    cd /android \
    && curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && unzip -q commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip -d $ANDROID_HOME \
    && PROXY_HOST_AND_PORT=${https_proxy#*://} \
    && PROXY_HOST_AND_PORT=${PROXY_HOST_AND_PORT%/} \
    && if [ -n "$PROXY_HOST_AND_PORT" ]; then \
    # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
    SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi \
    && yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS --sdk_root=$ANDROID_HOME "platform-tools" "cmdline-tools;latest"

RUN curl -ksS https://storage.googleapis.com/git-repo-downloads/repo | tee $ANDROID_HOME/cmdline-tools/bin/repo > /dev/null 2>&1 \
    && sudo chmod a+x $ANDROID_HOME/cmdline-tools/bin/repo

FROM scratch AS android
COPY --from=androidbuild /opt/android-sdk /opt/android-sdk

#------------------------------------------------------------------------
#  Dart
FROM ort-base-image AS dartbuild

ARG DART_VERSION=2.18.4
WORKDIR /opt/

ENV DART_SDK=/opt/dart-sdk
ENV PATH=$PATH:$DART_SDK/bin

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN --mount=type=tmpfs,target=/dart \
    ARCH=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/x64/) \
    && curl -o /dart/dart.zip -L https://storage.googleapis.com/dart-archive/channels/stable/release/$DART_VERSION/sdk/dartsdk-linux-$ARCH-release.zip \
    && unzip /dart/dart.zip

FROM scratch AS dart
COPY --from=dartbuild /opt/dart-sdk /opt/dart-sdk

#------------------------------------------------------------------------
# SBT
FROM ort-base-image AS sbtbuild

ARG SBT_VERSION=1.6.1

ENV SBT_HOME=/opt/sbt
ENV PATH=$PATH:$SBT_HOME/bin

RUN curl -L https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz | tar -C /opt -xz

FROM scratch AS sbt
COPY --from=sbtbuild /opt/sbt /opt/sbt

#------------------------------------------------------------------------
# SPM
FROM ort-base-image AS spmbuild

ARG SWIFT_VERSION=5.8.1

ENV SWIFT_HOME=/opt/swift
ENV PATH=$PATH:$SWIFT_HOME/bin

RUN mkdir $SWIFT_HOME \
    && curl -L https://download.swift.org/swift-$SWIFT_VERSION-release/ubuntu2204/swift-$SWIFT_VERSION-RELEASE/swift-$SWIFT_VERSION-RELEASE-ubuntu22.04.tar.gz \
    | tar -xz -C $SWIFT_HOME --strip-components=2

FROM scratch AS spm
COPY --from=spmbuild /opt/swift /opt/swift

#------------------------------------------------------------------------
# PHP
FROM ort-base-image AS phpbuild

# PHP composer
ARG COMPOSER_VERSION=2.2

ENV PATH=$PATH:/opt/php/bin
RUN mkdir -p /opt/php/bin \
    && curl -ksS https://getcomposer.org/installer | php -- --install-dir=/opt/php/bin --filename=composer --$COMPOSER_VERSION

FROM scratch AS php
COPY --from=phpbuild /opt/php /opt/php

#------------------------------------------------------------------------
# NUGET
FROM ort-base-image AS nugetbuilld
# nuget-inspector
ENV NUGET_INSPECTOR_HOME=/opt/nuget-inspector
ENV NUGET_INSPECTOR_BIN=$NUGET_INSPECTOR_HOME/bin
ENV DOTNET_HOME=$NUGET_INSPECTOR_HOME/dotnet

ENV PATH=$PATH:$DOTNET_HOME:$DOTNET_HOME/tools:$NUGET_INSPECTOR_BIN

# Note: We are not installing a dotnet package directly because
# debian packages from Ubuntu and Microsoft are incomplete
RUN mkdir -p $DOTNET_HOME \
    && curl -L https://aka.ms/dotnet/6.0/dotnet-sdk-linux-x64.tar.gz \
    | tar -C $DOTNET_HOME -xz

ARG NUGET_INSPECTOR_VERSION=0.9.12
RUN mkdir -p $NUGET_INSPECTOR_BIN \
    && curl -L https://github.com/nexB/nuget-inspector/releases/download/v$NUGET_INSPECTOR_VERSION/nuget-inspector-v$NUGET_INSPECTOR_VERSION-linux-x64.tar.gz \
    | tar --strip-components=1 -C $NUGET_INSPECTOR_BIN -xz

FROM scratch AS nuget
COPY --from=nugetbuild /opt/nuget-inspector /opt/nuget-inspector

#------------------------------------------------------------------------
# ORT
FROM ort-base-image as ortbuild

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

WORKDIR $HOME/src/ort

# Prepare Gradle
RUN --mount=type=cache,target=/var/tmp/gradle \
    --mount=type=bind,target=$HOME/src/ort,rw \
    export GRADLE_USER_HOME=/var/tmp/gradle \
    && sudo chown -R "$USER". $HOME/src/ort /var/tmp/gradle \
    && scripts/set_gradle_proxy.sh \
    && ./gradlew --no-daemon --stacktrace \
    -Pversion=$ORT_VERSION \
    :cli:installDist \
    :helper-cli:startScripts \
    && mkdir /opt/ort \
    && cp -a $HOME/src/ort/cli/build/install/ort /opt/ \
    && cp -a $HOME/src/ort/scripts/*.sh /opt/ort/bin/ \
    && cp -a $HOME/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/ \
    && cp -a $HOME/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/

FROM scratch AS ortbin
COPY --from=ortbuild /opt/ort /opt/ort

#------------------------------------------------------------------------
# Main Minimal Runtime container
FROM ort-base-image as run

# Remove ort build scripts
RUN [ -d /etc/scripts ] && sudo rm -rf /etc/scripts

# Minor requirements
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update && \
    DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    subversion \
    && sudo rm -rf /var/lib/apt/lists/*

RUN syft / --exclude '*/usr/share/doc' --exclude '*/etc' -o spdx-json --file /usr/share/doc/ort/ort-base.spdx.json

# Python
ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:$PYENV_ROOT/shims:$PYENV_ROOT/bin
COPY --from=python --chown=$USER:$USER $PYENV_ROOT $PYENV_ROOT
RUN syft $PYENV_ROOT -o spdx-json --file /usr/share/doc/ort/ort-python.spdx.json

# NodeJS
ARG NODEJS_VERSION=18.14.2
ENV NVM_DIR=/opt/nvm
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin
COPY --from=nodejs --chown=$USER:$USER $NVM_DIR $NVM_DIR
RUN syft $NVM_DIR  -o spdx-json --file /usr/share/doc/ort/ort-nodejs.spdx.json

# ORT
COPY --from=ortbin --chown=$USER:$USER /opt/ort /opt/ort
ENV PATH=$PATH:/opt/ort/bin

USER $USER
WORKDIR $HOME

# Ensure that the ORT data directory exists to be able to mount the config into it with correct permissions.
RUN mkdir -p "$HOMEDIR/.ort"

ENTRYPOINT ["/opt/ort/bin/ort"]
