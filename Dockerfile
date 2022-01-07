# syntax=docker/dockerfile:1.2

# Copyright (C) 2020 Bosch Software Innovations GmbH
# Copyright (C) 2021 Bosch.IO GmbH
# Copyright (C) 2021 Alliander N.V.
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

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES=""

# Set this to the ScanCode version to use.
ARG SCANCODE_VERSION="30.1.0"
ARG ANDROID_SDK_VERSION="6858069"

FROM adoptopenjdk:11-jre-hotspot-focal AS base-image
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt --mount=type=cache,target=/opt \
    apt-get update && \
    apt-get install -y --no-install-recommends gnupg software-properties-common build-essential
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    curl -ksS "https://keyserver.ubuntu.com/pks/lookup?op=get&options=mr&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key adv --import - && \
    curl -sL https://deb.nodesource.com/setup_16.x | bash - && \
    add-apt-repository -y ppa:git-core/ppa && \
    add-apt-repository ppa:deadsnakes/ppa -y && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        # Install general tools required by this Dockerfile.
        lib32stdc++6 \
        libffi-dev \
        libgmp-dev \
        libxext6 \
        libxi6 \
        libxrender1 \
        libxtst6 \
        make \
        netbase \
        openssh-client \
        unzip \
        xz-utils \
        zlib1g-dev

FROM adoptopenjdk/openjdk11:alpine-slim AS java-build

# Apk install commands.
RUN apk add --no-cache \
        # Required for Node.js to build the reporter-web-app.
        libstdc++ \
        # Required to allow to download via a proxy with a self-signed certificate.
        ca-certificates \
        coreutils \
        openssl \
    nodejs \
    npm \
    yarn

COPY . /usr/local/src/ort

WORKDIR /usr/local/src/ort

# Gradle build.
ARG ORT_VERSION
RUN --mount=type=cache,target=/tmp/.gradle/ \
    GRADLE_USER_HOME=/tmp/.gradle/ && \
    scripts/import_proxy_certs.sh && \
    scripts/set_gradle_proxy.sh && \
    ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:distTar :helper-cli:startScripts

FROM base-image AS rust-build

ENV \
  RUST_VERSION=1.56.0 \
  RUST_PLATFORM=x86_64-unknown-linux-gnu

# Apt install commands.
RUN mkdir -p /opt && \
    curl --proto '=https' --tlsv1.2 -sSf https://static.rust-lang.org/dist/rust-${RUST_VERSION}-${RUST_PLATFORM}.tar.gz| gzip -d | (cd /opt; tar xf -) && \
    mv /opt/rust-${RUST_VERSION}-${RUST_PLATFORM} /opt/rust && \
    /opt/rust/cargo/bin/cargo -V

FROM base-image AS go-build

ENV \
    GO_DEP_VERSION=0.5.4 \
    GO_VERSION=1.17.3 \
    GOPATH=/opt/go

ENV DEBIAN_FRONTEND=noninteractive \
    PATH="$PATH:$HOME/.local/bin:/opt/go/bin"

RUN mkdir -p /opt/go && \
    curl -skSL https://go.dev/dl/go$GO_VERSION.linux-amd64.tar.gz | tar -C /opt -xz -f - && \
    curl -ksSL https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | sh

FROM base-image AS flutter-build

ENV \
    FLUTTER_VERSION=2.2.3-stable

RUN mkdir -p /opt/flutter && \
    curl -ksSL https://storage.googleapis.com/flutter_infra_release/releases/stable/linux/flutter_linux_$FLUTTER_VERSION.tar.xz | tar -xvJ -C /opt -f -

FROM base-image as scancode-build

ARG SCANCODE_VERSION

ENV \
    # Package manager versions.
    CONAN_VERSION=1.40.3 \
    PYTHON_PIPENV_VERSION=2021.5.29 \
    PYTHON_VIRTUALENV_VERSION=20.2.0 \
    PYTHON_FUTURE_VERSION=0.18.2

ENV DEBIAN_FRONTEND=noninteractive \
    PATH="$PATH:$HOME/.local/bin:/opt/go/bin:$GEM_PATH/bin"

# Apt install commands.
RUN apt-get install -y --no-install-recommends \
        python-dev \
        python-setuptools \
        python3-dev \
        python3-pip \
        python3-setuptools \
        python3-future \
        python3-virtualenv \
        python3.6

