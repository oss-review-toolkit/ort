#!/usr/bin/env ruby

# Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
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

# Optionally determine dependencies for a custom platform like "java" or "x86_64-linux" instead of for the platform of
# the Ruby interpreter. This needs to match the logic in "resolve_dependencies.rb".
custom_platform = ENV['ORT_BUNDLER_PLATFORM']
unless custom_platform.to_s.empty?
    platform = Gem::Platform.new(custom_platform)

    # Gemfile platforms like ":mri" or ":jruby" only map to generic platforms like "ruby" or "java".
    target_platforms = [platform, Bundler::GemHelpers.generic(platform)].uniq

    Bundler.definition.dependencies.each { |dep|
        dep.define_singleton_method(:current_platform?) {
            platforms.empty? || gem_platforms(target_platforms).any?
        }
    }
end

Bundler.load.current_dependencies.each { |dep|
    dep.groups.each { |group|
        (groups[group.to_s] ||= []) << dep.name
    }
}

groups
