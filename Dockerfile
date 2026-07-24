# syntax=devthefuture/dockerfile-x:v1.5.0
# The above opts-in for an extended syntax that supports e.g. "INCLUDE" statements, see
# https://codeberg.org/devthefuture/dockerfile-x

# Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

INCLUDE_ARGS .env.versions

# Use OpenJDK Eclipe Temurin Ubuntu LTS
FROM eclipse-temurin:$JAVA_VERSION-jdk-$UBUNTU_VERSION AS base

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Check and set apt proxy
COPY scripts/set_apt_proxy.sh /etc/scripts/set_apt_proxy.sh
RUN /etc/scripts/set_apt_proxy.sh

# Base package set
RUN --mount=type=cache,target=/var/cache,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    --mount=type=tmpfs,target=/var/log \
    apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends extrepo \
    && extrepo enable mise \
    && apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    coreutils \
    curl \
    dirmngr \
    file \
    gcc \
    git \
    git-lfs \
    g++ \
    gnupg2 \
    iproute2 \
    libarchive-tools \
    libffi-dev \
    libgmp-dev \
    libmagic1 \
    libyaml-dev \
    libz-dev \
    locales \
    lzma \
    make \
    mise \
    netbase \
    openssh-client \
    openssl \
    procps \
    rng-tools5 \
    rsync \
    sudo \
    tzdata \
    uuid-dev \
    unzip \
    xz-utils \
    && git lfs install

RUN echo $LANG > /etc/locale.gen \
    && locale-gen $LANG \
    && update-locale LANG=$LANG

ARG USERNAME=ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID
ARG HOMEDIR=/home/ort

# Non privileged user
RUN --mount=type=tmpfs,target=/var/log \
    userdel -r ubuntu && \
    groupadd --gid $USER_GID $USERNAME && \
    useradd \
    --uid $USER_ID \
    --gid $USER_GID \
    --shell /bin/bash \
    --home-dir $HOMEDIR \
    --create-home $USERNAME

RUN chgrp $USERNAME /opt \
    && chmod g+wx /opt

# sudo support
RUN echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Make curl trust system certificates both at container build timeand run time.
RUN echo "ca-native" > /etc/curlrc

