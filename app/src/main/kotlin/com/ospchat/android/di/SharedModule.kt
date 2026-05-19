package com.ospchat.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ospchat.android.notifications.MessageNotifier
import com.ospchat.shared.data.attachments.AndroidImageCompressor
import com.ospchat.shared.data.attachments.AttachmentStore
import com.ospchat.shared.data.attachments.FileAttachmentStore
import com.ospchat.shared.data.attachments.ImageCompressor
import com.ospchat.shared.data.avatar.AvatarStore
import com.ospchat.shared.data.avatar.FileAvatarStore
import com.ospchat.shared.data.discovery.DiscoveryRepository
import com.ospchat.shared.data.discovery.NsdPeerDiscovery
import com.ospchat.shared.data.discovery.PeerDiscoveryService
import com.ospchat.shared.data.groups.GroupDao
import com.ospchat.shared.data.groups.GroupMessageDao
import com.ospchat.shared.data.groups.GroupMessageRepository
import com.ospchat.shared.data.groups.GroupRepository
import com.ospchat.shared.data.groups.GroupSyncer
import com.ospchat.shared.data.identity.IdentityRepository
import com.ospchat.shared.data.messages.MessageDao
import com.ospchat.shared.data.messages.MessageRepository
import com.ospchat.shared.data.peers.PeerAvatarSync
import com.ospchat.shared.data.peers.PeerDao
import com.ospchat.shared.data.peers.PeerHistoryDao
import com.ospchat.shared.data.peers.PeerHistoryRecorder
import com.ospchat.shared.data.peers.PeerInfoNotifier
import com.ospchat.shared.data.peers.PeerRepository
import com.ospchat.shared.data.reactions.ReactionDao
import com.ospchat.shared.data.reactions.ReactionRepository
import com.ospchat.shared.domain.contacts.AddToContactsUseCase
import com.ospchat.shared.domain.contacts.RemoveFromContactsUseCase
import com.ospchat.shared.domain.groups.AddGroupMembersUseCase
import com.ospchat.shared.domain.groups.CreateGroupUseCase
import com.ospchat.shared.domain.groups.GroupBroadcaster
import com.ospchat.shared.domain.groups.LeaveGroupUseCase
import com.ospchat.shared.domain.groups.RemoveGroupMembersUseCase
import com.ospchat.shared.net.client.MessageClient
import com.ospchat.shared.net.server.MessageServer
import com.ospchat.shared.notifications.ActiveChatTracker
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import com.ospchat.shared.notifications.MessageNotifier as SharedMessageNotifier

