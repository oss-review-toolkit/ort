#!/usr/bin/env ruby

# Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

# This script makes Bundler resolve dependencies for the platform given by the "ORT_BUNDLER_PLATFORM" environment
# variable (like "ruby", "java" or "x86_64-linux") instead of for the platform of the current Ruby interpreter.
# It needs to be required before the Bundler definition is created.
#
# As Bundler itself compares the local platform to the platforms in the lockfile, the locked dependencies are kept as-is
# if the platform is present in the lockfile. This also applies to platforms that Bundler considers to be covered by the
# locked "ruby" platform, like "x86_64-linux", in which case only the platform-specific variants of locked Gems are used.
# Otherwise, the platform is added and dependencies get re-resolved, unless the bundle is frozen, in which case Bundler
# fails.

require 'bundler'

module OrtBundlerPlatform
  # Note that for "ruby" or an empty value this is the plain string constant `Gem::Platform::RUBY`.
  PLATFORM = Gem::Platform.new(ENV['ORT_BUNDLER_PLATFORM'])

  # Override the platform used for resolving, materializing, and filtering dependencies.
  module LocalPlatform
    def local_platform
      PLATFORM
    end
  end

  # Override the interpreter checks that are used to evaluate `platforms` restrictions of dependencies in a Gemfile,
  # like `gem 'foo', platforms: :mri`. Derive them from the generic platform like Bundler's own filtering does, so both
  # agree. The Ruby version checks are kept as-is.
  module CurrentRuby
    def generic_local_platform
      if Bundler.respond_to?(:generic_local_platform)
        Bundler.generic_local_platform
      else
        Bundler::GemHelpers.generic_local_platform
      end
    end

    def jruby?
      generic_local_platform == Gem::Platform::JAVA
    end

    def mri?
      generic_local_platform == Gem::Platform::RUBY
    end

    # Do not use `Gem::Platform::WINDOWS` as that constant is only defined by some Bundler versions.
    def windows?
      platform = generic_local_platform
      platform.is_a?(Gem::Platform) && platform.os.match?(/\A(mswin|mingw)/)
    end

    # The original aliases still refer to the original method, so redefine them.
    alias_method :mswin?, :windows?
    alias_method :mswin64?, :windows?
    alias_method :mingw?, :windows?
    alias_method :x64_mingw?, :windows?
  end
end

Bundler.singleton_class.prepend(OrtBundlerPlatform::LocalPlatform)
Bundler::CurrentRuby.prepend(OrtBundlerPlatform::CurrentRuby)
