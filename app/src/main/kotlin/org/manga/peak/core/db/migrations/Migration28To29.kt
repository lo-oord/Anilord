package org.manga.peak.core.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration28To29 : Migration(28, 29) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE favourite_categories ADD COLUMN is_public INTEGER NOT NULL DEFAULT 1")
	}
}
