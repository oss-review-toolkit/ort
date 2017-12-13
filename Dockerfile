FROM openjdk:8

# Install dependencies for NPM scanning
RUN ["/bin/bash", "-c", "set -o pipefail && curl -sL https://deb.nodesource.com/setup_8.x | bash - && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - && echo \"deb https://dl.yarnpkg.com/debian/ stable main\" | tee /etc/apt/sources.list.d/yarn.list"]
RUN apt update \
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
  && pip install virtualenv \
  && rm -rf /var/lib/apt/lists

# Install the OSS Review Toolkit
ENV APPDIR=/opt/oss-review-toolkit
COPY . "${APPDIR}"
WORKDIR "${APPDIR}"
RUN ./gradlew installDist

# Add the tools to the path
ENV PATH="${APPDIR}/analyzer/build/install/analyzer/bin:${APPDIR}/graph/build/install/graph/bin:${APPDIR}/downloader/build/install/downloader/bin:${APPDIR}/scanner/build/install/scanner/bin:${PATH}"

# Change to non-root
RUN groupadd -r toolkit && useradd --no-log-init -r -g toolkit toolkit
USER toolkit
