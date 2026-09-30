package app.jourjour.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * How an installed database is brought up to the schema this build expects.
 *
 * Every migration here has been run against the schema it starts from, which is what
 * `app/schemas` is exported for: a migration that leaves a table one column short of what
 * Room expects fails on the next open, on somebody else's phone, with their trips in it.
 *
 * The statements are written out rather than generated. They have to match what Room would
 * have created down to the index names, so they are copied from the exported schema of the
 * version they lead to.
 */
val MIGRATION_1_2 = Migration(1, 2) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `budget_items` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`trip_id` INTEGER NOT NULL, " +
            "`category_id` INTEGER NOT NULL, " +
            "`title` TEXT NOT NULL, " +
            "`notes` TEXT NOT NULL, " +
            "`quantity_milli` INTEGER, " +
            "`amount_milli` INTEGER NOT NULL, " +
            "`currency_code` TEXT NOT NULL, " +
            "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
            "FOREIGN KEY(`currency_code`) REFERENCES `currencies`(`code`) " +
            "ON UPDATE CASCADE ON DELETE RESTRICT )"
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_items_trip_id` ON `budget_items` (`trip_id`)")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_budget_items_category_id` ON `budget_items` (`category_id`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_budget_items_currency_code` " +
            "ON `budget_items` (`currency_code`)"
    )

    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `trip_budget_categories` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`trip_id` INTEGER NOT NULL, " +
            "`category_id` INTEGER NOT NULL, " +
            "`is_spending_closed` INTEGER NOT NULL, " +
            "`is_planning_closed` INTEGER NOT NULL, " +
            "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT )"
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_trip_budget_categories_trip_id_category_id` " +
            "ON `trip_budget_categories` (`trip_id`, `category_id`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_trip_budget_categories_category_id` " +
            "ON `trip_budget_categories` (`category_id`)"
    )
}

val MIGRATION_2_3 = Migration(2, 3) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `schedule_events` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`trip_id` INTEGER NOT NULL, " +
            "`title` TEXT NOT NULL, " +
            "`notes` TEXT NOT NULL, " +
            "`starts_at` INTEGER NOT NULL, " +
            "`duration_minutes` INTEGER NOT NULL, " +
            "`starts_in_zone` TEXT, " +
            "`ends_in_zone` TEXT, " +
            "`follows_id` INTEGER, " +
            "`category_id` INTEGER, " +
            "`amount_milli` INTEGER, " +
            "`currency_code` TEXT, " +
            "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`follows_id`) REFERENCES `schedule_events`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE SET NULL , " +
            "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
            "FOREIGN KEY(`currency_code`) REFERENCES `currencies`(`code`) " +
            "ON UPDATE CASCADE ON DELETE RESTRICT )"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_schedule_events_trip_id_starts_at` " +
            "ON `schedule_events` (`trip_id`, `starts_at`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_schedule_events_follows_id` " +
            "ON `schedule_events` (`follows_id`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_schedule_events_category_id` " +
            "ON `schedule_events` (`category_id`)"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_schedule_events_currency_code` " +
            "ON `schedule_events` (`currency_code`)"
    )
}

/** What each day of a trip is about, which belongs to the date and to no event on it. */
val MIGRATION_3_4 = Migration(3, 4) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `schedule_days` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`trip_id` INTEGER NOT NULL, " +
            "`date` INTEGER NOT NULL, " +
            "`summary` TEXT NOT NULL, " +
            "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_schedule_days_trip_id_date` " +
            "ON `schedule_days` (`trip_id`, `date`)"
    )
}

/** Every migration this build knows, oldest first. */
val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