# Copy certificates scripts only.
COPY scripts/*_certificates.sh /etc/scripts/

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES="*.crt"
COPY "$CRT_FILES" /tmp/certificates/

RUN /etc/scripts/export_proxy_certificates.sh /tmp/certificates/ \
    && /etc/scripts/import_certificates.sh /tmp/certificates/

USER $USERNAME
WORKDIR $HOMEDIR
ENV USER=$USERNAME
ENV HOME=$HOMEDIR

# Make nodejs tools trust system certificates.
ENV NODE_EXTRA_CA_CERTS=/etc/ssl/certs/ca-certificates.crt

ENTRYPOINT [ "/bin/bash" ]

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM base AS python-build

ARG CONAN_VERSION
ARG CONAN2_VERSION
ARG PIP_VERSION
# PYENV_GIT_TAG is consumed as described here:
# https://github.com/pyenv/pyenv-installer/blob/63a9e6a216796aeba2535a3bac8e79ba5d95166d/README.rst?plain=1#L22.
ARG PYENV_GIT_TAG
ARG PYTHON_INSPECTOR_VERSION
ARG PYTHON_PIPENV_VERSION
ARG PYTHON_POETRY_PLUGIN_EXPORT_VERSION
ARG PYTHON_POETRY_VERSION
ARG PYTHON_SETUPTOOLS_VERSION
ARG PYTHON_SETUPTOOLS_RUST_VERSION=1.13.0
ARG PYTHON_VERSION
ARG SCANCODE_VERSION

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    libreadline-dev \
    libgdbm-dev \
    libicu-dev \
    libsqlite3-dev \
    libssl-dev \
    libbz2-dev \
    liblzma-dev \
    tk-dev

ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:$PYENV_ROOT/shims:$PYENV_ROOT/bin:$PYENV_ROOT/conan2/bin
RUN curl -kSs https://pyenv.run | bash \
    && pyenv install -v $PYTHON_VERSION \
    && pyenv global $PYTHON_VERSION

RUN if [ "$(arch)" = "aarch64" ]; then \
        pip install -U scancode-toolkit-mini==$SCANCODE_VERSION licensedcode-data setuptools==$PYTHON_SETUPTOOLS_VERSION; \
    else \
        curl -Os https://raw.githubusercontent.com/aboutcode-org/scancode-toolkit/v$SCANCODE_VERSION/requirements.txt; \
        pip install -U --constraint requirements.txt scancode-toolkit==$SCANCODE_VERSION setuptools==$PYTHON_SETUPTOOLS_VERSION; \
        rm requirements.txt; \
    fi

# Extract ScanCode license texts to a directory.
RUN scancode-license-data --path /opt/scancode-license-data \
    && find /opt/scancode-license-data -type f -not -name "*.LICENSE" -exec rm -f {} + \
    && rm -rf /opt/scancode-license-data/static

RUN pip install --no-cache-dir -U \
    pip=="$PIP_VERSION" \
    wheel \
    && pip install --no-cache-dir -U \
    Mercurial \
    conan=="$CONAN_VERSION" \
    pipenv=="$PYTHON_PIPENV_VERSION" \
    poetry=="$PYTHON_POETRY_VERSION" \
    poetry-plugin-export=="$PYTHON_POETRY_PLUGIN_EXPORT_VERSION" \
    python-inspector=="$PYTHON_INSPECTOR_VERSION" \
    setuptools=="$PYTHON_SETUPTOOLS_VERSION" \
    setuptools_rust=="$PYTHON_SETUPTOOLS_RUST_VERSION"
RUN mkdir /tmp/conan2 \
    && curl -L https://github.com/conan-io/conan/releases/download/$CONAN2_VERSION/conan-$CONAN2_VERSION-linux-$(arch).tgz | tar -xz -C /tmp/conan2 \
    # Rename the Conan 2 executable to "conan2" to be able to call both Conan version from the package manager.
    && mkdir $PYENV_ROOT/conan2 && mv /tmp/conan2/bin $PYENV_ROOT/conan2/ \
    && mv $PYENV_ROOT/conan2/bin/conan $PYENV_ROOT/conan2/bin/conan2

RUN find /opt/python -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null || true

#------------------------------------------------------------------------
# NODEJS - Build NodeJS as a separate component with nvm
FROM base AS nodejs-build

ARG BOWER_VERSION
ARG COREPACK_VERSION
ARG NODEJS_VERSION
ARG USER_ID=1000
ARG USER_GID=$USER_ID

ENV NVM_DIR=/opt/nvm
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin

RUN git clone --depth 1 https://github.com/nvm-sh/nvm.git $NVM_DIR
RUN --mount=type=cache,target=/opt/nvm/.cache,uid=$USER_ID,gid=$USER_GID \
    . $NVM_DIR/nvm.sh \
    && nvm install "$NODEJS_VERSION" \
    && nvm alias default "$NODEJS_VERSION" \
    && nvm use default \
    && npm install --global bower@$BOWER_VERSION corepack@$COREPACK_VERSION \
    && corepack enable

#------------------------------------------------------------------------
# RUBY - Build Ruby as a separate component
FROM base AS ruby-build

ARG COCOAPODS_VERSION
ARG LICENSEE_VERSION
ARG RUBY_VERSION

ENV RUBY_ROOT=/opt/ruby
RUN sudo mise install-into ruby@$RUBY_VERSION $RUBY_ROOT

RUN sudo $RUBY_ROOT/bin/gem install cocoapods:$COCOAPODS_VERSION licensee:$LICENSEE_VERSION

#------------------------------------------------------------------------
# RUST - Build as a separate component
FROM base AS rust-build

ARG RUST_VERSION

ENV RUST_HOME=/opt/rust
ENV CARGO_HOME=$RUST_HOME/cargo
ENV RUSTUP_HOME=$RUST_HOME/rustup
RUN curl -ksSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain $RUST_VERSION

#------------------------------------------------------------------------
# GOLANG - Build as a separate component
FROM base AS go-build

ARG GO_VERSION

ENV GOBIN=/opt/go/bin
ENV PATH=$PATH:/opt/go/bin

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN ARCH=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/amd64/) \
    && curl -L https://dl.google.com/go/go$GO_VERSION.linux-$ARCH.tar.gz | tar -C /opt -xz

#------------------------------------------------------------------------
# HASKELL STACK
FROM base AS haskell-build

ARG HASKELL_STACK_VERSION

ENV PATH=$PATH:$HOME/.ghcup/bin

RUN curl --proto '=https' --tlsv1.2 -sSf https://get-ghcup.haskell.org | BOOTSTRAP_HASKELL_MINIMAL=1 BOOTSTRAP_HASKELL_NONINTERACTIVE=1 sh && \
    ghcup install stack $HASKELL_STACK_VERSION && \
    mv $HOME/.ghcup /opt/haskell

#------------------------------------------------------------------------
# REPO / ANDROID SDK
FROM base AS android-build

ARG ANDROID_CMD_VERSION

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    unzip

ENV ANDROID_HOME=/opt/android-sdk

RUN mkdir /tmp/android && chmod =1777 /tmp/android
RUN --mount=type=tmpfs,target=/tmp/android \
    cd /tmp/android \
    && curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && unzip -q commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip -d $ANDROID_HOME \
    && PROXY_HOST_AND_PORT=${https_proxy#*://} \
    && PROXY_HOST_AND_PORT=${PROXY_HOST_AND_PORT%/} \
    && if [ -n "$PROXY_HOST_AND_PORT" ]; then \
        # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
        SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi \
    && yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS --sdk_root=$ANDROID_HOME "platform-tools" "cmdline-tools;latest"

RUN curl -ksS https://storage.googleapis.com/git-repo-downloads/repo > $ANDROID_HOME/cmdline-tools/bin/repo \
    && sudo chmod a+x $ANDROID_HOME/cmdline-tools/bin/repo

RUN chmod -R o+rw "$ANDROID_HOME"

#------------------------------------------------------------------------
# Dart
FROM base AS dart-build

ARG DART_VERSION

WORKDIR /opt/

ENV DART_SDK=/opt/dart-sdk
ENV PATH=$PATH:$DART_SDK/bin

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN mkdir /tmp/dart && chmod =1777 /tmp/dart
RUN --mount=type=tmpfs,target=/tmp/dart \
    ARCH=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/x64/) \
    && curl -o /tmp/dart/dart.zip -L https://storage.googleapis.com/dart-archive/channels/stable/release/$DART_VERSION/sdk/dartsdk-linux-$ARCH-release.zip \
    && unzip /tmp/dart/dart.zip

#------------------------------------------------------------------------
# SBT
FROM base AS scala-build

ARG SBT_VERSION

ENV SBT_HOME=/opt/sbt
ENV PATH=$PATH:$SBT_HOME/bin

RUN curl -L https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz | tar -C /opt -xz

#------------------------------------------------------------------------
# SWIFT
FROM base AS swift-build

ARG SWIFT_VERSION

ENV SWIFT_HOME=/opt/swift
ENV PATH=$PATH:$SWIFT_HOME/bin

RUN mkdir -p $SWIFT_HOME \
    && echo $SWIFT_VERSION \
    && if [ "$(arch)" = "aarch64" ]; then \
        SWIFT_PACKAGE="ubuntu2404-aarch64/swift-$SWIFT_VERSION-RELEASE/swift-$SWIFT_VERSION-RELEASE-ubuntu24.04-aarch64.tar.gz"; \
    else \
        SWIFT_PACKAGE="ubuntu2404/swift-$SWIFT_VERSION-RELEASE/swift-$SWIFT_VERSION-RELEASE-ubuntu24.04.tar.gz"; \
    fi \
    && curl -L https://download.swift.org/swift-$SWIFT_VERSION-release/$SWIFT_PACKAGE \
    | tar -xz -C $SWIFT_HOME --strip-components=2 \
    # Prune Swift installation: remove debugging tools, IDE support, static libraries,
    # sanitizers, and other components not needed for 'swift package show-dependencies'.
    && rm -rf \
    $SWIFT_HOME/bin/{*-swift-linux-musl-clang*,clangd,docc,lldb*,plutil,repl_swift,sourcekit-lsp,wasm-ld,wasmkit} \
    $SWIFT_HOME/bin/llvm-{cov,objcopy,objdump,profdata,symbolizer} \
    $SWIFT_HOME/bin/swift-{api-checker.py,build-sdk-interfaces,demangle,format,help} \
    $SWIFT_HOME/lib/{clang/*/lib,liblldb*,libLTO*,libsourcekitdInProc.so,lldb,sourcekitd.framework,swift_static} \
    $SWIFT_HOME/lib/swift/{embedded,FrameworkABIBaseline,migrator} \
    $SWIFT_HOME/{libexec,local,share}

