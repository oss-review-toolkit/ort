#!/usr/bin/python

# This helper script prints out the path to the executable of the Python interpreter running this script, see
# https://pyinstaller.readthedocs.io/en/latest/runtime-information.html#using-sys-executable-and-sys-argv-0

from __future__ import print_function
import sys

if __name__ == "__main__":
    print(sys.executable, end='')
