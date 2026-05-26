package com.ospchat.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ospchat.android.media.AndroidAudioCallSessionFactory
import com.ospchat.android.notifications.CallNotifier
import com.ospchat.android.notifications.MessageNotifier
import com.ospchat.shared.data.attachments.AndroidImageCompressor
import com.ospchat.shared.data.attachments.AttachmentStore
import com.ospchat.shared.data.attachments.BitmapFactoryImageBounds
import com.ospchat.shared.data.attachments.FileAttachmentStore
import com.ospchat.shared.data.attachments.ImageBounds
import com.ospchat.shared.data.attachments.ImageCompressor
import com.ospchat.shared.data.avatar.AvatarStore
import com.ospchat.shared.data.avatar.FileAvatarStore
import com.ospchat.shared.data.calls.CallDao
import com.ospchat.shared.data.calls.CallRepository
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
import com.ospchat.shared.data.peers.GossipedPeerStore
import com.ospchat.shared.data.peers.PeerAvatarSync
import com.ospchat.shared.data.peers.PeerDao
import com.ospchat.shared.data.peers.PeerHistoryDao
import com.ospchat.shared.data.peers.PeerHistoryRecorder
import com.ospchat.shared.data.peers.PeerInfoNotifier
import com.ospchat.shared.data.peers.PeerRepository
import com.ospchat.shared.data.peers.PeerRouter
import com.ospchat.shared.data.peers.RelayBridgeRegistry
import com.ospchat.shared.data.reactions.ReactionDao
import com.ospchat.shared.data.reactions.ReactionRepository
import com.ospchat.shared.domain.contacts.AddToContactsUseCase
import com.ospchat.shared.domain.contacts.RemoveFromContactsUseCase
import com.ospchat.shared.domain.groups.AddGroupMembersUseCase
import com.ospchat.shared.domain.groups.CreateGroupUseCase
import com.ospchat.shared.domain.groups.GroupBroadcaster
import com.ospchat.shared.domain.groups.LeaveGroupUseCase
import com.ospchat.shared.domain.groups.RemoveGroupMembersUseCase
import com.ospchat.shared.media.AudioCallSessionFactory
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
import com.ospchat.shared.notifications.CallNotifier as SharedCallNotifier
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

    @Provides
    @Singleton
    fun provideImageBounds(): ImageBounds = BitmapFactoryImageBounds()

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
        identityRepository: IdentityRepository,
    ): MessageClient =
        MessageClient(
            http = http,
            discoveryRepository = discoveryRepository,
            // Phase 2b: outbound DTOs get signed once the keypair has been
            // loaded by DiscoveryForegroundService's ensureSigningKeyPair
            // call at startup. Pre-startup sends would be unsigned, but
            // there shouldn't be any (the UI doesn't function until the
            // service has started).
            signingKeyProvider = { identityRepository.signingKeyPairOrNull() },
        )

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
        peerRouter: PeerRouter,
        gossipedPeerStore: GossipedPeerStore,
        relayBridgeRegistry: RelayBridgeRegistry,
    ): PeerRepository =
        PeerRepository(
            peerDao = peerDao,
            messageDao = messageDao,
            historyDao = historyDao,
            historyRecorder = historyRecorder,
            discoveryRepository = discoveryRepository,
            // Phase 4: lets toRecord compute "via <bridge-nickname>" and
            // mark peers offline when the bridge route disappears.
            peerRouter = peerRouter,
            gossipedPeerStore = gossipedPeerStore,
            relayBridgeRegistry = relayBridgeRegistry,
        )

    @Provides
    @Singleton
    fun providePeerAvatarSync(
        client: MessageClient,
        peerDao: PeerDao,
        avatarStore: AvatarStore,
        avatarBounds: ImageBounds,
        gossipedPeerStore: GossipedPeerStore,
        relayBridgeRegistry: RelayBridgeRegistry,
        identityRepository: IdentityRepository,
    ): PeerAvatarSync =
        PeerAvatarSync(
            client = client,
            peerDao = peerDao,
            avatarStore = avatarStore,
            avatarBounds = avatarBounds,
            gossipedPeerStore = gossipedPeerStore,
            relayBridgeRegistry = relayBridgeRegistry,
            // Phase 4 defence: filter self.uuid out of every inbound
            // gossip list before it enters GossipedPeerStore.
            identityRepository = identityRepository,
        )

    // ---- Phase 4 multi-network bridging -------------------------------------

    @Provides @Singleton fun provideGossipedPeerStore(): GossipedPeerStore = GossipedPeerStore()

    @Provides @Singleton fun provideRelayBridgeRegistry(): RelayBridgeRegistry = RelayBridgeRegistry()

    // ---- Phase 3 multi-network bridging -------------------------------------
    //
    // Embedded TURN server for voice-call ICE relay. DiscoveryForegroundService
    // owns the start/stop lifecycle (tied to the existing phase-4 relayEnabled
    // flag — one toggle gates both message-relay forwarding and voice TURN).
    // PR 2 wires the TurnCredentialService surface into /v1/call/relay-cred.

    @Provides @Singleton fun provideOspChatTurnServer(): com.ospchat.shared.turn.OspChatTurnServer =
        com.ospchat.shared.turn.OspChatTurnServer()

    @Provides
    @Singleton
    fun providePeerRouter(
        discoveryRepository: DiscoveryRepository,
        gossipedPeerStore: GossipedPeerStore,
        relayBridgeRegistry: RelayBridgeRegistry,
    ): PeerRouter =
        PeerRouter(
            discoveryRepository = discoveryRepository,
            gossipedPeerStore = gossipedPeerStore,
            relayBridgeRegistry = relayBridgeRegistry,
        )

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
        groupDao: GroupDao,
        peerDao: PeerDao,
        discoveryRepository: DiscoveryRepository,
    ): ReactionRepository =
        ReactionRepository(
            dao = dao,
            client = client,
            identityRepository = identityRepository,
            groupDao = groupDao,
            peerDao = peerDao,
            discoveryRepository = discoveryRepository,
        )

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
        attachmentBounds: ImageBounds,
        peerRouter: PeerRouter,
        gossipedPeerStore: GossipedPeerStore,
    ): MessageRepository =
        MessageRepository(
            messageDao = messageDao,
            peerDao = peerDao,
            client = client,
            identityRepository = identityRepository,
            notifier = notifier,
            attachmentStore = attachmentStore,
            attachmentCompressor = attachmentCompressor,
            attachmentBounds = attachmentBounds,
            peerRouter = peerRouter,
            gossipedPeerStore = gossipedPeerStore,
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
        reactionRepository: ReactionRepository,
    ): GroupSyncer =
        GroupSyncer(
            groupDao = groupDao,
            groupMessageDao = groupMessageDao,
            groupRepository = groupRepository,
            client = client,
            identityRepository = identityRepository,
            reactionRepository = reactionRepository,
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

    // ---- Calls ---------------------------------------------------------------

    @Provides
    @Singleton
    fun provideAudioCallSessionFactory(
        @ApplicationContext context: android.content.Context,
    ): AndroidAudioCallSessionFactory = AndroidAudioCallSessionFactory(context)

    @Provides
    @Singleton
    fun provideAudioCallSessionFactoryAsShared(impl: AndroidAudioCallSessionFactory): AudioCallSessionFactory = impl

    @Provides
    @Singleton
    fun provideCallRepository(
        callDao: CallDao,
        client: MessageClient,
        identityRepository: IdentityRepository,
        discoveryRepository: DiscoveryRepository,
        sessionFactory: AudioCallSessionFactory,
        notifier: SharedCallNotifier,
        peerDao: PeerDao,
        relayBridgeRegistry: RelayBridgeRegistry,
        peerRouter: PeerRouter,
    ): CallRepository =
        CallRepository(
            dao = callDao,
            client = client,
            identityRepository = identityRepository,
            discoveryRepository = discoveryRepository,
            sessionFactory = sessionFactory,
            notifier = notifier,
            peerDao = peerDao,
            // Phase 3 multi-network bridging: speculative TURN cred prefetch
            // before sessionFactory.create on every outbound/inbound call.
            relayBridgeRegistry = relayBridgeRegistry,
            // Phase 5 multi-network bridging: outbound call signaling DTOs
            // route via PeerRouter — direct when target is in discovery,
            // bridged with `toUuid` set when target is only in gossip.
            peerRouter = peerRouter,
        )

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
        callRepository: CallRepository,
        client: MessageClient,
        gossipedPeerStore: GossipedPeerStore,
        peerDao: PeerDao,
        turnServer: com.ospchat.shared.turn.OspChatTurnServer,
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
            callRepository = callRepository,
            messageClient = client,
            gossipedPeerStore = gossipedPeerStore,
            peerDao = peerDao,
            // Phase 3: backs POST /v1/call/relay-cred when this node is a
            // bridge. Same OspChatTurnServer instance that's already started
            // and stopped by DiscoveryForegroundService.
            turnCredentialService = turnServer,
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

    @Binds
    @Singleton
    abstract fun bindCallNotifier(impl: CallNotifier): SharedCallNotifier
}
