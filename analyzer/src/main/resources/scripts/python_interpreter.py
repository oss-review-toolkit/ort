#!/usr/bin/python

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

# This helper script prints out the path to the executable of the Python interpreter running this script, see
# https://pyinstaller.readthedocs.io/en/latest/runtime-information.html#using-sys-executable-and-sys-argv-0

from __future__ import print_function
import sys

if __name__ == "__main__":
    print(sys.executable, end="")
