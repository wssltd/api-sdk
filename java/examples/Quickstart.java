// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

import com.wizardapplication.sdk.Page;
import com.wizardapplication.sdk.WizardApplication;
import com.wizardapplication.sdk.WizardApplicationException;
import java.util.Map;

/**
 * Lists published jobs and the newest leads.
 *
 * <pre>
 * WIZARDAPP_API_KEY=wa_... WIZARDAPP_WORKSPACE=acme java -cp target/classes examples/Quickstart.java
 * </pre>
 */
public class Quickstart {
    public static void main(String[] args) {
        WizardApplication client = WizardApplication.builder().build();

        try {
            Page jobs = client.jobs().list(Map.of("status", "published", "per_page", 10));
            System.out.println(jobs.getTotal() + " published jobs");
            for (Map<String, Object> job : jobs) {
                System.out.println("- " + job.get("title") + " (" + job.getOrDefault("location", "no location") + ")");
            }

            Page leads = client.leads().list(Map.of("sort", "created_at", "order", "desc", "per_page", 5));
            System.out.println("Newest leads:");
            for (Map<String, Object> lead : leads) {
                System.out.println("- " + lead.get("first_name") + " " + lead.get("last_name") + " <" + lead.get("email") + ">");
            }
        } catch (WizardApplicationException e) {
            System.out.println("Request failed: " + e);
        }

        WizardApplication.RateLimit rl = client.getLastRateLimit();
        if (rl != null) {
            System.out.println(rl.remaining + " of " + rl.limit + " requests left this minute");
        }
    }
}
