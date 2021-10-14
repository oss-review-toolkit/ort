#!/usr/bin/ruby

# Copyright (C) 2017-2019 HERE Europe B.V.
# Copyright (C) 2021 Bosch.IO GmbH
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

# This script produces a list of top-level dependencies with group information. No bundle
# command except for 'bundle viz' seem to produce a dependency list with corresponding groups.
# But parsing the dot / svg output of 'bundle viz' seems to be overhead.

require 'bundler'
require 'yaml'

groups = {}

Bundler.load.current_dependencies.each do |dep|
    dep.groups.each do |group|
        (groups[group.to_s] ||= []) << dep.name
    end
end

puts(YAML.dump(groups))