#------------------------------------------------------------------------
# DOTNET
FROM base AS dotnet-build

ARG DOTNET_VERSION
ARG NUGET_INSPECTOR_VERSION

ENV DOTNET_HOME=/opt/dotnet
ENV NUGET_INSPECTOR_HOME=$DOTNET_HOME
ENV PATH=$PATH:$DOTNET_HOME:$DOTNET_HOME/tools:$DOTNET_HOME/bin

# Note: We are not installing a dotnet package directly because
# debian packages from Ubuntu and Microsoft are incomplete

RUN mkdir -p $DOTNET_HOME \
    && echo $DOTNET_VERSION \
    && if [ "$(arch)" = "aarch64" ]; then \
        curl -L https://aka.ms/dotnet/$DOTNET_VERSION/dotnet-sdk-linux-arm64.tar.gz | tar -C $DOTNET_HOME -xz; \
    else \
        curl -L https://aka.ms/dotnet/$DOTNET_VERSION/dotnet-sdk-linux-x64.tar.gz | tar -C $DOTNET_HOME -xz; \
    fi

RUN mkdir -p $DOTNET_HOME/bin \
    && curl -L https://github.com/aboutcode-org/nuget-inspector/releases/download/v$NUGET_INSPECTOR_VERSION/nuget-inspector-v$NUGET_INSPECTOR_VERSION-linux-x64.tar.gz \
    | tar --strip-components=1 -C $DOTNET_HOME/bin -xz \
    # Prune .NET installation: keep only the runtime needed for nuget-inspector.
    && rm -rf $DOTNET_HOME/{templates,packs,sdk,sdk-manifests} \
    && rm -rf $DOTNET_HOME/shared/Microsoft.AspNetCore.App

