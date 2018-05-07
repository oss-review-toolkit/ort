#!/usr/bin/ruby

# This script produces a list of top-level dependencies with group information. No bundle
# command except for 'bundle viz' seem to produce a dependency list with corresponding groups.
# But parsing the dot / svg output of 'bundle viz' seemes to be overhead.

require 'bundler'
require 'json'

groups = {}

Bundler.load.current_dependencies.each do |dep|
    dep.groups.each do |group|
        (groups[group] ||= []) << dep.name
    end
end

puts JSON.generate(groups)
