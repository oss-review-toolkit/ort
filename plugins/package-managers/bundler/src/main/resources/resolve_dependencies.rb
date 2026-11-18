#!/usr/bin/env ruby

# Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

# This script mimics the behavior of calling the `bundle lock` CLI command, which resolves a `Gemfile`'s dependencies
# and writes them along with the respective versions to a lockfile [1]. Internally, Bundler tries to find the
# dependencies' `gemspec` files both locally and remotely, and retrieves the respective metadata. However, except for
# the name, version, and transitive dependencies, the Bundler call discards any other metadata. To maintain all
# metadata, this script basically follows the same steps, but then serializes all metadata as YAML for further
# processing by other tools.
#
# [1] https://github.com/rubygems/bundler/blob/35be6d9a603084f719fec4f4028c18860def07f6/lib/bundler/cli/lock.rb#L49-L58

require 'bundler'
require 'yaml'

Bundler.ui.silence do
  # Get dependencies independently of the Ruby interpreter.
  all_groups = Bundler.settings.temporary(force_ruby_platform: true) do
    Bundler.definition.dependencies.flat_map(&:groups)
  end

  # Resolve all groups including optional ones.
  Bundler.settings.temporary(with: all_groups) do
    definition = Bundler.definition

    # Optionally resolve dependencies for a custom platform like "java" or "x86_64-linux" instead of for the platforms
    # of the Ruby interpreter or from the lockfile. This needs to match the logic in "root_dependencies.rb".
    custom_platform = ENV['ORT_BUNDLER_PLATFORM']
    unless custom_platform.to_s.empty?
      platform = Gem::Platform.new(custom_platform)

      # Replace the platforms from the lockfile. Also add the generic platform as Gemfile platforms like ":mri" or
      # ":jruby" only map to generic platforms like "ruby" or "java". Adding a platform not in the lockfile triggers a
      # re-resolution which keeps the locked versions where possible.
      definition.platforms.clear
      [platform, Bundler::GemHelpers.generic(platform)].uniq.each { |p| definition.add_platform(p) }

      # By default, Bundler only materializes Gemfile dependencies whose platforms match the Ruby interpreter (JRuby),
      # so make the dependencies match the custom platform instead.
      definition.dependencies.each do |dep|
        dep.define_singleton_method(:current_platform?) do
          platforms.empty? || gem_platforms(definition.platforms).any?
        end
      end
    end

    # This command resolves dependencies that are specified in the Gemfile of the current working directory.
    definition.resolve_remotely!

    # Resolving is triggered lazily, so the below "to_yaml" might create progress output.
    definition.specs.map(&:to_yaml).join("\0")
  end
end