#------------------------------------------------------------------------
# BAZEL
FROM base AS bazel-build

ARG BAZELISK_VERSION

ENV BAZEL_HOME=/opt/bazel
ENV GOBIN=/opt/go/bin

RUN mkdir -p $BAZEL_HOME/bin \
    && if [ "$(arch)" = "aarch64" ]; then \
        curl -L https://github.com/bazelbuild/bazelisk/releases/download/v$BAZELISK_VERSION/bazelisk-linux-arm64 -o $BAZEL_HOME/bin/bazel; \
    else \
        curl -L https://github.com/bazelbuild/bazelisk/releases/download/v$BAZELISK_VERSION/bazelisk-linux-amd64 -o $BAZEL_HOME/bin/bazel; \
    fi \
    && chmod a+x $BAZEL_HOME/bin/bazel

COPY --from=go-build /opt/go /opt/go

RUN $GOBIN/go install github.com/bazelbuild/buildtools/buildozer@latest && chmod a+x $GOBIN/buildozer

#------------------------------------------------------------------------
# Cosign for signature verification
FROM ghcr.io/sigstore/cosign/cosign:v$COSIGN_VERSION AS cosign

#------------------------------------------------------------------------
# Elixir (Mix SBoM)
FROM base AS mix-sbom-build

COPY --from=cosign /ko-app/cosign /usr/local/bin/cosign

