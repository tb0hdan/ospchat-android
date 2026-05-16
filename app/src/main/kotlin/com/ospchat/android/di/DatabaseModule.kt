package com.ospchat.android.di

import android.content.Context
import androidx.room.Room
import com.ospchat.android.data.db.MIGRATION_1_2
import com.ospchat.android.data.db.MIGRATION_2_3
import com.ospchat.android.data.db.MIGRATION_3_4
import com.ospchat.android.data.db.MIGRATION_4_5
import com.ospchat.android.data.db.MIGRATION_5_6
import com.ospchat.android.data.db.OspChatDatabase
import com.ospchat.android.data.messages.MessageDao
import com.ospchat.android.data.peers.PeerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideOspChatDatabase(
        @ApplicationContext context: Context,
    ): OspChatDatabase =
        Room
            .databaseBuilder(context, OspChatDatabase::class.java, "ospchat.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            ).build()

    @Provides
    @Singleton
    fun provideMessageDao(database: OspChatDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun providePeerDao(database: OspChatDatabase): PeerDao = database.peerDao()
}
