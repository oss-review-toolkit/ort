# syntax=docker/dockerfile:1.3

# Copyright (C) 2020 Bosch Software Innovations GmbH
# Copyright (C) 2021-2022 Bosch.IO GmbH
# Copyright (C) 2021 Alliander N.V.
# Copyright (C) 2021 BMW CarIT GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

#------------------------------------------------------------------------
# Use OpenJDK Eclipe Temurin Ubuntu LTS
FROM eclipse-temurin:11-jdk-focal AS build

ENV LANG=C.UTF-8

# Prepare build environment to use ort scripts from here
COPY scripts /etc/scripts

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES=""
COPY "$CRT_FILES" /tmp/certificates/
RUN /etc/scripts/import_proxy_certs.sh \
    && if [ -n "$CRT_FILES" ]; then \
    /etc/scripts/import_certificates.sh /tmp/certificates/; \
    fi

#------------------------------------------------------------------------
# Ubuntu build toolchain
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    build-essential \
    clang-9 \
    clang++-9 \
    dirmngr \
    dpkg-dev \
    git \
    gnupg \
    libbluetooth-dev \
    libbz2-dev \
    libc6-dev \
    libexpat1-dev \
    libffi-dev \
    libgmp-dev \
    libgdbm-dev \
    liblzma-dev \
    libmpdec-dev \
    libncursesw5-dev \
    libreadline-dev \
    libsqlite3-dev \
    libssl-dev \
    make \
    netbase \
    tk-dev \
    tzdata \
    uuid-dev \
    unzip \
    xz-utils \
    zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM build as pythonbuild

ARG PYTHON_VERSION=3.10.2
ARG PYTHON2_VERSION=""

ENV PYENV_ROOT=/opt/python
RUN curl -L https://github.com/pyenv/pyenv-installer/raw/master/bin/pyenv-installer | bash
ENV PATH=/opt/python/bin:$PATH
# Python 3.6.x series has problems with alignment with modern GCC
# As we don not want patch Python versions we use Clang as compiler
RUN for pyvers in ${PYTHON_VERSION} ${PYTHON2_VERSION}; do \
    CC=clang-9 CXX=clang++9 pyenv install $pyvers; done
RUN pyenv global ${PYTHON_VERSION} ${PYTHON2_VERSION}
ENV PATH=/opt/python/shims:$PATH

ARG CONAN_VERSION=1.44.0
ARG PYTHON_VIRTUALENV_VERSION=20.13.0
ARG PIPTOOL_VERSION=21.3.1
ARG SCANCODE_VERSION=30.1.0

# Install pip and wheel ahead of regular modules as python try to generate
# whl packages if they are not automatic available to download.
RUN pip install -U \
    pip=="${PIPTOOL_VERSION}" \
    wheel
RUN pip install -U \
    Mercurial \
    conan=="${CONAN_VERSION}" \
    pipenv \
    scancode-toolkit==${SCANCODE_VERSION} \
    virtualenv=="${PYTHON_VIRTUALENV_VERSION}"

COPY docker/python.sh /etc/profile.d

#------------------------------------------------------------------------
# RUBY - Build Ruby as a separate component with rbenv
FROM build as rubybuild

ARG COCOAPODS_VERSION=1.11.2
ARG RUBY_VERSION=2.7.4
ENV RBENV_ROOT=/opt/rbenv
ENV PATH=${RBENV_ROOT}/bin:${RBENV_ROOT}/shims/:${RBENV_ROOT}/plugins/ruby-build/bin:$PATH

RUN git clone --depth 1 https://github.com/rbenv/rbenv.git ${RBENV_ROOT}
RUN git clone --depth 1 https://github.com/rbenv/ruby-build.git "$(rbenv root)"/plugins/ruby-build
WORKDIR ${RBENV_ROOT}
RUN src/configure \
    && make -C src
RUN rbenv install ${RUBY_VERSION} \
    && rbenv global ${RUBY_VERSION} \
    && gem install bundler cocoapods:${COCOAPODS_VERSION}

COPY docker/ruby.sh /etc/profile.d