ARG MIX_SBOM_VERSION

ENV MIX_SBOM_HOME=/opt/mix_sbom

# Download and verify mix_sbom binary signature
RUN mkdir -p $MIX_SBOM_HOME/bin \
    && ARCH=$(arch | sed s/aarch64/ARM64/ | sed s/x86_64/X64/) \
    && curl -sSL "https://github.com/erlef/mix_sbom/releases/download/v${MIX_SBOM_VERSION}/mix_sbom_Linux_${ARCH}" \
       -o $MIX_SBOM_HOME/bin/mix_sbom \
    && curl -sSL "https://github.com/erlef/mix_sbom/releases/download/v${MIX_SBOM_VERSION}/mix_sbom_Linux_${ARCH}.sigstore" \
       -o /tmp/mix_sbom.sigstore \
    && cosign verify-blob \
       --bundle /tmp/mix_sbom.sigstore \
       --certificate-identity-regexp "^https://github.com/erlef/mix_sbom/.*@refs/tags/v${MIX_SBOM_VERSION}$" \
       --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
       $MIX_SBOM_HOME/bin/mix_sbom \
    && chmod a+x $MIX_SBOM_HOME/bin/mix_sbom \
    && rm /tmp/mix_sbom.sigstore \
    && $MIX_SBOM_HOME/bin/mix_sbom --version

#------------------------------------------------------------------------
# Erlang (Rebar3 SBoM wrapped in Bombom)
FROM base AS rebar3-sbom-build

COPY --from=cosign /ko-app/cosign /usr/local/bin/cosign

ARG BOMBOM_VERSION

ENV BOMBOM_HOME=/opt/bombom

# Download and verify bombom binary signature
RUN mkdir -p $BOMBOM_HOME/bin \
    && ARCH=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/amd64/) \
    && curl -sSL "https://github.com/erlef/bombom/releases/download/${BOMBOM_VERSION}/bombom-linux-${ARCH}.bin" \
       -o $BOMBOM_HOME/bin/bombom \
    && curl -sSL "https://github.com/erlef/bombom/releases/download/${BOMBOM_VERSION}/bombom-linux-${ARCH}.bin.sigstore" \
       -o /tmp/bombom.sigstore \
    && cosign verify-blob \
       --bundle /tmp/bombom.sigstore \
       --certificate-identity-regexp "^https://github.com/erlef/bombom/.*@refs/tags/${BOMBOM_VERSION}$" \
       --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
       $BOMBOM_HOME/bin/bombom \
    && chmod a+x $BOMBOM_HOME/bin/bombom \
    && rm /tmp/bombom.sigstore

#------------------------------------------------------------------------
# ORT
FROM base AS ort-build

# Set this to the version ORT should report.
ARG ORT_VERSION="DOCKER-SNAPSHOT"

WORKDIR $HOME/src/ort

