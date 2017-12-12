FROM openjdk:8

# Install the OSS Review Toolkit
ENV APPDIR=/opt/oss-review-toolkit
COPY . "${APPDIR}"
WORKDIR "${APPDIR}"
RUN ./gradlew installDist

# Install dependencies for NPM scanning
RUN ["/bin/bash", "-c", "set -o pipefail && curl -sL https://deb.nodesource.com/setup_8.x | bash -"]
RUN ["/bin/bash", "-c", "set -o pipefail && curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add -"]
RUN ["/bin/bash", "-c", "set -o pipefail && echo \"deb https://dl.yarnpkg.com/debian/ stable main\" | tee /etc/apt/sources.list.d/yarn.list"]
RUN apt-get update && apt-get install -y apt-utils 
RUN apt-get install -y build-essential python-pip nodejs yarn && pip install virtualenv && rm -rf /var/lib/apt/lists

# Add the tools to the path
ENV PATH="${APPDIR}/analyzer/build/install/analyzer/bin:${APPDIR}/graph/build/install/graph/bin:${APPDIR}/downloader/build/install/downloader/bin:${APPDIR}/scanner/build/install/scanner/bin:${PATH}"

# Change to non-root
RUN groupadd -r toolkit && useradd --no-log-init -r -g toolkit toolkit
USER toolkit