#------------------------------------------------------------------------
# NODEJS - Build NodeJS as a separate component with nvm
FROM build AS nodebuild

ARG BOWER_VERSION=1.8.8
ARG NODEJS_VERSION=16.13.2
ARG NPM_VERSION=7.20.6
ARG NVM_DIR=/opt/nodejs
ARG YARN_VERSION=1.22.10

RUN git clone --depth 1 https://github.com/nvm-sh/nvm.git $NVM_DIR
RUN . $NVM_DIR/nvm.sh \
    && nvm install "${NODEJS_VERSION}" \
    && nvm alias default "${NODEJS_VERSION}" \
    && nvm use default \
    && npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION yarn@$YARN_VERSION

#------------------------------------------------------------------------
# GOLANG - Build as a separate component
FROM build AS gobuild

ARG GO_DEP_VERSION=0.5.4
ARG GO_VERSION=1.17.3
ENV GOPATH=/opt/go
RUN curl -L https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz | tar -C /opt -xz
ENV PATH=/opt/go/bin:$PATH
RUN go version
RUN curl -ksS https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | bash
RUN echo "add_local_path /opt/go/bin:\$PATH" > /etc/profile.d/go.sh

#------------------------------------------------------------------------
# HASKELL STACK
FROM build AS haskellbuild

ARG HASKELL_STACK_VERSION=2.1.3
RUN curl -ksS https://raw.githubusercontent.com/commercialhaskell/stack/v$HASKELL_STACK_VERSION/etc/scripts/get-stack.sh | bash -s -- -d /usr/bin

#------------------------------------------------------------------------
# REPO / ANDROID SDK
FROM build AS androidbuild

ARG ANDROID_CMD_VERSION=7583922
ENV ANDROID_HOME=/opt/android-sdk

RUN curl -ksS https://storage.googleapis.com/git-repo-downloads/repo > /usr/bin/repo \
    && chmod a+x /usr/bin/repo

