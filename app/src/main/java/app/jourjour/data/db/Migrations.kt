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
 *
 * A migration starts only from a version that has gone out in a release, because that is
 * what a phone may be sitting on. Version 1 is one; 2 and 3 never left the machines the app
 * was built on, so a release database goes from 1 straight to 4. Once 4 ships it is fixed
 * for good and the next change is a migration of its own.
 */
val MIGRATION_1_4 = Migration(1, 4) { db ->
    // The budget: a line for each thing the trip is expected to cost, and what has been
    // settled about a category of it.
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
            "`payment_method_id` INTEGER, " +
            "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
            "FOREIGN KEY(`currency_code`) REFERENCES `currencies`(`code`) " +
            "ON UPDATE CASCADE ON DELETE RESTRICT , " +
            "FOREIGN KEY(`payment_method_id`) REFERENCES `payment_methods`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT )"
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
        "CREATE INDEX IF NOT EXISTS `index_budget_items_payment_method_id` " +
            "ON `budget_items` (`payment_method_id`)"
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

    // The schedule: what happens at a known time, and what each day of the trip is about.
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
            "`payment_method_id` INTEGER, " +
            "FOREIGN KEY(`trip_id`) REFERENCES `trips`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`follows_id`) REFERENCES `schedule_events`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE SET NULL , " +
            "FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
            "FOREIGN KEY(`currency_code`) REFERENCES `currencies`(`code`) " +
            "ON UPDATE CASCADE ON DELETE RESTRICT , " +
            "FOREIGN KEY(`payment_method_id`) REFERENCES `payment_methods`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE RESTRICT )"
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
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_schedule_events_payment_method_id` " +
            "ON `schedule_events` (`payment_method_id`)"
    )

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

    // What a rate has to say for itself. The note arrives empty on every rate that already
    // exists, which is what a rate with nothing to say looks like anyway.
    db.execSQL("ALTER TABLE `trip_rates` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
    db.execSQL(
        "ALTER TABLE `trip_rates` ADD COLUMN `note_from_lookup` INTEGER NOT NULL DEFAULT 0"
    )

    // A checklist item counts how many, says what there is to say about it, and keeps the
    // place it is dragged into. A column is added rather than the table rebuilt, so the
    // items keep their ids. Every item there already is arrives with nothing to say and no
    // count, which is what most items have, and in the order the list already showed them.
    db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `quantity_milli` INTEGER")
    db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `sort_order` INTEGER NOT NULL DEFAULT 0")

    // Nights counted the way litres are. The category is reached by `name_key` rather than
    // by name: the name is in whatever language the app was first opened in, and a person
    // may have rewritten it. One that has been deleted is simply not there to update.
    db.execSQL(
        "UPDATE `categories` SET `track_quantity` = 1 WHERE `name_key` = 'category_accommodation'"
    )

    // Three icons drawn with fewer shapes, which is what an icon read at this size is
    // for: a bed in place of the hotel's windows, a knife and fork without the plate
    // between them, a handset in place of the smartphone's screen and buttons.
    //
    // Each only where the seed's own icon is still what the row carries: an icon somebody
    // chose for themselves is theirs, whatever this app has since changed its mind about.
    db.execSQL(
        "UPDATE `categories` SET `icon` = '🛏️' " +
            "WHERE `name_key` = 'category_accommodation' AND `icon` = '🏨'"
    )
    db.execSQL(
        "UPDATE `categories` SET `icon` = '🍴' " +
            "WHERE `name_key` = 'category_dining' AND `icon` = '🍽'"
    )
    db.execSQL(
        "UPDATE `categories` SET `icon` = '📞' " +
            "WHERE `name_key` = 'category_connectivity' AND `icon` = '📱'"
    )
    // The picker offers them too, each where the icon it replaces stands. The old ones
    // stay on offer — they are already in this table on every installed copy, and
    // somebody keeping one should not have to type it back in.
    db.execSQL(
        "INSERT OR IGNORE INTO `icon_presets` (`target`, `emoji`, `sort_order`) " +
            "VALUES ('CATEGORY', '🛏️', 2), ('CATEGORY', '🍴', 0), ('CATEGORY', '📞', 6)"
    )
}

/** Every migration this build knows, oldest first. */
val MIGRATIONS = arrayOf(MIGRATION_1_4)
