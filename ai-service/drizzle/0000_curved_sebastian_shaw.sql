CREATE TABLE `coach_cache` (
	`owner` text PRIMARY KEY NOT NULL,
	`payload` text NOT NULL,
	`fingerprint` text NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `coach_quota` (
	`id` text PRIMARY KEY NOT NULL,
	`count` integer NOT NULL,
	`next_at` integer NOT NULL
);
