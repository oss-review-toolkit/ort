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

FROM openjdk:11-jre-slim-sid

ENV SCANCODE_VERSION=2.9.7

RUN apt update && apt install -y --no-install-recommends curl

# Add VCS tools (no specific versions required here).
RUN apt install -y --no-install-recommends \
        cvs \
        git \
        mercurial \
        subversion \
    && \

    curl https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo && \
    chmod a+x /usr/local/bin/repo

# Add package managers (in versions known to work).
RUN apt install -y --no-install-recommends \
        composer=1.8.0-1 \
        npm=5.8.0+ds6-3 \
        python-pip=18.1-4 \
        bundler=1.16.1-3 \
        sbt=0.13.13-2 \
    && \

    npm install --global bower@1.8.4 yarn@1.9.4 && \
    pip install virtualenv==15.1.0 && \

    curl https://raw.githubusercontent.com/golang/dep/7d5cd199ce454707f81c63b7ea4299151b8b981d/install.sh | \
        DEP_RELEASE_TAG="v0.4.1" GOPATH=/tmp/gopath INSTALL_DIRECTORY=/usr/local/bin sh && \

    curl -sSL https://get.haskellstack.org/ | sh

# Add scanners (in versions known to work).
RUN curl -sSL https://github.com/nexB/scancode-toolkit/archive/v$SCANCODE_VERSION.tar.gz | \
        tar -zxC /usr/local && \
        # Trigger configuration for end-users:
        /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode --version && \
        ln -s /usr/local/scancode-toolkit-$SCANCODE_VERSION/scancode /usr/local/bin/scancode

RUN rm -rf /var/lib/apt/lists/*
