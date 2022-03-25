# syntax=docker/dockerfile:1.3

# Copyright (C) 2020 Bosch Software Innovations GmbH
# Copyright (C) 2021-2022 Bosch.IO GmbH
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

FROM eclipse-temurin:11-jdk-focal AS build

COPY . /usr/local/src/ort

WORKDIR /usr/local/src/ort

# Gradle build.
ARG ORT_VERSION
RUN --mount=type=cache,target=/tmp/.gradle/ \
    export GRADLE_USER_HOME=/tmp/.gradle/ && \
    scripts/import_proxy_certs.sh && \
    scripts/set_gradle_proxy.sh && \
    sed -i -r 's,(^distributionUrl=)(.+)-all\.zip$,\1\2-bin.zip,' gradle/wrapper/gradle-wrapper.properties && \
    sed -i -r '/distributionSha256Sum=[0-9a-f]{64}/d' gradle/wrapper/gradle-wrapper.properties && \
    ./gradlew --no-daemon --stacktrace -Pversion=$ORT_VERSION :cli:distTar :helper-cli:startScripts

FROM eclipse-temurin:11-jdk-focal AS run

ENV \
    # Package manager versions.
    BOWER_VERSION=1.8.12 \
    CARGO_VERSION=0.58.0-0ubuntu1~20.04.1 \
    COCOAPODS_VERSION=1.11.2 \
    COMPOSER_VERSION=1.10.1-1 \
    CONAN_VERSION=1.45.0 \
    GO_DEP_VERSION=0.5.4 \
    GO_VERSION=1.16.5 \
    HASKELL_STACK_VERSION=2.1.3 \
    NPM_VERSION=8.5.0 \
    PYTHON_PIPENV_VERSION=2018.11.26 \
    PYTHON_VIRTUALENV_VERSION=15.1.0 \
    SBT_VERSION=1.6.1 \
    YARN_VERSION=1.22.10 \
    # SDK versions.
    ANDROID_SDK_VERSION=6858069 \
    # Installation directories.
    ANDROID_HOME=/opt/android-sdk \
    GOPATH=/tmp/go

ENV DEBIAN_FRONTEND=noninteractive \
    PATH="$PATH:$GOPATH/bin:/opt/go/bin:/opt/ort/bin"

# Apt install commands.
RUN --mount=type=cache,target=/var/cache/apt --mount=type=cache,target=/var/lib/apt \
    apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates gnupg software-properties-common && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee -a /etc/apt/sources.list.d/sbt.list && \
    curl -ksS "https://keyserver.ubuntu.com/pks/lookup?op=get&options=mr&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | apt-key adv --import - && \
    curl -sL https://deb.nodesource.com/setup_16.x | bash - && \
    add-apt-repository -y ppa:git-core/ppa && \
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
        cargo=$CARGO_VERSION \
        composer=$COMPOSER_VERSION \
        nodejs \
        python-dev \
        python-setuptools \
        python3-dev \
        python3-pip \
        python3-setuptools \
        ruby-dev \
        sbt=$SBT_VERSION \
    && \
    rm -rf /var/lib/apt/lists/*

COPY scripts/*.sh /opt/ort/bin/

ARG CRT_FILES
COPY "$CRT_FILES" /tmp/certificates/

# Custom install commands.
RUN /opt/ort/bin/import_proxy_certs.sh && \
    if [ -n "$CRT_FILES" ]; then \
      /opt/ort/bin/import_certificates.sh /tmp/certificates/; \
    fi && \
    # Install VCS tools (no specific versions required here).
    curl -ksS https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+x /usr/local/bin/repo && \
    # Install package managers (in versions known to work).
    npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION yarn@$YARN_VERSION && \
    pip install --no-cache-dir wheel && \
    pip install --no-cache-dir conan==$CONAN_VERSION pipenv==$PYTHON_PIPENV_VERSION virtualenv==$PYTHON_VIRTUALENV_VERSION && \
    # Install golang in order to have `go mod` as package manager.
    curl -ksSO https://dl.google.com/go/go$GO_VERSION.linux-amd64.tar.gz && \
    tar -C /opt -xzf go$GO_VERSION.linux-amd64.tar.gz && \
    rm go$GO_VERSION.linux-amd64.tar.gz && \
    export GOBIN=/opt/go/bin && \
    curl -ksS https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | sh && \
    curl -ksS https://raw.githubusercontent.com/commercialhaskell/stack/v$HASKELL_STACK_VERSION/etc/scripts/get-stack.sh | sh && \
    # Install SDKs required for analysis.
    curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip && \
    unzip -q commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip -d $ANDROID_HOME && \
    rm commandlinetools-linux-${ANDROID_SDK_VERSION}_latest.zip && \
    PROXY_HOST_AND_PORT=${https_proxy#*://} && \
    if [ -n "$PROXY_HOST_AND_PORT" ]; then \
        # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
        SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi && \
    yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS --sdk_root=$ANDROID_HOME "platform-tools" && \
    chmod -R o+w $ANDROID_HOME && \
    # Install 'CocoaPods'.
    gem install cocoapods -v $COCOAPODS_VERSION

# Add scanners (in versions known to work).
ARG SCANCODE_VERSION
RUN pip install --no-cache-dir scancode-toolkit==$SCANCODE_VERSION

FROM run

COPY --from=build /usr/local/src/ort/cli/build/distributions/ort-*.tar /opt/ort.tar

RUN tar xf /opt/ort.tar -C /opt/ort --exclude="*.bat" --strip-components 1 && \
    rm /opt/ort.tar && \
    /opt/ort/bin/ort requirements

COPY --from=build /usr/local/src/ort/helper-cli/build/scripts/orth /opt/ort/bin/
COPY --from=build /usr/local/src/ort/helper-cli/build/libs/helper-cli-*.jar /opt/ort/lib/

ENTRYPOINT ["/opt/ort/bin/ort"]
