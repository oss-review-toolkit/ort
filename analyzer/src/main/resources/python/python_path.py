#!/usr/bin/python

from __future__ import print_function
import sys

# Short script to find out the path to a specific version of Python. See:
# https://pyinstaller.readthedocs.io/en/v3.3.1/runtime-information.html#using-sys-executable-and-sys-argv-0
# Example: https://stackoverflow.com/a/647600/5877109
if __name__ == '__main__':
    print(sys.executable, end='')
