#!/usr/bin/python

import ast
import os
import logging
from argparse import ArgumentParser

parser = ArgumentParser()
parser.add_argument("-d", "--dir", dest="directory",
                    help="Directory to python project", metavar="DIR")

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
                    return False

    logging.debug("Project " + path + " compatible with Python 3.")
    return True


if __name__ == '__main__':
    dir_path = args.directory
    logging.debug("Scanning project" + dir_path + " for resolving python version.")
    print(project_compatibility(dir_path), end="")


