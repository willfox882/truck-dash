package com.truckdash.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: imperial -> metric. Tables are rebuilt (RENAME COLUMN needs SQLite 3.25, newer than minSdk 26 guarantees)
 * and values converted: miles x 1.609344, gallons (US) x 3.785411784, $/gal / 3.785411784.
 * Trips/points/events were already SI (metres, m/s) and are untouched.
 * The statements are kept in a plain list so they can be exercised against a real SQLite in tooling.
 */
val MIGRATION_1_2_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `fuel_logs_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateMs` INTEGER NOT NULL, `odometerKm` REAL NOT NULL, `litres` REAL NOT NULL, `pricePerLitre` REAL NOT NULL, `fullTank` INTEGER NOT NULL)",
    "INSERT INTO fuel_logs_new (id, dateMs, odometerKm, litres, pricePerLitre, fullTank) SELECT id, dateMs, odometerMi * 1.609344, gallons * 3.785411784, pricePerGal / 3.785411784, fullTank FROM fuel_logs",
    "DROP TABLE fuel_logs",
    "ALTER TABLE fuel_logs_new RENAME TO fuel_logs",
    "CREATE TABLE IF NOT EXISTS `maintenance_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `intervalKm` INTEGER, `intervalDays` INTEGER, `lastOdoKm` REAL NOT NULL, `lastDateMs` INTEGER NOT NULL)",
    "INSERT INTO maintenance_items_new (id, name, intervalKm, intervalDays, lastOdoKm, lastDateMs) SELECT id, name, CAST(ROUND(intervalMiles * 1.609344) AS INTEGER), intervalDays, lastOdoMi * 1.609344, lastDateMs FROM maintenance_items",
    "DROP TABLE maintenance_items",
    "ALTER TABLE maintenance_items_new RENAME TO maintenance_items",
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
    }
}
