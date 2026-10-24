# frozen_string_literal: true

# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

# Lists published jobs and the newest leads.
#
#   WIZARDAPP_API_KEY=wa_... WIZARDAPP_WORKSPACE=acme ruby -Ilib examples/quickstart.rb

require "wizardapplication"

client = WizardApplication::Client.new

begin
  jobs = client.jobs.list(status: "published", per_page: 10)
  puts "#{jobs.total} published jobs"
  jobs.each { |job| puts "- #{job['title']} (#{job['location'] || 'no location'})" }

  leads = client.leads.list(sort: "created_at", order: "desc", per_page: 5)
  puts "Newest leads:"
  leads.each { |lead| puts "- #{lead['first_name']} #{lead['last_name']} <#{lead['email']}>" }
rescue WizardApplication::Error => e
  puts "Request failed: #{e.message}"
end

p client.last_rate_limit
