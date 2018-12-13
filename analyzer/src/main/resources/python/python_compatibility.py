#!/usr/bin/python

# Script for detect version of python based on solution: https://stackoverflow.com/a/40886697/5877109
# The script must be running at least by Python 3.

import ast
import os
import logging
from argparse import ArgumentParser

parser = ArgumentParser()
parser.add_argument("-d", "--dir", dest="directory",
                    help="Directory with Python project.", metavar="DIR")

args = parser.parse_args()


def compatible_python3(file_path):
    try:
        return ast.parse(file_path)
    except SyntaxError as exc:
        return False


def project_compatibility(path):
    for root, dirs, files in os.walk(path):
        for file in files:
            if file.endswith(".py"):
                file_path = os.path.join(root, file)
                file_content = open(file_path).read()
                if not compatible_python3(file_content):
                    logging.debug("At least one file incompatible with Python 3: " + file_path)
                    logging.debug("Project " + path + " compatible with Python 2.")
                    return 2

    logging.debug("Project " + path + " compatible with Python 3.")
    return 3


if __name__ == '__main__':
    dir_path = args.directory
    logging.debug("Scanning project" + dir_path + " for resolving python version.")
    print(project_compatibility(dir_path), end="")
