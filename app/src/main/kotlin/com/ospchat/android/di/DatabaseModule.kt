package com.ospchat.android.di

import android.content.Context
import com.ospchat.shared.data.db.OspChatDatabase
import com.ospchat.shared.data.db.ospChatDatabase
import com.ospchat.shared.data.groups.GroupDao
import com.ospchat.shared.data.groups.GroupMessageDao
import com.ospchat.shared.data.messages.MessageDao
import com.ospchat.shared.data.peers.PeerDao
import com.ospchat.shared.data.peers.PeerHistoryDao
import com.ospchat.shared.data.reactions.ReactionDao
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
    ): OspChatDatabase = ospChatDatabase(context = context)

    @Provides
    @Singleton
    fun provideMessageDao(database: OspChatDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun providePeerDao(database: OspChatDatabase): PeerDao = database.peerDao()

    @Provides
    @Singleton
    fun provideReactionDao(database: OspChatDatabase): ReactionDao = database.reactionDao()

    @Provides
    @Singleton
    fun providePeerHistoryDao(database: OspChatDatabase): PeerHistoryDao = database.peerHistoryDao()

    @Provides
    @Singleton
    fun provideGroupDao(database: OspChatDatabase): GroupDao = database.groupDao()

    @Provides
    @Singleton
    fun provideGroupMessageDao(database: OspChatDatabase): GroupMessageDao = database.groupMessageDao()
}