# Prepare Gradle
RUN --mount=type=cache,target=/var/tmp/gradle \
    --mount=type=bind,target=$HOME/src/ort,rw \
    export GRADLE_USER_HOME=/var/tmp/gradle \
    && sudo chown -R $USER $HOME/src/ort /var/tmp/gradle \
    && scripts/set_gradle_proxy.sh \
    && ./gradlew --no-daemon --stacktrace \
    -Pversion=$ORT_VERSION \
    :cli:installDist \
    :cli-helper:startScripts \
    && mkdir /opt/ort \
    && cp -a $HOME/src/ort/cli/build/install/ort /opt/ \
    && cp -a $HOME/src/ort/scripts/*.sh /opt/ort/bin/ \
    && cp -a $HOME/src/ort/cli-helper/build/scripts/orth /opt/ort/bin/ \
    && cp -a $HOME/src/ort/cli-helper/build/libs/cli-helper-*.jar /opt/ort/lib/

#------------------------------------------------------------------------
# Gleam
FROM base AS gleam-build

COPY --from=cosign /ko-app/cosign /usr/local/bin/cosign

ARG GLEAM_VERSION

ENV GLEAM_HOME=/opt/gleam

# Download and verify Gleam binary signature
RUN mkdir -p $GLEAM_HOME/bin \
    && ARCH=$(arch) \
    && curl -sSL "https://github.com/gleam-lang/gleam/releases/download/v${GLEAM_VERSION}/gleam-v${GLEAM_VERSION}-${ARCH}-unknown-linux-musl.tar.gz" \
       -o /tmp/gleam.tar.gz \
    && curl -sSL "https://github.com/gleam-lang/gleam/releases/download/v${GLEAM_VERSION}/gleam-v${GLEAM_VERSION}-${ARCH}-unknown-linux-musl.tar.gz.sigstore" \
       -o /tmp/gleam.sigstore \
    && cosign verify-blob \
       --bundle /tmp/gleam.sigstore \
       --certificate-identity-regexp "^https://github.com/gleam-lang/gleam/.*@refs/tags/v${GLEAM_VERSION}$" \
       --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
       /tmp/gleam.tar.gz \
    && tar -xzf /tmp/gleam.tar.gz -C $GLEAM_HOME/bin \
    && chmod a+x $GLEAM_HOME/bin/gleam \
    && rm /tmp/gleam.tar.gz /tmp/gleam.sigstore \
    && $GLEAM_HOME/bin/gleam --version

#------------------------------------------------------------------------
# Askalono
FROM rust-build AS askalono-build

ARG ASKALONO_VERSION

ENV PATH=$PATH:$CARGO_HOME/bin

RUN mkdir -p /opt/askalono && \
    if [ "$(arch)" = "aarch64" ]; then \
        cargo install --git https://github.com/jpeddicord/askalono.git --tag $ASKALONO_VERSION --root /opt/askalono; \
    else \
        curl -LOs https://github.com/jpeddicord/askalono/releases/download/$ASKALONO_VERSION/askalono-Linux.zip && \
        unzip askalono-Linux.zip -d /opt/askalono/bin && \
        rm askalono-Linux.zip; \
    fi

#------------------------------------------------------------------------
# cargo-credential-netrc
FROM rust-build AS cargo-credential-netrc-build

ENV PATH=$PATH:$CARGO_HOME/bin

RUN cargo install cargo-credential-netrc --locked --root /opt/cargo-credential-netrc

#------------------------------------------------------------------------
# Provenant
FROM rust-build AS provenant-build

ARG PROVENANT_VERSION

ENV PATH=$PATH:$CARGO_HOME/bin

RUN mkdir -p /opt/provenant && \
    if [ "$(arch)" = "aarch64" ]; then \
        PROVENANT_ARCHIVE="provenant-linux-aarch64.tar.gz"; \
    else \
        PROVENANT_ARCHIVE="provenant-linux-x86_64.tar.gz"; \
    fi \
    && curl -LSs "https://github.com/mstykow/provenant/releases/download/v$PROVENANT_VERSION/$PROVENANT_ARCHIVE" | tar -xz -C /opt/provenant

#------------------------------------------------------------------------
# Container with minimal selection of supported package managers.
FROM base AS minimal-tools

ARG NODEJS_VERSION

# Remove ort build scripts
RUN sudo rm -rf /etc/scripts

# Install optional tool subversion for ORT analyzer
RUN --mount=type=cache,target=/var/cache,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    --mount=type=tmpfs,target=/var/log \
    sudo apt-get update && \
    DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    subversion

# Python
ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:$PYENV_ROOT/shims:$PYENV_ROOT/bin:$PYENV_ROOT/conan2/bin
COPY --from=python-build --chown=$USER:$USER $PYENV_ROOT $PYENV_ROOT

# NodeJS
ENV NVM_DIR=/opt/nvm
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin
COPY --from=nodejs-build --chown=$USER:$USER $NVM_DIR $NVM_DIR

# Rust
ENV RUST_HOME=/opt/rust
ENV CARGO_HOME=$RUST_HOME/cargo
ENV RUSTUP_HOME=$RUST_HOME/rustup
ENV PATH=$PATH:$CARGO_HOME/bin:$RUSTUP_HOME/bin
COPY --from=rust-build --chown=$USER:$USER $RUST_HOME $RUST_HOME
RUN chmod o+rwx $CARGO_HOME

# cargo-credential-netrc
ENV CARGO_CREDENTIAL_NETRC_HOME=/opt/cargo-credential-netrc
ENV PATH=$PATH:$CARGO_CREDENTIAL_NETRC_HOME/bin
COPY --from=cargo-credential-netrc-build $CARGO_CREDENTIAL_NETRC_HOME $CARGO_CREDENTIAL_NETRC_HOME

# Golang
ENV PATH=$PATH:/opt/go/bin
COPY --from=go-build --chown=$USER:$USER /opt/go /opt/go

# Ruby
ENV RUBY_ROOT=/opt/ruby
ENV GEM_HOME=/var/tmp/gem
ENV PATH=$PATH:$RUBY_ROOT/bin
COPY --from=ruby-build --chown=$USER:$USER $RUBY_ROOT $RUBY_ROOT

COPY --from=python-build --chown=$USER:$USER /opt/scancode-license-data /opt/scancode-license-data

#------------------------------------------------------------------------
# Container with all supported package managers.
FROM minimal-tools AS all-tools

ARG ABOM_VERSION
ARG ASKALONO_VERSION
ARG COMPOSER_VERSION
ARG PHP_VERSION

ENV ABOM_HOME=/opt/abom
ENV PATH=$PATH:$ABOM_HOME
RUN sudo mise install-into github:JulietSecurity/abom@$ABOM_VERSION $ABOM_HOME

# Repo and Android
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV ANDROID_USER_HOME=$HOME/.android
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/cmdline-tools/bin
ENV PATH=$PATH:$ANDROID_HOME/platform-tools
COPY --from=android-build --chown=$USER:$USER $ANDROID_HOME $ANDROID_HOME
RUN chmod o+rw $ANDROID_HOME

# Swift
ENV SWIFT_HOME=/opt/swift
ENV PATH=$PATH:$SWIFT_HOME/bin
COPY --from=swift-build --chown=$USER:$USER $SWIFT_HOME $SWIFT_HOME
RUN --mount=type=cache,target=/var/cache,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    --mount=type=tmpfs,target=/var/log \
    sudo apt-get update \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends libncurses6

# Scala
ENV SBT_HOME=/opt/sbt
ENV PATH=$PATH:$SBT_HOME/bin
COPY --from=scala-build --chown=$USER:$USER $SBT_HOME $SBT_HOME

# Dart
ENV DART_SDK=/opt/dart-sdk
ENV PATH=$PATH:$DART_SDK/bin
COPY --from=dart-build --chown=$USER:$USER $DART_SDK $DART_SDK

# Dotnet
ENV DOTNET_HOME=/opt/dotnet
ENV NUGET_INSPECTOR_HOME=$DOTNET_HOME
ENV PATH=$PATH:$DOTNET_HOME:$DOTNET_HOME/tools:$DOTNET_HOME/bin

COPY --from=dotnet-build --chown=$USER:$USER $DOTNET_HOME $DOTNET_HOME

# PHP
RUN --mount=type=cache,target=/var/cache,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    --mount=type=tmpfs,target=/var/log \
    curl -fsSL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x14aa40ec0831756756d7f66c4f4ea0aae5267a6c" | sudo gpg --dearmor -o /usr/share/keyrings/ondrej-php-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/ondrej-php-keyring.gpg] https://ppa.launchpadcontent.net/ondrej/php/ubuntu jammy main" | sudo tee /etc/apt/sources.list.d/ondrej-php.list \
    && sudo apt-get update \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends php${PHP_VERSION}-cli

RUN mkdir -p /opt/php/bin \
    && curl -ksS https://getcomposer.org/installer | php -- --install-dir=/opt/php/bin --filename=composer --$COMPOSER_VERSION

ENV PATH=$PATH:/opt/php/bin

# Haskell
ENV HASKELL_HOME=/opt/haskell
ENV PATH=$PATH:$HASKELL_HOME/bin

COPY --from=haskell-build /opt/haskell /opt/haskell

# Bazel
ENV BAZEL_HOME=/opt/bazel
ENV PATH=$PATH:$BAZEL_HOME/bin

COPY --from=bazel-build $BAZEL_HOME $BAZEL_HOME
COPY --from=bazel-build --chown=$USER:$USER /opt/go/bin/buildozer /opt/go/bin/buildozer

# Askalono
COPY --from=askalono-build --chown=$USER:$USER /opt/askalono /opt/askalono
ENV PATH=$PATH:/opt/askalono/bin

# Gleam
ENV GLEAM_HOME=/opt/gleam
ENV PATH=$PATH:$GLEAM_HOME/bin
COPY --from=gleam-build --chown=$USER:$USER $GLEAM_HOME $GLEAM_HOME

# Elixir (Mix SBoM)
ENV MIX_SBOM_HOME=/opt/mix_sbom
ENV PATH=$PATH:$MIX_SBOM_HOME/bin
COPY --from=mix-sbom-build --chown=$USER:$USER $MIX_SBOM_HOME $MIX_SBOM_HOME

# Erlang (Rebar3 SBoM wrapped in Bombom)
ENV BOMBOM_HOME=/opt/bombom
ENV PATH=$PATH:$BOMBOM_HOME/bin
COPY --from=rebar3-sbom-build --chown=$USER:$USER $BOMBOM_HOME $BOMBOM_HOME

# Provenant
COPY --from=provenant-build --chown=$USER:$USER /opt/provenant /opt/provenant
ENV PATH=$PATH:/opt/provenant

#------------------------------------------------------------------------
# Runtime container with minimal selection of supported package managers pre-installed.
FROM minimal-tools AS minimal

# ORT
COPY --from=ort-build --chown=$USER:$USER /opt/ort /opt/ort
ENV PATH=$PATH:/opt/ort/bin

USER $USER
WORKDIR $HOME

# Ensure that these directories exist in the container to be able to mount directories from the host into them with correct permissions.
RUN mkdir -p "$HOME/.ort" "$HOME/.gradle"

ENTRYPOINT ["/opt/ort/bin/ort"]

#------------------------------------------------------------------------
# Runtime container with all supported package managers pre-installed.
FROM all-tools AS run

ARG HOMEDIR=/home/ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID

# ORT
COPY --from=ort-build --chown=$USER:$USER /opt/ort /opt/ort
ENV PATH=$PATH:/opt/ort/bin

USER $USER
WORKDIR $HOME

# Ensure that these directories exist in the container to be able to mount directories from the host into them with correct permissions.
RUN mkdir -p "$HOME/.ort" "$HOME/.gradle"

# Verify that all tools required by ORT are available.
# Mount /tmp and $HOMEDIR as cache to prevent temporary files from being persisted to the image.
RUN --mount=type=tmpfs,target=/tmp \
    --mount=type=cache,target=$HOMEDIR,uid=$USER_ID,gid=$USER_GID \
    ort requirements

ENTRYPOINT ["/opt/ort/bin/ort"]
