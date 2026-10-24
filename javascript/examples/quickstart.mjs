// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

// Lists published jobs and the newest leads.
//
//   WIZARDAPP_API_KEY=wa_... WIZARDAPP_WORKSPACE=acme node examples/quickstart.mjs

import WizardApplication, { WizardApplicationError } from '../lib/index.mjs';

const client = new WizardApplication();

try {
  const jobs = await client.jobs.list({ status: 'published', perPage: 10 });
  console.log(`${jobs.total} published jobs`);
  for (const job of jobs) console.log(`- ${job.title} (${job.location || 'no location'})`);

  const leads = await client.leads.list({ sort: 'created_at', order: 'desc', perPage: 5 });
  console.log('Newest leads:');
  for (const lead of leads) console.log(`- ${lead.first_name || ''} ${lead.last_name || ''} <${lead.email || ''}>`);
} catch (e) {
  if (!(e instanceof WizardApplicationError)) throw e;
  console.error(`Request failed: ${e.message}`);
}

console.log(client.lastRateLimit);
