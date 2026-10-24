# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

"""Lists published jobs and the newest leads.

    export WIZARDAPP_API_KEY=wa_...
    export WIZARDAPP_WORKSPACE=acme
    python examples/quickstart.py
"""

from wizardapplication import WizardApplication, WizardApplicationError


def main() -> None:
    client = WizardApplication()

    try:
        jobs = client.jobs.list(status="published", per_page=10)
        print(f"{jobs.total} published jobs")
        for job in jobs:
            print(f"- {job['title']} ({job.get('location') or 'no location'})")

        leads = client.leads.list(sort="created_at", order="desc", per_page=5)
        print("Newest leads:")
        for lead in leads:
            print(f"- {lead.get('first_name', '')} {lead.get('last_name', '')} <{lead.get('email', '')}>")
    except WizardApplicationError as e:
        print(f"Request failed: {e}")

    print(client.last_rate_limit)


if __name__ == "__main__":
    main()
