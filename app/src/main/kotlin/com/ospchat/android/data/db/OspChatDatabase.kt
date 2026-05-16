package com.ospchat.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ospchat.android.data.messages.MessageDao
import com.ospchat.android.data.messages.MessageEntity
import com.ospchat.android.data.peers.PeerDao
import com.ospchat.android.data.peers.PeerEntity

@Database(
    entities = [MessageEntity::class, PeerEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class OspChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun peerDao(): PeerDao
}

/**
 * v1 (messages only) → v2 (adds the `peers` table). No data loss; the
 * messages table is unchanged.
 */
internal val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `peers` (
                    `uuid` TEXT NOT NULL,
                    `nickname` TEXT NOT NULL,
                    `last_host` TEXT NOT NULL,
                    `last_port` INTEGER NOT NULL,
                    `first_seen_at` INTEGER NOT NULL,
                    `last_seen_at` INTEGER NOT NULL,
                    PRIMARY KEY(`uuid`)
                )
                """.trimIndent(),
            )
        }
    }

/**
 * v2 → v3: adds the `status` column to `messages`. Pre-existing rows have no
 * known status; we default them to `DELIVERED` (true for inbound, optimistic
 * for outbound).
 */
internal val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `messages` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'DELIVERED'",
            )
        }
    }

/**
 * v3 → v4: adds the `last_read_at` column to `peers`. Existing rows default
 * to `0` (epoch) — every previously-stored inbound message is treated as
 * unread until the user opens the chat.
 */
internal val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `peers` ADD COLUMN `last_read_at` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

/**
 * v4 → v5: adds five nullable attachment columns to `messages`. All default
 * to NULL, so existing rows continue to render as plain text messages.
 */
internal val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachment_mime` TEXT")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachment_size_bytes` INTEGER")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachment_width` INTEGER")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachment_height` INTEGER")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `attachment_local_path` TEXT")
        }
    }

/**
 * v5 → v6: adds two nullable avatar columns to `peers`. NULL on both means
 * the peer hasn't set a custom avatar; the UI falls back to nickname
 * initials in that case.
 */
internal val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `peers` ADD COLUMN `avatar_hash` TEXT")
            db.execSQL("ALTER TABLE `peers` ADD COLUMN `avatar_local_path` TEXT")
        }
    }