RUN mkdir -p /usr/local/bin /opt/scancode && \
    if [ ! -f /usr/bin/python ]; then \
       ln -s /usr/bin/python3.6 /usr/bin/python; \
    fi && \
    pip install --no-cache-dir wheel && \
    pip install --no-cache-dir conan==$CONAN_VERSION pipenv==$PYTHON_PIPENV_VERSION virtualenv==$PYTHON_VIRTUALENV_VERSION && \
    # Add scanners (in versions known to work).
        curl -ksSL https://github.com/nexB/scancode-toolkit/releases/download/v${SCANCODE_VERSION}/scancode-toolkit-${SCANCODE_VERSION}_py36-linux.tar.xz | \
        tar -Jx -f - -C /opt/scancode/ && \
        # Trigger ScanCode configuration for Python 3 and reindex licenses initially.
        ( cd /opt/scancode/scancode-toolkit-${SCANCODE_VERSION}; PYTHON_EXE=/usr/bin/python3.6 ./scancode --reindex-licenses ) && \
        chmod -R o=u /opt/scancode/scancode-toolkit-${SCANCODE_VERSION} && \
        ln -s /opt/scancode/scancode-toolkit-${SCANCODE_VERSION}/scancode /usr/local/bin/scancode

FROM base-image as android-sdk-buiild

ARG CRT_FILES
ARG ANDROID_SDK_VERSION

ENV \
    # Installation directories.
    ANDROID_HOME=/opt/android-sdk

COPY "$CRT_FILES" /tmp/certificates/

