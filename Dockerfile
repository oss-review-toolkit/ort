FROM openjdk:8

# Environment
ENV APPDIR=/opt/oss-review-toolkit \
    SCANCODE_VERSION=2.2.1

# Install dependencies
RUN ["/bin/bash", "-c", "set -o pipefail \
  && groupadd -r toolkit \
  && useradd --no-log-init -r -g toolkit toolkit \
  && curl -sL https://github.com/nexB/scancode-toolkit/releases/download/v${SCANCODE_VERSION}/scancode-toolkit-${SCANCODE_VERSION}.tar.bz2 > /tmp/scancode.tar.bz2 \
  && tar xvjf /tmp/scancode.tar.bz2 -C /opt/ \
  && curl -sL https://deb.nodesource.com/setup_8.x | bash - \
  && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - \
  && echo \"deb https://dl.yarnpkg.com/debian/ stable main\" | tee /etc/apt/sources.list.d/yarn.list \
  && apt update \
  && apt install -y \
    apt-utils \
    build-essential \
    python-pip \
    nodejs \
    yarn \
    cvs \
    git \
    mercurial \
    subversion \
    python-dev \
    libbz2-1.0 \
    xz-utils \
    zlib1g \
    libxml2-dev \
    libxslt1-dev \
  && pip install virtualenv \
  && rm -rf /var/lib/apt/lists \
  && rm /tmp/scancode.tar.bz2 \
  && /opt/scancode-toolkit-${SCANCODE_VERSION}/scancode --version \
  && chown -R toolkit:toolkit /opt/scancode-toolkit-${SCANCODE_VERSION}/"]

# Install the OSS Review Toolkit
COPY . "${APPDIR}"
WORKDIR "${APPDIR}"
RUN ./gradlew installDist

# Add the tools to the path
ENV PATH="${APPDIR}/analyzer/build/install/analyzer/bin:${APPDIR}/graph/build/install/graph/bin:${APPDIR}/downloader/build/install/downloader/bin:${APPDIR}/scanner/build/install/scanner/bin:/opt/scancode-toolkit-${SCANCODE_VERSION}:${PATH}"

# Change to non-root
USER toolkit
