package com.ospchat.android.data.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(name = "identity")

@Singleton
class IdentityRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val store get() = context.identityDataStore

        val nicknameFlow: Flow<String?> = store.data.map { it[NICKNAME_KEY] }

        val uuidFlow: Flow<String?> = store.data.map { it[UUID_KEY] }

        /**
         * SHA-256 hex of the currently-set custom avatar JPEG. `null` means
         * the user has no custom avatar; the UI falls back to nickname
         * initials. Peers consult this via `GET /v1/info`.
         */
        val avatarHashFlow: Flow<String?> = store.data.map { it[AVATAR_HASH_KEY] }

        suspend fun setNickname(nickname: String) {
            val trimmed = nickname.trim()
            require(trimmed.isNotEmpty()) { "Nickname must not be blank" }
            store.edit { it[NICKNAME_KEY] = trimmed }
        }

        /** Returns the stable per-install UUID, generating it on first use. */
        suspend fun ensureUuid(): String {
            store.data.first()[UUID_KEY]?.let { return it }
            val generated = UUID.randomUUID().toString()
            var winner = generated
            store.edit { prefs ->
                // Re-check under the edit transaction: if a concurrent caller
                // already wrote a UUID, keep theirs and discard ours.
                val existing = prefs[UUID_KEY]
                if (existing != null) {
                    winner = existing
                } else {
                    prefs[UUID_KEY] = generated
                }
            }
            return winner
        }

        suspend fun currentAvatarHash(): String? = store.data.first()[AVATAR_HASH_KEY]

        suspend fun setAvatarHash(hash: String?) {
            store.edit { prefs ->
                if (hash == null) {
                    prefs.remove(AVATAR_HASH_KEY)
                } else {
                    prefs[AVATAR_HASH_KEY] = hash
                }
            }
        }

        private companion object {
            val NICKNAME_KEY = stringPreferencesKey("nickname")
            val UUID_KEY = stringPreferencesKey("uuid")
            val AVATAR_HASH_KEY = stringPreferencesKey("avatar_hash")
        }
    }
