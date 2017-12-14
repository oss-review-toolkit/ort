FROM openjdk:8

# Define additional package sources.
RUN ["/bin/bash", "-c", "set -o pipefail \
 && curl -sL https://deb.nodesource.com/setup_8.x | bash - \
 && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - \
 && echo \"deb https://dl.yarnpkg.com/debian/ stable main\" | tee /etc/apt/sources.list.d/yarn.list \
"]

# Install required packages.
RUN apt update && apt install -y --no-install-recommends \
    # Platform tools
    apt-utils \
    build-essential \
    # Package managers
    python-pip \
    python-setuptools \
    python-wheel \
    nodejs \
    yarn \
    # Version Control Systems
    cvs \
    git \
    mercurial \
    subversion \
 # Install package manager specifics.
 && pip install virtualenv \
 # Install git-repo.
 && curl https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo \
 && chmod a+x /usr/local/bin/repo \
 # Clean up the apt cache to reduce the image size.
 && apt -y autoremove \
 && apt -y clean \
 && rm -rf /var/lib/apt/lists /var/cache/apt/archives

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