/**
 * Provides every shared (`com.ospchat.shared.*`) class to Hilt. The shared
 * classes have plain constructor injection — no annotations — so each one
 * needs an explicit `@Provides`. Repository / use-case bodies are pure data;
 * the Android-specific bits ([MessageNotifier], [NsdManager], [Context]) come
 * from the existing [AppModule] / [DatabaseModule] / [NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedModule {
    // ---- Identity / DataStore ------------------------------------------------

    private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(name = "identity")

    @Provides
    @Singleton
    fun provideIdentityDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.identityDataStore

    @Provides
    @Singleton
    fun provideIdentityRepository(store: DataStore<Preferences>): IdentityRepository = IdentityRepository(store)

    // ---- Stores + image compressor ------------------------------------------

    @Provides
    @Singleton
    fun provideAttachmentStore(
        @ApplicationContext context: Context,
    ): AttachmentStore = FileAttachmentStore(parentDir = context.filesDir)

    @Provides
    @Singleton
    fun provideAvatarStore(
        @ApplicationContext context: Context,
    ): AvatarStore = FileAvatarStore(parentDir = context.filesDir)

    @Provides
    @Singleton
    fun provideImageCompressor(): ImageCompressor = AndroidImageCompressor()

    // ---- Discovery -----------------------------------------------------------

    @Provides
    @Singleton
    fun provideNsdPeerDiscovery(nsdManager: android.net.nsd.NsdManager): NsdPeerDiscovery = NsdPeerDiscovery(nsdManager = nsdManager)

    @Provides
    @Singleton
    fun providePeerDiscoveryService(impl: NsdPeerDiscovery): PeerDiscoveryService = impl

    @Provides
    @Singleton
    fun provideDiscoveryRepository(discovery: PeerDiscoveryService): DiscoveryRepository = DiscoveryRepository(discovery)

    // ---- Network client ------------------------------------------------------

    @Provides
    @Singleton
    fun provideMessageClient(
        http: HttpClient,
        discoveryRepository: DiscoveryRepository,
    ): MessageClient = MessageClient(http = http, discoveryRepository = discoveryRepository)

    // ---- Misc shared --------------------------------------------------------

    @Provides
    @Singleton
    fun provideActiveChatTracker(): ActiveChatTracker = ActiveChatTracker()

    // ---- Peer repositories ---------------------------------------------------

    @Provides
    @Singleton
    fun providePeerHistoryRecorder(dao: PeerHistoryDao): PeerHistoryRecorder = PeerHistoryRecorder(historyDao = dao)

    @Provides
    @Singleton
    fun providePeerRepository(
        peerDao: PeerDao,
        messageDao: MessageDao,
        historyDao: PeerHistoryDao,
        historyRecorder: PeerHistoryRecorder,
        discoveryRepository: DiscoveryRepository,
    ): PeerRepository =
        PeerRepository(
            peerDao = peerDao,
            messageDao = messageDao,
            historyDao = historyDao,
            historyRecorder = historyRecorder,
            discoveryRepository = discoveryRepository,
        )

    @Provides
    @Singleton
    fun providePeerAvatarSync(
        client: MessageClient,
        peerDao: PeerDao,
        avatarStore: AvatarStore,
    ): PeerAvatarSync = PeerAvatarSync(client = client, peerDao = peerDao, avatarStore = avatarStore)

    @Provides
    @Singleton
    fun providePeerInfoNotifier(
        client: MessageClient,
        discoveryRepository: DiscoveryRepository,
    ): PeerInfoNotifier = PeerInfoNotifier(client = client, discoveryRepository = discoveryRepository)

    // ---- Message + reaction repositories ------------------------------------

    @Provides
    @Singleton
    fun provideReactionRepository(
        dao: ReactionDao,
        client: MessageClient,
        identityRepository: IdentityRepository,
    ): ReactionRepository = ReactionRepository(dao = dao, client = client, identityRepository = identityRepository)

    @Provides
    @Singleton
    fun provideMessageRepository(
        messageDao: MessageDao,
        peerDao: PeerDao,
        client: MessageClient,
        identityRepository: IdentityRepository,
        notifier: SharedMessageNotifier,
        attachmentStore: AttachmentStore,
        attachmentCompressor: ImageCompressor,
    ): MessageRepository =
        MessageRepository(
            messageDao = messageDao,
            peerDao = peerDao,
            client = client,
            identityRepository = identityRepository,
            notifier = notifier,
            attachmentStore = attachmentStore,
            attachmentCompressor = attachmentCompressor,
        )

    // ---- Group repositories -------------------------------------------------

    @Provides
    @Singleton
    fun provideGroupRepository(
        groupDao: GroupDao,
        groupMessageDao: GroupMessageDao,
        peerDao: PeerDao,
        identityRepository: IdentityRepository,
    ): GroupRepository =
        GroupRepository(
            groupDao = groupDao,
            groupMessageDao = groupMessageDao,
            peerDao = peerDao,
            identityRepository = identityRepository,
        )

    @Provides
    @Singleton
    fun provideGroupMessageRepository(
        groupDao: GroupDao,
        groupMessageDao: GroupMessageDao,
        peerDao: PeerDao,
        client: MessageClient,
        identityRepository: IdentityRepository,
        discoveryRepository: DiscoveryRepository,
        groupRepository: GroupRepository,
        notifier: SharedMessageNotifier,
    ): GroupMessageRepository =
        GroupMessageRepository(
            groupDao = groupDao,
            groupMessageDao = groupMessageDao,
            peerDao = peerDao,
            client = client,
            identityRepository = identityRepository,
            discoveryRepository = discoveryRepository,
            groupRepository = groupRepository,
            notifier = notifier,
        )

    @Provides
    @Singleton
    fun provideGroupSyncer(
        groupDao: GroupDao,
        groupMessageDao: GroupMessageDao,
        groupRepository: GroupRepository,
        client: MessageClient,
        identityRepository: IdentityRepository,
    ): GroupSyncer =
        GroupSyncer(
            groupDao = groupDao,
            groupMessageDao = groupMessageDao,
            groupRepository = groupRepository,
            client = client,
            identityRepository = identityRepository,
        )

    @Provides
    @Singleton
    fun provideGroupBroadcaster(
        groupDao: GroupDao,
        groupRepository: GroupRepository,
        peerDao: PeerDao,
        discoveryRepository: DiscoveryRepository,
        client: MessageClient,
    ): GroupBroadcaster =
        GroupBroadcaster(
            groupDao = groupDao,
            groupRepository = groupRepository,
            peerDao = peerDao,
            discoveryRepository = discoveryRepository,
            client = client,
        )

    // ---- Use cases -----------------------------------------------------------

    @Provides
    @Singleton
    fun provideAddToContactsUseCase(peerRepository: PeerRepository): AddToContactsUseCase = AddToContactsUseCase(peerRepository)

    @Provides
    @Singleton
    fun provideRemoveFromContactsUseCase(peerRepository: PeerRepository): RemoveFromContactsUseCase =
        RemoveFromContactsUseCase(peerRepository)

    @Provides
    @Singleton
    fun provideCreateGroupUseCase(
        groupRepository: GroupRepository,
        groupBroadcaster: GroupBroadcaster,
    ): CreateGroupUseCase = CreateGroupUseCase(groupRepository, groupBroadcaster)

    @Provides
    @Singleton
    fun provideAddGroupMembersUseCase(
        groupRepository: GroupRepository,
        groupBroadcaster: GroupBroadcaster,
    ): AddGroupMembersUseCase = AddGroupMembersUseCase(groupRepository, groupBroadcaster)

    @Provides
    @Singleton
    fun provideRemoveGroupMembersUseCase(
        groupRepository: GroupRepository,
        groupBroadcaster: GroupBroadcaster,
    ): RemoveGroupMembersUseCase = RemoveGroupMembersUseCase(groupRepository, groupBroadcaster)

    @Provides
    @Singleton
    fun provideLeaveGroupUseCase(
        groupRepository: GroupRepository,
        groupDao: GroupDao,
        groupBroadcaster: GroupBroadcaster,
    ): LeaveGroupUseCase = LeaveGroupUseCase(groupRepository, groupDao, groupBroadcaster)

    // ---- Embedded HTTP server ------------------------------------------------

    @Provides
    @Singleton
    fun provideMessageServer(
        discoveryRepository: DiscoveryRepository,
        messageRepository: MessageRepository,
        messageDao: MessageDao,
        attachmentStore: AttachmentStore,
        identityRepository: IdentityRepository,
        reactionRepository: ReactionRepository,
        avatarStore: AvatarStore,
        peerAvatarSync: PeerAvatarSync,
        groupMessageRepository: GroupMessageRepository,
        groupRepository: GroupRepository,
        groupSyncer: GroupSyncer,
    ): MessageServer =
        MessageServer(
            discoveryRepository = discoveryRepository,
            messageRepository = messageRepository,
            messageDao = messageDao,
            attachmentStore = attachmentStore,
            identityRepository = identityRepository,
            reactionRepository = reactionRepository,
            avatarStore = avatarStore,
            peerAvatarSync = peerAvatarSync,
            groupMessageRepository = groupMessageRepository,
            groupRepository = groupRepository,
            groupSyncer = groupSyncer,
        )
}

/**
 * `@Binds` for the abstract MessageNotifier interface — binds it to the
 * concrete Android impl. Lives in a separate `abstract class` module per
 * Hilt's requirement.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotifierBindings {
    @Binds
    @Singleton
    abstract fun bindMessageNotifier(impl: MessageNotifier): SharedMessageNotifier
}
