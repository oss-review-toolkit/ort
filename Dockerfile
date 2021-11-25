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
FROM eclipse-temurin:11-jdk AS build

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
RUN --mount=type=cache,target=/var/cache/apt apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        build-essential \
        clang-9 \
        clang++-9 \
        dirmngr \
        clang-9 \
        clang++-9 \
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

# Prepare build components to install their modules
RUN mkdir -p /etc/ort/bash_modules

#------------------------------------------------------------------------
# Build ort as a separate component

FROM build as ortbuild

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

COPY . /usr/local/src/ort
WORKDIR /usr/local/src/ort

# Prepare Gradle
RUN --mount=type=cache,target=/tmp/.gradle/ \
    export GRADLE_USER_HOME=/tmp/.gradle/ && \
    scripts/import_proxy_certs.sh && \
    scripts/set_gradle_proxy.sh && \
    ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:distTar :helper-cli:startScripts

RUN mkdir -p /opt/ort \
    && tar xf /usr/local/src/ort/cli/build/distributions/ort-$ORT_VERSION.tar -C /opt/ort --strip-components 1 \
    && cp -a /usr/local/src/ort/scripts/*.sh /opt/ort/bin/ \
    && cp -a /usr/local/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/ \
    && cp -a /usr/local/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM build as pythonbuild

ARG CONAN_VERSION=1.40.3
ARG PYTHON_VERSION=3.6.15
ARG PYTHON_VIRTUALENV_VERSION=15.1.0
ARG PIPTOOL_VERSION=21.3.1

ENV PYENV_ROOT=/opt/python
RUN curl -L https://github.com/pyenv/pyenv-installer/raw/master/bin/pyenv-installer | bash
ENV PATH=/opt/python/bin:$PATH
# Python 3.6.x series has problems with alignment with modern GCC
# As we don not want patch Python versions we use Clang as compiler
RUN CC=clang-9 CXX=clang++9 pyenv install "${PYTHON_VERSION}"
RUN pyenv global "${PYTHON_VERSION}"
ENV PATH=/opt/python/shims:$PATH
RUN pip install -U \
    conan=="${CONAN_VERSION}" \
    pip=="${PIPTOOL_VERSION}" \
    pipenv \
    Mercurial \
    virtualenv=="${PYTHON_VIRTUALENV_VERSION}" \
    wheel

COPY docker/python.sh /etc/ort/bash_modules

#------------------------------------------------------------------------
# RUBY - Build Ruby as a separate component with rbenv
FROM build as rubybuild

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
    && gem install bundler cocoapods

# Install 'CocoaPods'. As https://github.com/CocoaPods/CocoaPods/pull/10609 is needed but not yet released.
RUN curl -ksSL https://github.com/CocoaPods/CocoaPods/archive/9461b346aeb8cba6df71fd4e71661688138ec21b.tar.gz | \
    tar -zxC . \
    && (cd CocoaPods-9461b346aeb8cba6df71fd4e71661688138ec21b \
        && gem build cocoapods.gemspec \
        && gem install cocoapods-1.10.1.gem \
        ) \
    && rm -rf CocoaPods-9461b346aeb8cba6df71fd4e71661688138ec21b

COPY docker/ruby.sh /etc/ort/bash_modules

#------------------------------------------------------------------------
# NODEJS - Build NodeJS as a separate component with nvm
FROM build AS nodebuild

ARG BOWER_VERSION=1.8.8
ARG NODEJS_VERSION="--lts"
ARG NPM_VERSION=7.20.6
ARG YARN_VERSION=1.22.10

ENV NVM_DIR=/opt/nodejs

RUN git clone --depth 1 https://github.com/nvm-sh/nvm.git /opt/nodejs
RUN . $NVM_DIR/nvm.sh && nvm install "${NODEJS_VERSION}"
RUN . $NVM_DIR/nvm.sh \
    && npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION yarn@$YARN_VERSION
COPY docker/nodejs.sh /etc/ort/bash_modules

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
RUN echo "add_local_path /opt/go/bin:$PATH" > /etc/ort/bash_modules/go.sh

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
COPY docker/android.sh /etc/ort/bash_modules

#------------------------------------------------------------------------
# SCANCODE - Build ScanCode as a separate component
FROM pythonbuild AS scancodebuild

ARG SCANCODE_VERSION="3.2.1rc2"
ENV SCANCODE_URL "https://github.com/nexB/scancode-toolkit/archive/refs/tags/"
ENV PATH=/opt/python/shims:$PATH

RUN mkdir -p /opt/scancode \
    && curl -ksSL ${SCANCODE_URL}/v${SCANCODE_VERSION}.tar.gz | tar -C /opt/scancode -xz --strip-components=1 \
    && cd /opt/scancode \
    && PYTHON_EXE=python3 ./configure \
    # Clean up unneeded installed binaries
    && rm -rf /opt/scancode/thirdparty \
    # Run scancode once to generate indexes as superuser
    && /opt/scancode/bin/scancode --version

#------------------------------------------------------------------------
# Main container
FROM eclipse-temurin:11-jre

# ORT
COPY --from=ortbuild /opt/ort /opt/ort
COPY docker/bash_bootstrap.sh /etc/ort/bash_bootstrap.sh
COPY docker/ort-wrapper.sh /usr/bin/ort
COPY docker/ort-wrapper.sh /usr/bin/orth
RUN chmod 755 /usr/bin/ort

# Python
COPY --from=pythonbuild /opt/python /opt/python
COPY --from=pythonbuild /etc/ort/bash_modules/python.sh /etc/ort/bash_modules/

# Ruby
COPY --from=rubybuild /opt/rbenv /opt/rbenv
COPY --from=rubybuild /etc/ort/bash_modules/ruby.sh /etc/ort/bash_modules/

# NodeJS
COPY --from=nodebuild /opt/nodejs /opt/nodejs
COPY --from=nodebuild /etc/ort/bash_modules/nodejs.sh /etc/ort/bash_modules/

# Golang
COPY --from=gobuild /opt/go /opt/go/
COPY --from=gobuild  /etc/ort/bash_modules/go.sh /etc/ort/bash_modules/

# Haskell
COPY --from=haskellbuild /usr/bin/stack /usr/bin

# Repo and Android
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_API_LEVEL=29
COPY --from=androidbuild /usr/bin/repo /usr/bin/
COPY --from=androidbuild /opt/android-sdk /opt/android-sdk
COPY --from=androidbuild /etc/ort/bash_modules/android.sh /etc/ort/bash_modules/

# ScanCode
COPY --from=scancodebuild /opt/scancode /opt/scancode
RUN ln -s /opt/scancode/bin/scancode /usr/bin/scancode \
    && ln -s /opt/scancode/bin/pip /usr/bin/scancode-pip \
    && ln -s /opt/scancode/bin/extractcode /usr/bin/extractcode

RUN  --mount=type=cache,target=/var/cache/apt apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        cvs \
        curl \
        gnupg \
        libarchive-tools \
        netbase \
        openssh-client \
        openssl \
        unzip \
    && rm -rf /var/lib/apt/lists/*

# External repositories for SBT
ARG SBT_VERSION=1.3.8
RUN KEYURL="https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list \
    && curl -ksS "$KEYURL" | gpg --dearmor | tee "/etc/apt/trusted.gpg.d/scala_ubuntu.gpg" > /dev/null

# External repository for latest Git
RUN KEYURL="https://keyserver.ubuntu.com/pks/lookup?op=get&search=0xa1715d88e1df1f24" \
    && echo "deb http://ppa.launchpad.net/git-core/ppa/ubuntu bionic main" > /etc/apt/sources.list.d/git-core-ubuntu-ppa-bionic.list \
    && curl -ksS "$KEYURL" | gpg --dearmor | tee "/etc/apt/trusted.gpg.d/git-core_ubuntu_ppa.gpg"  > /dev/null

ARG COMPOSER_VERSION=1.10.1-1

# Apt install commands.
RUN --mount=type=cache,target=/var/cache/apt apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        cargo \
        composer=$COMPOSER_VERSION \
        git \
        sbt=$SBT_VERSION \
        subversion \
    && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["/usr/bin/ort"]