RUN curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && unzip -q commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip -d $ANDROID_HOME \
    && rm commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && PROXY_HOST_AND_PORT=${https_proxy#*://} \
    && if [ -n "$PROXY_HOST_AND_PORT" ]; then \
    # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
    SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi \
    && yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS \
    --sdk_root=$ANDROID_HOME "platform-tools" "cmdline-tools;latest"
COPY docker/android.sh /etc/profile.d

#------------------------------------------------------------------------
# ORT
FROM nodebuild as ortbuild

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

COPY . /usr/local/src/ort
WORKDIR /usr/local/src/ort

# Prepare Gradle
RUN --mount=type=cache,target=/tmp/.gradle/ \
    . /opt/nodejs/nvm.sh \
    && GRADLE_USER_HOME=/tmp/.gradle/ \
    && scripts/import_proxy_certs.sh \
    && scripts/set_gradle_proxy.sh \
    && sed -i -r 's,(^distributionUrl=)(.+)-all\.zip$,\1\2-bin.zip,' gradle/wrapper/gradle-wrapper.properties \
    && sed -i -r '/distributionSha256Sum=[0-9a-f]{64}/d' gradle/wrapper/gradle-wrapper.properties \
    && ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:distTar :helper-cli:startScripts

RUN mkdir -p /opt/ort \
    && tar xf /usr/local/src/ort/cli/build/distributions/ort-$ORT_VERSION.tar -C /opt/ort --strip-components 1 \
    && cp -a /usr/local/src/ort/scripts/*.sh /opt/ort/bin/ \
    && cp -a /usr/local/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/ \
    && cp -a /usr/local/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/ \
    && cd \
    && rm -rf /usr/local/src

#------------------------------------------------------------------------
# Main container
FROM eclipse-temurin:11-jdk-focal

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8


# Base package set
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    coreutils \
    cvs \
    curl \
    dirmngr \
    gcc \
    gnupg2 \
    iproute2 \
    libarchive-tools \
    libz-dev \
    locales \
    netbase \
    openssh-client \
    openssl \
    procps \
    rsync \
    sudo \
    unzip \
    wget \
    && rm -rf /var/lib/apt/lists/* \
    && sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen \
    && locale-gen

ARG USERNAME=ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID
ARG HOMEDIR=/workspace
ENV HOME=$HOMEDIR

# Run with non privileged user
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd \
    --uid $USER_ID \
    --gid $USER_GID \
    --shell /bin/bash \
    --home-dir $HOMEDIR \
    --create-home $USERNAME

# sudo support
RUN echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

COPY docker/00-add_local_path.sh /etc/profile.d/

# Python
COPY --chown=$USERNAME:$USERNAME --from=pythonbuild /opt/python /opt/python
COPY --from=pythonbuild /etc/profile.d/python.sh /etc/profile.d/

# Ruby
COPY --chown=$USERNAME:$USERNAME --from=rubybuild /opt/rbenv /opt/rbenv
COPY --from=rubybuild /etc/profile.d/ruby.sh /etc/profile.d/

# NodeJS
ARG NODEJS_VERSION=16.13.2
ARG NVM_DIR=/opt/nodejs
ENV NODE_PATH $NVM_DIR/v$NODEJS_VERSION/lib/node_modules
ENV PATH $NVM_DIR/versions/node/v$NODEJS_VERSION/bin:$PATH
COPY --chown=$USERNAME:$USERNAME --from=nodebuild /opt/nodejs /opt/nodejs

# Golang
COPY --chown=$USERNAME:$USERNAME --from=gobuild /opt/go /opt/go/
COPY --from=gobuild  /etc/profile.d/go.sh /etc/profile.d/

# Haskell
COPY --from=haskellbuild /usr/bin/stack /usr/bin

# Repo and Android
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_API_LEVEL=29
COPY --from=androidbuild /usr/bin/repo /usr/bin/
COPY --from=androidbuild /etc/profile.d/android.sh /etc/profile.d/
COPY --chown=$USERNAME:$USERNAME --from=androidbuild /opt/android-sdk /opt/android-sdk

# External repositories for SBT
ARG SBT_VERSION=1.6.1
RUN KEYURL="https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list \
    && curl -ksS "$KEYURL" | gpg --dearmor | tee "/etc/apt/trusted.gpg.d/scala_ubuntu.gpg" > /dev/null

# External repository for latest Git
RUN KEYURL="https://keyserver.ubuntu.com/pks/lookup?op=get&search=0xa1715d88e1df1f24" \
    && echo "deb http://ppa.launchpad.net/git-core/ppa/ubuntu bionic main" > /etc/apt/sources.list.d/git-core-ubuntu-ppa-bionic.list \
    && curl -ksS "$KEYURL" | gpg --dearmor | tee "/etc/apt/trusted.gpg.d/git-core_ubuntu_ppa.gpg"  > /dev/null

# External repository for Dart
RUN KEYURL="https://dl-ssl.google.com/linux/linux_signing_key.pub" \
    && LISTURL="https://storage.googleapis.com/download.dartlang.org/linux/debian/dart_stable.list" \
    && curl -ksS "$KEYURL" | gpg --dearmor | tee "/etc/apt/trusted.gpg.d/dart.gpg" > /dev/null \
    && curl -ksS "$LISTURL" > /etc/apt/sources.list.d/dart.list \
    && echo "add_local_path /usr/lib/dart/bin:\$PATH" > /etc/profile.d/dart.sh

ARG COMPOSER_VERSION=1.10.1-1

# Apt install commands.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    cargo \
    composer=$COMPOSER_VERSION \
    dart \
    git \
    sbt=$SBT_VERSION \
    subversion \
    && rm -rf /var/lib/apt/lists/*

# ORT
COPY --chown=$USERNAME:$USERNAME --from=ortbuild /opt/ort /opt/ort
COPY docker/ort-wrapper.sh /usr/bin/ort
COPY docker/ort-wrapper.sh /usr/bin/orth
RUN chmod 755 /usr/bin/ort

USER $USERNAME
WORKDIR $HOMEDIR

ENTRYPOINT ["/usr/bin/ort"]