# Custom install commands.
RUN curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip && \
    unzip -q commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip -d $ANDROID_HOME && \
    rm commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip && \
    PROXY_HOST_AND_PORT=${https_proxy#*://} && \
    if [ -n "$PROXY_HOST_AND_PORT" ]; then \
        # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
        SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi && \
    yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS --sdk_root=$ANDROID_HOME "platform-tools"

FROM base-image

ARG CRT_FILES
ARG SCANCODE_VERSION

ENV \
    # Package manager versions.
    BOWER_VERSION=1.8.8 \
    COMPOSER_VERSION=1.10.1-1 \
    CONAN_VERSION=1.40.3 \
    HASKELL_STACK_VERSION=2.1.3 \
    NPM_VERSION=7.20.6 \
    PYTHON_PIPENV_VERSION=2021.5.29 \
    PYTHON_VIRTUALENV_VERSION=20.2.0 \
    PYTHON_FUTURE_VERSION=0.18.2 \
    SBT_VERSION=1.3.8 \
    YARN_VERSION=1.22.4 \
    # SDK versions.
    ANDROID_SDK_VERSION=6858069 \
    # Installation directories.
    ANDROID_HOME=/opt/android-sdk \
    COCOAPODS_TAG=1-10-stable \
    FLUTTER_VERSION=2.2.3-stable

ENV DEBIAN_FRONTEND=noninteractive \
    PATH="$PATH:$HOME/.local/bin:/opt/go/bin:$GEM_PATH/bin"

# Apt install commands.
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates gnupg software-properties-common && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    curl -ksS "https://keyserver.ubuntu.com/pks/lookup?op=get&options=mr&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key adv --import - && \
    curl -sL https://deb.nodesource.com/setup_16.x | bash - && \
    add-apt-repository -y ppa:git-core/ppa && \
    add-apt-repository ppa:deadsnakes/ppa -y && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        # Install general tools required by this Dockerfile.
        lib32stdc++6 \
        libffi-dev \
        libgmp-dev \
        libxext6 \
        libxi6 \
        libxrender1 \
        libxtst6 \
        make \
        netbase \
        openssh-client \
        unzip \
        xz-utils \
        zlib1g-dev \
        # Install VCS tools (no specific versions required here).
        cvs \
        git \
        mercurial \
        subversion \
        # Install package managers (in versions known to work).
        composer=$COMPOSER_VERSION \
        nodejs \
        python-dev \
        python-setuptools \
        python3-dev \
        python3-pip \
        python3-setuptools \
        python3-future \
        python3-virtualenv \
        python3.6 \
        ruby-dev \
        sbt=$SBT_VERSION \
    && \
    rm -rf /var/lib/apt/lists/*

COPY --from=java-build /usr/local/src/ort/scripts/*.sh /opt/ort/bin/

COPY "$CRT_FILES" /tmp/certificates/

# Custom install commands.
RUN /opt/ort/bin/import_proxy_certs.sh && \
    if [ -n "$CRT_FILES" ]; then \
      /opt/ort/bin/import_certificates.sh /tmp/certificates/; \
    fi && \
    # Install VCS tools (no specific versions required here).
    mkdir -p /usr/local/bin && \
    if [ ! -f /usr/bin/python ]; then \
       ln -s /usr/bin/python3 /usr/bin/python; \
    fi && \
    curl -ksS https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+x /usr/local/bin/repo && \
    # Install package managers (in versions known to work).
    npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION yarn@$YARN_VERSION && \
    pip install --no-cache-dir wheel && \
    pip install --no-cache-dir conan==$CONAN_VERSION pipenv==$PYTHON_PIPENV_VERSION virtualenv==$PYTHON_VIRTUALENV_VERSION && \
    curl -ksS https://raw.githubusercontent.com/commercialhaskell/stack/v$HASKELL_STACK_VERSION/etc/scripts/get-stack.sh | sh && \
    # Install 'CocoaPods'. As https://github.com/CocoaPods/CocoaPods/pull/10609 is needed but not yet released.
    curl -ksSL https://github.com/CocoaPods/CocoaPods/archive/9461b346aeb8cba6df71fd4e71661688138ec21b.tar.gz | \
        tar -zxC . && \
        (cd CocoaPods-9461b346aeb8cba6df71fd4e71661688138ec21b && \
            gem build cocoapods.gemspec && \
            gem install cocoapods-1.10.1.gem \
        ) && \
        rm -rf CocoaPods-9461b346aeb8cba6df71fd4e71661688138ec21b && \
    # Update installed bundler
    gem install bundler  -v '~> 2.2.0' && \
    gem install json -v '~> 2.5.0'
#    # Add cocoa pods
#    git clone https://github.com/CocoaPods/CocoaPods.git && \
#        cd CocoaPods && \
#        git checkout $COCOAPODS_TAG && \
#        gem build cocoapods.gemspec && gem install *.gem && cd .. && \
#        pod repo add main https://github.com/CocoaPods/Specs.git --allow-root

# Pull in Rust from build image
RUN mkdir -p /opt/rust
COPY --from=rust-build /opt/rust /opt/rust
RUN ln -s /opt/rust/cargo/bin/cargo /usr/local/bin

# Pull in go and godep from build image
RUN mkdir -p /opt/go
COPY --from=go-build /opt/go /opt/go
RUN ln -s /opt/go/bin/go /usr/local/bin

# Pull in flutter from build image
RUN mkdir -p /opt/flutter
COPY --from=flutter-build /opt/flutter /opt/flutter
RUN ln -s /opt/flutter/bin/cache/dart-sdk/bin/pub /opt/flutter/bin/cache/dart-sdk/bin/dart*  /usr/local/bin

# Pull in scancode from build image
RUN mkdir -p /opt/scancode
COPY --from=scancode-build /opt/scancode /opt/scancode
RUN ln -s /opt/scancode/scancode-toolkit-${SCANCODE_VERSION}/scancode /usr/local/bin/scancode

# Pull in Android SDK from build image
RUN mkdir -p ${ANDROID_HOME}
COPY --from=android-sdk-buiild ${ANDROID_HOME} ${ANDROID_HOME}

# Pull in ORT from build image
COPY --from=java-build /usr/local/src/ort/cli/build/distributions/ort-*.tar /opt/ort.tar

RUN tar xf /opt/ort.tar -C /opt/ort --strip-components 1 && \
    rm /opt/ort.tar
    # Disabled to due hardcoded scancode version in requirements check
    # /opt/ort/bin/ort requirements

COPY --from=java-build /usr/local/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/
COPY --from=java-build /usr/local/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/

COPY curations.tar /

RUN mkdir /curations && tar -xvf /curations.tar -C /curations

ENTRYPOINT ["/opt/ort/bin/ort"]
# ENTRYPOINT ["/bin/bash"]
