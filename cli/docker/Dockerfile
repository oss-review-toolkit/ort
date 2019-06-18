# Copyright (C) 2017-2019 HERE Europe B.V.
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
    COMPOSER_VERSION=1.8.4-1 \
    GO_DEP_VERSION=0.5.1+really0.5.0-1 \
    HASKELL_STACK_VERSION=1.7.1-3 \
    NPM_VERSION=5.8.0+DS6-4 \
    PYTHON_PIP_VERSION=18.1-5 \
    PYTHON_VIRTUALENV_VERSION=15.1.0 \
    SBT_VERSION=0.13.13-2 \
    YARN_VERSION=1.16.0 \
    # Scanner versions.
    SCANCODE_VERSION=2.9.7

# Apt install commands.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        # Install general tools required by this Dockefile.
        curl \
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
    pip install virtualenv==$PYTHON_VIRTUALENV_VERSION && \
    # Add scanners (in versions known to work).
    curl -sSL https://github.com/nexB/scancode-toolkit/archive/v$SCANCODE_VERSION.tar.gz | \
        tar -zxC /usr/local && \
        # Trigger configuration for end-users.
        /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode --version && \
        ln -s /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode /usr/local/bin/scancode
