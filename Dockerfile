FROM openjdk:8

# Define additional package sources.
RUN ["/bin/bash", "-c", "set -o pipefail \
 && curl -sL https://deb.nodesource.com/setup_8.x | bash - \
 && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - \
 && echo \"deb https://dl.yarnpkg.com/debian/ stable main\" | tee /etc/apt/sources.list.d/yarn.list \
 && echo \"deb https://dl.bintray.com/sbt/debian /\" | tee -a /etc/apt/sources.list.d/sbt.list \
 && apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 \
"]

# Install required packages.
RUN apt update && apt install -y --no-install-recommends \
    # Platform tools
    apt-utils \
    build-essential \
    locales \
    # Package managers
    python-pip \
    python-setuptools \
    python-wheel \
    nodejs \
    sbt \
    yarn=1.3.2-1 \
    # Version Control Systems
    cvs \
    git \
    mercurial \
    subversion \
 # Install package manager specifics.
 && npm install -g npm@5.5.1 \
 && pip install virtualenv==15.1.0 pipdeptree==0.10.1 \
 # Install git-repo.
 && curl https://storage.googleapis.com/git-repo-downloads/repo > /usr/local/bin/repo \
 && chmod a+x /usr/local/bin/repo \
 # Clean up the apt cache to reduce the image size.
 && apt -y autoremove \
 && apt -y clean \
 && rm -rf /var/lib/apt/lists /var/cache/apt/archives \
 # Generate a locale.
 && locale-gen en_US.UTF-8

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

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
