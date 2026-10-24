# frozen_string_literal: true

# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

# Official Ruby SDK for the Wizard Application API.
module WizardApplication
  VERSION = "1.0.0"
end

require "openssl"
require "timeout"
require_relative "wizardapplication/errors"
require_relative "wizardapplication/resources"
require_relative "wizardapplication/client"
