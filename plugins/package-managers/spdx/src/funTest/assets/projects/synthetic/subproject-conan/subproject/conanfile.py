import os
import shutil

from conans import ConanFile, tools

class PocoConan(ConanFile):
    name = "ConanProject"
    version = "1.0.0"
    url = "http://github.com/ossreviewtoolkit/ort"
    author = "The ORT Project Authors"
    license = "The Boost Software License 1.0"
    description = "Modern, powerful open source C++ class libraries for building network- and internet-based " \
                  "applications that run on desktop, server, mobile and embedded systems."

    def requirements(self):
        self.requires.add("openssl/3.0.0", private=False)
