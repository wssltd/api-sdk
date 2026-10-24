# frozen_string_literal: true

Gem::Specification.new do |spec|
  spec.name = "wizardapplication"
  spec.version = "1.0.0"
  spec.authors = ["Wizard Software Solutions Ltd"]
  spec.summary = "Official Ruby SDK for the Wizard Application API"
  spec.description = "Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved."
  spec.homepage = "https://wizardapplication.com/development/api-docs"
  spec.license = "Nonstandard"
  spec.required_ruby_version = ">= 3.0"
  spec.files = Dir["lib/**/*.rb"] + ["README.md", "LICENSE"]
  spec.require_paths = ["lib"]
  spec.metadata["rubygems_mfa_required"] = "true"
end
