package com.ospchat.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ospchat.android.data.groups.GroupDao
import com.ospchat.android.data.groups.GroupEntity
import com.ospchat.android.data.groups.GroupMemberEntity
import com.ospchat.android.data.groups.GroupMessageDao
import com.ospchat.android.data.groups.GroupMessageEntity
import com.ospchat.android.data.messages.MessageDao
import com.ospchat.android.data.messages.MessageEntity
import com.ospchat.android.data.peers.PeerAddressEntity
import com.ospchat.android.data.peers.PeerDao
import com.ospchat.android.data.peers.PeerEntity
import com.ospchat.android.data.peers.PeerHistoryDao
import com.ospchat.android.data.peers.PeerNicknameEntity
import com.ospchat.android.data.reactions.ReactionDao
import com.ospchat.android.data.reactions.ReactionEntity

@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        ReactionEntity::class,
        PeerAddressEntity::class,
        PeerNicknameEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        GroupMessageEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class OspChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun peerDao(): PeerDao

    abstract fun reactionDao(): ReactionDao

    abstract fun peerHistoryDao(): PeerHistoryDao

    abstract fun groupDao(): GroupDao

    abstract fun groupMessageDao(): GroupMessageDao
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

/**
 * v6 → v7: adds the `reactions` table. Composite PK on
 * `(message_id, from_uuid)` enforces the one-reaction-per-user-per-message
 * rule (a fresh reaction REPLACES the previous one).
 */
internal val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reactions` (
                    `message_id` TEXT NOT NULL,
                    `from_uuid` TEXT NOT NULL,
                    `from_nickname` TEXT NOT NULL,
                    `emoji` TEXT NOT NULL,
                    `reacted_at` INTEGER NOT NULL,
                    PRIMARY KEY(`message_id`, `from_uuid`)
                )
                """.trimIndent(),
            )
        }
    }

/**
 * v7 → v8: adds the `is_contact` flag to `peers`, plus the `peer_addresses`
 * and `peer_nicknames` history tables. Pre-existing peers default to
 * `is_contact = 0` (must be promoted explicitly via the Contacts UI).
 *
 * The history tables backfill from `peers.last_host/last_port/nickname` so
 * peers that pre-date the migration still have at least one history row each.
 */
internal val MIGRATION_7_8 =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `peers` ADD COLUMN `is_contact` INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `peer_addresses` (
                    `uuid` TEXT NOT NULL,
                    `host` TEXT NOT NULL,
                    `port` INTEGER NOT NULL,
                    `first_seen_at` INTEGER NOT NULL,
                    `last_seen_at` INTEGER NOT NULL,
                    PRIMARY KEY(`uuid`, `host`, `port`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `peer_nicknames` (
                    `uuid` TEXT NOT NULL,
                    `nickname` TEXT NOT NULL,
                    `first_seen_at` INTEGER NOT NULL,
                    `last_seen_at` INTEGER NOT NULL,
                    PRIMARY KEY(`uuid`, `nickname`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `peer_addresses`
                    (`uuid`, `host`, `port`, `first_seen_at`, `last_seen_at`)
                SELECT `uuid`, `last_host`, `last_port`, `first_seen_at`, `last_seen_at`
                FROM `peers`
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `peer_nicknames`
                    (`uuid`, `nickname`, `first_seen_at`, `last_seen_at`)
                SELECT `uuid`, `nickname`, `first_seen_at`, `last_seen_at`
                FROM `peers`
                """.trimIndent(),
            )
        }
    }

/**
 * v8 → v9: adds three tables backing the Groups feature.
 *   - `groups` is the per-device copy of every group the user is a member of.
 *   - `group_members(group_id, member_uuid)` is the resolved member list,
 *     authoritative when sourced from the creator's snapshot.
 *   - `group_messages(group_id, sent_at)` mirrors `messages` but keyed by
 *     `group_id` instead of `peer_uuid`. v1 stores text only — no
 *     attachments, no reactions, no read-receipt status.
 */
internal val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `groups` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `creator_uuid` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `membership_updated_at` INTEGER NOT NULL,
                    `last_read_at` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `group_members` (
                    `group_id` TEXT NOT NULL,
                    `member_uuid` TEXT NOT NULL,
                    `member_nickname` TEXT NOT NULL,
                    `joined_at` INTEGER NOT NULL,
                    PRIMARY KEY(`group_id`, `member_uuid`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `group_messages` (
                    `id` TEXT NOT NULL,
                    `group_id` TEXT NOT NULL,
                    `from_uuid` TEXT NOT NULL,
                    `from_nickname` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `sent_at` INTEGER NOT NULL,
                    `direction` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_group_messages_group_id_sent_at` " +
                    "ON `group_messages` (`group_id`, `sent_at`)",
            )
        }
    }
