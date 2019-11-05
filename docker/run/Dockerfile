# Copyright (C) 2017-2019 HERE Europe B.V.
# Copyright (C) 2019 Bosch Software Innovations GmbH
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

# The source code for the binaries installed to the Docker image as part of this
# Dockerfile can be downloaded from the locations listed below.
#
# - Base image: https://github.com/docker-library/openjdk/blob/master/11/jre/slim/Dockerfile
# - Debian Sid packages: https://sources.debian.org/
# - git-repo tool: https://gerrit-review.googlesource.com/admin/repos/git-repo
# - NPM packages: https://www.npmjs.com/
# - Python packages: https://pypi.org/
# - Go language's dep tool: https://github.com/golang/dep
# - Haskell language's stack tool: https://github.com/commercialhaskell/stack
# - ScanCode tool: https://github.com/nexB/scancode-toolkit

FROM openjdk:11-jre-slim-sid

ENV \
    # Package manager versions.
    BOWER_VERSION=1.8.8 \
    BUNDLER_VERSION=1.17.3-3 \
    COMPOSER_VERSION=1.9.1-1 \
    CONAN_VERSION=1.18.0 \
    FLUTTER_VERSION=v1.7.8+hotfix.3-stable \
    GO_DEP_VERSION=0.5.4-2 \
    HASKELL_STACK_VERSION=1.7.1-3 \
    NPM_VERSION=5.8.0+DS6-4 \
    PYTHON_PIP_VERSION=18.1-5 \
    PYTHON_PIPENV_VERSION=2018.11.26 \
    PYTHON_VIRTUALENV_VERSION=15.1.0 \
    SBT_VERSION=0.13.13-2 \
    YARN_VERSION=1.17.3 \
    # Scanner versions.
    SCANCODE_VERSION=3.0.2 \
    # Installation directories.
    FLUTTER_HOME=/opt/flutter

ENV PATH="$PATH:$FLUTTER_HOME/bin:$FLUTTER_HOME/bin/cache/dart-sdk/bin"

# Apt install commands.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        # Install general tools required by this Dockefile.
        curl \
        lib32stdc++6 \
        openssh-client \
        # Install VCS tools (no specific versions required here).
        cvs \
        git \
        mercurial \
        subversion \
        # Install package managers (in versions known to work).
        bundler=$BUNDLER_VERSION \
        composer=$COMPOSER_VERSION \
        go-dep=$GO_DEP_VERSION \
        haskell-stack=$HASKELL_STACK_VERSION \
        npm=$NPM_VERSION \
        python-pip=$PYTHON_PIP_VERSION \
        python-setuptools \
        python3-pip=$PYTHON_PIP_VERSION \
        python3-setuptools \
        sbt=$SBT_VERSION \
    && \
    rm -rf /var/lib/apt/lists/*

# Custom install commands.
RUN \
    # Install VCS tools (no specific versions required here).
    curl https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+x /usr/local/bin/repo && \
    # Install package managers (in versions known to work).
    npm install --global bower@$BOWER_VERSION yarn@$YARN_VERSION && \
    pip install pipenv==$PYTHON_PIPENV_VERSION && \
    pip install virtualenv==$PYTHON_VIRTUALENV_VERSION && \
    curl -Os https://storage.googleapis.com/flutter_infra/releases/stable/linux/flutter_linux_$FLUTTER_VERSION.tar.xz && \
    tar xf flutter_linux_$FLUTTER_VERSION.tar.xz -C $(dirname $FLUTTER_HOME) && \
    chmod -R a+rw $FLUTTER_HOME && \
    flutter config --no-analytics && \
    flutter doctor && \
    pip install conan==$CONAN_VERSION && \
    # Add scanners (in versions known to work).
    curl -sSL https://github.com/nexB/scancode-toolkit/archive/v$SCANCODE_VERSION.tar.gz | \
        tar -zxC /usr/local && \
        # Trigger configuration for end-users.
        /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode --version && \
        chmod -R o=u /usr/local/scancode-toolkit-$SCANCODE_VERSION && \
        ln -s /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode /usr/local/bin/scancode

ARG ORT_VERSION
ADD ort-$ORT_VERSION.tar .

# As the ENTRYPOINT cannot contain variables, create a symbolic link to the specific version instead.
RUN ln -s /ort-$ORT_VERSION /ort
ENTRYPOINT ["/ort/bin/ort"]
