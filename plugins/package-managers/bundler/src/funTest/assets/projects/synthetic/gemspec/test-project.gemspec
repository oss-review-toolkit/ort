# encoding: utf-8

Gem::Specification.new do |gem|
  gem.name        = 'test-project'
  gem.version     = '1.0.0'
  gem.files       = ['Gemfile']
  gem.authors     = ['Bob Example']
  gem.summary     = 'Test project with gemspec'

  gem.add_runtime_dependency 'rack', '>=2.0.8', '<2.3.0'
  gem.add_runtime_dependency 'signet', '~>0.8.1'
  gem.add_development_dependency 'rspec'
end
