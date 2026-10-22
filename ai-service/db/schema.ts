import{integer,sqliteTable,text}from'drizzle-orm/sqlite-core';
export const coachCache=sqliteTable('coach_cache',{owner:text('owner').primaryKey(),payload:text('payload').notNull(),fingerprint:text('fingerprint').notNull(),createdAt:integer('created_at').notNull()});
export const coachQuota=sqliteTable('coach_quota',{id:text('id').primaryKey(),count:integer('count').notNull(),nextAt:integer('next_at').notNull()});
