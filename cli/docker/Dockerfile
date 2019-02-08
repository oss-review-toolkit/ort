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

ENV SCANCODE_VERSION=2.9.7

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
        bundler=1.17.3-2 \
        composer=1.8.3-1 \
        npm=5.8.0+ds6-3 \
        python-pip=18.1-4 \
        sbt=0.13.13-2 \
    && \
    rm -rf /var/lib/apt/lists/*

# Custom install commands.
RUN \
    # Install VCS tools (no specific versions required here).
    curl https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+x /usr/local/bin/repo && \
    # Install package managers (in versions known to work).
    curl https://raw.githubusercontent.com/golang/dep/master/install.sh | \
        DEP_RELEASE_TAG="v0.4.1" GOPATH=/tmp/gopath INSTALL_DIRECTORY=/usr/local/bin sh && \
    npm install --global bower@1.8.4 yarn@1.13.0 && \
    pip install virtualenv==15.1.0 && \
    curl -sSL https://get.haskellstack.org/ | sh && \
    # Add scanners (in versions known to work).
    curl -sSL https://github.com/nexB/scancode-toolkit/archive/v$SCANCODE_VERSION.tar.gz | \
        tar -zxC /usr/local && \
        # Trigger configuration for end-users.
        /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode --version && \
        ln -s /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode /usr/local/bin/scancode
