# syntax=docker/dockerfile-upstream:1.4

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

# Use OpenJDK Eclipe Temurin Ubuntu LTS
FROM eclipse-temurin:11-jdk-jammy as base

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

RUN echo "Architecture: $(arch | sed s/aarch64/arm64/ | sed s/x86_64/amd64/)"

# Check and set apt proxy
COPY docker/set_apt_proxy.sh /etc/scripts/set_apt_proxy.sh
RUN /etc/scripts/set_apt_proxy.sh

# Base package set
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    coreutils \
    cvs \
    curl \
    dirmngr \
    gcc \
    g++ \
    git \
    gnupg2 \
    iproute2 \
    libarchive-tools \
    libgmp-dev \
    libffi-dev \
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
    && sudo rm -rf /var/lib/apt/lists/*

RUN echo $LANG > /etc/locale.gen \
    && locale-gen $LANG \
    && update-locale LANG=$LANG

ARG USERNAME=ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID
ARG HOMEDIR=/home/ort
ENV USER=$USERNAME
ENV HOME=$HOMEDIR

# Non privileged user
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd \
    --uid $USER_ID \
    --gid $USER_GID \
    --shell /bin/bash \
    --home-dir $HOMEDIR \
    --create-home $USERNAME

# We use /opt as main language install dir
RUN chgrp $USERNAME /opt \
    && chmod g+wx /opt

# sudo support
RUN echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Import certificates scripts only
COPY scripts/import_certificates.sh /etc/scripts/import_certificates.sh

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES=""
COPY "$CRT_FILES" /tmp/certificates/

RUN /etc/scripts/import_certificates.sh \
    && if [ -n "$CRT_FILES" ]; then \
        /etc/scripts/import_certificates.sh /tmp/certificates/; \
    fi

USER ${USERNAME}

ENTRYPOINT [ "/bin/bash" ]

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM base as python

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

ARG PYTHON_VERSION=3.10.6
ARG PYENV_GIT_TAG=v2.3.4

ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:${PYENV_ROOT}/shims:${PYENV_ROOT}/bin

RUN curl -kSs https://pyenv.run | bash \
    && pyenv install -v ${PYTHON_VERSION} \
    && pyenv global ${PYTHON_VERSION}

ARG CONAN_VERSION=1.53.0
ARG PYTHON_INSPECTOR_VERSION=0.9.2
ARG PYTHON_PIPENV_VERSION=2022.9.24
ARG PYTHON_POETRY_VERSION=1.1.13
ARG PIPTOOL_VERSION=22.2.2

RUN pip install --no-cache-dir -U \
    pip=="${PIPTOOL_VERSION}" \
    wheel \
    && pip install --no-cache-dir -U \
    Mercurial \
    conan=="${CONAN_VERSION}" \
    pipenv=="${PYTHON_PIPENV_VERSION}" \
    poetry==${PYTHON_POETRY_VERSION} \
    python-inspector=="${PYTHON_INSPECTOR_VERSION}" 

ARG SCANCODE_VERSION=30.1.0

# Scancode is not ready for linux/arm64
RUN arch=$(arch | sed s/aarch64/arm64/) \
    && if [ "$arch" != "arm64" ]; then pip install --no-cache-dir -U  scancode-toolkit==${SCANCODE_VERSION}; fi

#------------------------------------------------------------------------
# RUBY - Build Ruby as a separate component with rbenv
FROM base as ruby

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    libreadline6-dev \
    libssl-dev \
    libz-dev \
    xvfb \
    zlib1g-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG RUBY_VERSION=3.1.2

ENV RBENV_ROOT=/opt/rbenv
ENV PATH=${RBENV_ROOT}/bin:${RBENV_ROOT}/shims/:${RBENV_ROOT}/plugins/ruby-build/bin:$PATH

RUN git clone --depth 1 https://github.com/rbenv/rbenv.git ${RBENV_ROOT}
RUN git clone --depth 1 https://github.com/rbenv/ruby-build.git "$(rbenv root)"/plugins/ruby-build
WORKDIR ${RBENV_ROOT}
RUN src/configure \
    && make -C src
RUN rbenv install ${RUBY_VERSION} -v \
    && rbenv global ${RUBY_VERSION}

# Packages
ARG COCOAPODS_VERSION=1.11.2

RUN gem install bundler cocoapods:${COCOAPODS_VERSION}

#------------------------------------------------------------------------
# NODEJS - Build NodeJS as a separate component with nvm
FROM base AS nodejs

ARG NODEJS_VERSION=16.17.1
ARG NPM_VERSION=8.15.1

ENV NVM_DIR=/opt/nvm

RUN git clone --depth 1 https://github.com/nvm-sh/nvm.git $NVM_DIR
RUN . $NVM_DIR/nvm.sh \
    && nvm install "${NODEJS_VERSION}" \
    && nvm alias default "${NODEJS_VERSION}" \
    && nvm use default

# Packages
ARG BOWER_VERSION=1.8.12
ARG PNPM_VERSION-7.8.0
ARG YARN_VERSION=1.22.10

ENV NODE_PATH $NVM_DIR/v$NODEJS_VERSION/lib/node_modules
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin

RUN npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION pnpm@$PNPM_VERSION yarn@$YARN_VERSION

#------------------------------------------------------------------------
# RUST - Build as a separate component
FROM base AS rust

ARG RUST_VERSION=1.64.0

ENV RUST_HOME=/opt/rust
ENV CARGO_HOME=${RUST_HOME}/cargo
ENV RUSTUP_HOME=${RUST_HOME}/rustup

RUN curl -ksSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain ${RUST_VERSION}

#------------------------------------------------------------------------
# GOLANG - Build as a separate component
FROM base AS go

ARG GO_DEP_VERSION=0.5.4
ARG GO_VERSION=1.18.3

ENV GOPATH=/opt/go
ENV PATH=$PATH:${GOPATH}/bin

RUN arch=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/amd64/) \
    && curl -L https://dl.google.com/go/go${GO_VERSION}.linux-${arch}.tar.gz | tar -C /opt -xz;
    
RUN curl -ksS https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | bash

#------------------------------------------------------------------------
# HASKELL STACK
FROM base AS haskell_stack

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    zlib1g-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG HASKELL_STACK_VERSION=2.7.5

ENV HASKELL_HOME=/opt/haskell
ENV PATH=$PATH:${HASKELL_HOME}/bin

RUN curl -sSL https://get.haskellstack.org/ | bash -s -- -d ${HASKELL_HOME}/bin

#------------------------------------------------------------------------
# REPO / ANDROID SDK
FROM base AS android_cmd

ARG ANDROID_CMD_VERSION=8512546
ENV ANDROID_HOME=/opt/android-sdk

ARG USERNAME=ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID
ARG HOMEDIR=/home/ort
ENV USER=$USERNAME
ENV HOME=$HOMEDIR

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    unzip \
    && sudo rm -rf /var/lib/apt/lists/*

RUN curl -ksS https://storage.googleapis.com/git-repo-downloads/repo | sudo tee /usr/bin/repo > /dev/null 2>&1 \
    && sudo chmod a+x /usr/bin/repo

RUN --mount=type=tmpfs,target=/android \
    cd /android \
    && curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && unzip -q commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip -d $ANDROID_HOME \
    && PROXY_HOST_AND_PORT=${https_proxy#*://} \
    && if [ -n "$PROXY_HOST_AND_PORT" ]; then \
    # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
    SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi \
    && yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS \
    --sdk_root=$ANDROID_HOME "platform-tools" "cmdline-tools;latest"

#------------------------------------------------------------------------
#  Dart
FROM base AS dart

ARG DART_VERSION=2.18.4
WORKDIR /opt/

ENV DART_SDK=/opt/dart-sdk
ENV PATH=$PATH:${DART_SDK}/bin

RUN --mount=type=tmpfs,target=/dart \
    arch=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/x64/) \
    && curl -o /dart/dart.zip -L https://storage.googleapis.com/dart-archive/channels/stable/release/${DART_VERSION}/sdk/dartsdk-linux-${arch}-release.zip \
    && unzip /dart/dart.zip

#------------------------------------------------------------------------
#  SBT
FROM base AS sbt

ARG SBT_VERSION=1.6.1

ENV SBT_HOME=/opt/sbt
ENV PATH=$PATH:${SBT_HOME}/bin

RUN curl -L https://github.com/sbt/sbt/releases/download/v${SBT_VERSION}/sbt-${SBT_VERSION}.tgz | tar -C /opt -xz 

#------------------------------------------------------------------------
# ORT
FROM base as ort

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

WORKDIR ${HOME}/src/ort

# Prepare Gradle
RUN --mount=type=bind,target=${HOME}/src/ort,rw \
    sudo chown -R ${USER}. . \
    && scripts/import_proxy_certs.sh ${HOME}/src/ort/gradle.properties \
    && scripts/set_gradle_proxy.sh \
    && ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:distTar :helper-cli:startScripts \
    && mkdir -p /opt/ort \
    && cd /opt/ort \
    && tar xf ${HOME}/src/ort/cli/build/distributions/ort-$ORT_VERSION.tar -C /opt/ort --strip-components 1 \
    && cp -a ${HOME}/src/ort/scripts/*.sh /opt/ort/bin/ \
    && cp -a ${HOME}/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/ \
    && cp -a ${HOME}/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/

#------------------------------------------------------------------------
# Components container
FROM base as components

ARG USERNAME=ort
ARG HOMEDIR=/home/ort
ENV USER=$USERNAME
ENV HOME=$HOMEDIR

USER $USER
WORKDIR $HOME

# Apt install commands.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq && \
    DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    php \
    subversion \
    && sudo rm -rf /var/lib/apt/lists/*

# PHP composer
ARG COMPOSER_VERSION=2.2
RUN curl -ksS https://getcomposer.org/installer | sudo php -- --install-dir=/bin --filename=composer --$COMPOSER_VERSION

# Python 
ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:${PYENV_ROOT}/shims:${PYENV_ROOT}/bin

COPY --from=python ${PYENV_ROOT} ${PYENV_ROOT}
RUN sudo chown -R ${USER}.${USER} ${PYENV_ROOT}

# Ruby
ENV RBENV_ROOT=/opt/rbenv/
ENV PATH=$PATH:${RBENV_ROOT}/bin:${RBENV_ROOT}/shims:${RBENV_ROOT}/plugins/ruby-install/bin

COPY --chown=${USER}.${USER} --from=ruby ${RBENV_ROOT} ${RBENV_ROOT}
RUN sudo chown -R ${USER}.${USER} ${RBENV_ROOT}

# NodeJS
ARG NODEJS_VERSION=16.17.1

ENV NVM_DIR=/opt/nvm
ENV NODE_PATH $NVM_DIR/v$NODEJS_VERSION/lib/node_modules
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin

COPY --from=nodejs ${NVM_DIR} ${NVM_DIR}
RUN sudo chown -R ${USER}.${USER} ${NVM_DIR}

# Rust
ENV RUST_HOME=/opt/rust
ENV CARGO_HOME=${RUST_HOME}/cargo
ENV RUSTUP_HOME=${RUST_HOME}/rustup
ENV PATH=$PATH:${CARGO_HOME}/bin:${RUSTUP_HOME}/bin

COPY --from=rust ${RUST_HOME} ${RUST_HOME}
RUN sudo chown -R ${USER}.${USER} ${RUST_HOME} \
    && sudo chmod -R o+rw ${RUST_HOME}

# Golang
ENV GOPATH=/opt/go
ENV PATH=$PATH:${GOPATH}/bin

COPY  --from=go ${GOPATH} ${GOPATH}
RUN sudo chown -R ${USER}.${USER} ${GOPATH} \
    && sudo chmod -R o+rw ${GOPATH}

# Haskell
COPY --from=haskell_stack /opt/haskell/bin/stack /usr/bin

# Repo and Android
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:${ANDROID_HOME}/tools/bin:${ANDROID_HOME}/emulator:${ANDROID_HOME}/platform-tools

COPY --from=android_cmd /usr/bin/repo /usr/bin/
COPY --from=android_cmd ${ANDROID_HOME} ${ANDROID_HOME}
RUN sudo chown -R ${USER}.${USER} ${ANDROID_HOME} \
    && sudo chmod -R o+rw ${ANDROID_HOME}

# Dart
ENV DART_SDK=/opt/dart-sdk
ENV PATH=$PATH:${DART_SDK}/bin

COPY --from=dart ${DART_SDK} ${DART_SDK}
RUN sudo chown -R ${USER}.${USER} ${DART_SDK}

# SBT
ENV SBT_HOME=/opt/sbt
ENV PATH=$PATH:${SBT_HOME}/bin

COPY --from=sbt ${SBT_HOME} ${SBT_HOME}
RUN sudo chown -R ${USER}.${USER} ${SBT_HOME}

#------------------------------------------------------------------------
# Main Runtime container
FROM components AS run

ARG USERNAME=ort
ARG HOMEDIR=/home/ort
ENV USER=$USERNAME
ENV HOME=$HOMEDIR

USER $USER
WORKDIR $HOME

# ORT
COPY --from=ort /opt/ort /opt/ort
RUN sudo ln  -sf /opt/ort/bin/ort /usr/bin/ort \
    && sudo ln  -sf /opt/ort/bin/orth /usr/bin/orth

ENTRYPOINT ["/usr/bin/ort"]
