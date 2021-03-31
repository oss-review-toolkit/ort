#!/usr/bin/python3

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

# This helper script tries to detect the version of Python required by the project files located in a specific directory
# based on the solution proposed at https://stackoverflow.com/a/40886697/5877109. The script must be run using Python 3.

import ast
import os
import logging
from argparse import ArgumentParser

parser = ArgumentParser()
parser.add_argument("-d", "--directory", dest="directory", help="A Python project directory.", metavar="DIR")

args = parser.parse_args()


def compatible_python3(file_path):
    try:
        return ast.parse(file_path)
    except SyntaxError as e:
        return False


def project_compatibility(path):
    for root, dirs, files in os.walk(path):
        for file in files:
            if file.endswith(".py"):
                file_path = os.path.join(root, file)
                file_content = open(file_path, encoding="UTF-8").read()
                if not compatible_python3(file_content):
                    logging.debug("At least one file is incompatible with Python 3: " + file_path)
                    logging.debug("Assuming the project in '" + path + "' to be Python 2.")
                    return 2

    logging.debug("The project in '" + path + "' seems to be compatible with Python 3.")
    return 3


if __name__ == "__main__":
    dir_path = args.directory
    logging.debug("Trying to determine the required Python version for the project in '" + dir_path + "'.")
    print(project_compatibility(dir_path), end="")
