// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

// Command quickstart lists published jobs and the newest leads.
//
//	WIZARDAPP_API_KEY=wa_... WIZARDAPP_WORKSPACE=acme go run ./examples/quickstart
package main

import (
	"context"
	"fmt"
	"log"
	"time"

	wa "github.com/wssltd/api-sdk/go"
)

func main() {
	client, err := wa.New("")
	if err != nil {
		log.Fatal(err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Minute)
	defer cancel()

	jobs, err := client.Jobs.List(ctx, wa.Params{"status": "published", "per_page": 10})
	if err != nil {
		log.Fatalf("listing jobs: %v", err)
	}
	fmt.Printf("%d published jobs\n", jobs.Pagination.Total)
	for _, job := range jobs.Data {
		fmt.Printf("- %v (%v)\n", job["title"], job["location"])
	}

	leads, err := client.Leads.List(ctx, wa.Params{"sort": "created_at", "order": "desc", "per_page": 5})
	if err != nil {
		log.Fatalf("listing leads: %v", err)
	}
	fmt.Println("Newest leads:")
	for _, lead := range leads.Data {
		fmt.Printf("- %v %v <%v>\n", lead["first_name"], lead["last_name"], lead["email"])
	}

	if rl := client.LastRateLimit(); rl != nil {
		fmt.Printf("%d of %d requests left this minute\n", rl.Remaining, rl.Limit)
	}
}
