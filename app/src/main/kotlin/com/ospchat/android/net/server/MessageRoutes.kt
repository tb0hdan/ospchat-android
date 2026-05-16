package com.ospchat.android.net.server

import com.ospchat.android.data.attachments.AttachmentStore
import com.ospchat.android.data.avatar.AvatarStore
import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.identity.IdentityRepository
import com.ospchat.android.data.messages.MessageDao
import com.ospchat.android.data.messages.MessageRepository
import com.ospchat.android.data.peers.PeerAvatarSync
import com.ospchat.android.net.ApiVersion
import com.ospchat.android.net.dto.ErrorDto
import com.ospchat.android.net.dto.IncomingMessageDto
import com.ospchat.android.net.dto.InfoDto
import com.ospchat.android.net.dto.ReadReceiptDto
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

internal data class ServerIdentity(
    val uuid: String,
    val nickname: String,
)

internal fun Routing.installMessageRoutes(
    identity: ServerIdentity,
    discoveryRepository: DiscoveryRepository,
    messageRepository: MessageRepository,
    messageDao: MessageDao,
    attachmentStore: AttachmentStore,
    avatarStore: AvatarStore,
    identityRepository: IdentityRepository,
    peerAvatarSync: PeerAvatarSync,
) {
    route("/v1") {
        get("/info") {
            // Read the avatar hash dynamically: the user can change their
            // avatar without the service restarting, and `/v1/info` should
            // reflect the latest value so peers can re-sync.
            val avatarHash = identityRepository.currentAvatarHash()
            call.respond(
                InfoDto(
                    uuid = identity.uuid,
                    nickname = identity.nickname,
                    apiVersion = ApiVersion.V1,
                    avatarHash = avatarHash,
                ),
            )
        }
        post("/messages") {
            val dto = call.receive<IncomingMessageDto>()
            val known = call.verifiedPeerOrRespond(dto.fromUuid, discoveryRepository) ?: return@post
            messageRepository.receive(known, dto)
            call.respond(HttpStatusCode.Accepted)
        }
        post("/read-receipts") {
            val dto = call.receive<ReadReceiptDto>()
            val known = call.verifiedPeerOrRespond(dto.fromUuid, discoveryRepository) ?: return@post
            messageDao.markOutboundRead(peerUuid = known.uuid, upToSentAt = dto.upToSentAt)
            call.respond(HttpStatusCode.Accepted)
        }
        get("/attachments/{messageId}") {
            val messageId =
                call.parameters["messageId"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorDto(ErrorCodes.BAD_REQUEST, "missing messageId"),
                    )
            call.verifiedRequestingPeerOrRespond(discoveryRepository) ?: return@get
            val file = attachmentStore.fileFor(messageId)
            if (!file.isFile) {
                call.respond(HttpStatusCode.NotFound, ErrorDto(ErrorCodes.UNKNOWN_PEER, "attachment not found"))
                return@get
            }
            val mime =
                messageDao.findById(messageId)?.attachmentMime
                    ?: "application/octet-stream"
            call.respondOutputStream(contentType = ContentType.parse(mime)) {
                file.inputStream().use { it.copyTo(this) }
            }
        }
        post("/notify-refresh") {
            // Empty-body POST; caller identified by source IP. Schedules a
            // background sync against the sending peer so the response goes
            // straight back without waiting for the HTTP round-trips.
            val peer = call.verifiedRequestingPeerOrRespond(discoveryRepository) ?: return@post
            peerAvatarSync.triggerSync(peer)
            call.respond(HttpStatusCode.Accepted)
        }
        get("/avatar") {
            call.verifiedRequestingPeerOrRespond(discoveryRepository) ?: return@get
            val currentHash = identityRepository.currentAvatarHash()
            if (currentHash == null) {
                call.respond(HttpStatusCode.NotFound, ErrorDto(ErrorCodes.UNKNOWN_PEER, "no custom avatar"))
                return@get
            }
            val file = avatarStore.selfFile(currentHash)
            if (!file.isFile) {
                call.respond(HttpStatusCode.NotFound, ErrorDto(ErrorCodes.UNKNOWN_PEER, "no custom avatar"))
                return@get
            }
            call.respondOutputStream(contentType = ContentType.Image.JPEG) {
                file.inputStream().use { it.copyTo(this) }
            }
        }
    }
}

private suspend fun ApplicationCall.verifiedPeerOrRespond(
    fromUuid: String,
    discoveryRepository: DiscoveryRepository,
): Peer? {
    val known = discoveryRepository.findPeer(fromUuid)
    if (known == null) {
        respond(HttpStatusCode.NotFound, ErrorDto(ErrorCodes.UNKNOWN_PEER))
        return null
    }
    if (!request.origin.remoteAddress.matchesPeerHost(known.host)) {
        respond(HttpStatusCode.Unauthorized, ErrorDto(ErrorCodes.ADDRESS_MISMATCH))
        return null
    }
    return known
}

/**
 * For endpoints without a body that identifies the caller (like `GET /v1/attachments`),
 * look up the requester by their source IP against the live NSD snapshot.
 */
private suspend fun ApplicationCall.verifiedRequestingPeerOrRespond(discoveryRepository: DiscoveryRepository): Peer? {
    val remoteAddress = request.origin.remoteAddress
    val match =
        discoveryRepository.peerSnapshot.value.values.firstOrNull { peer ->
            remoteAddress.matchesPeerHost(peer.host)
        }
    if (match == null) {
        respond(HttpStatusCode.Unauthorized, ErrorDto(ErrorCodes.ADDRESS_MISMATCH))
        return null
    }
    return match
}

internal object ErrorCodes {
    const val BAD_REQUEST = "bad_request"
    const val UNKNOWN_PEER = "unknown_peer"
    const val ADDRESS_MISMATCH = "address_mismatch"
    const val INTERNAL_ERROR = "internal_error"
}

private fun String.matchesPeerHost(advertised: String): Boolean {
    if (this == advertised) return true
    val a = substringBefore('%')
    val b = advertised.substringBefore('%')
    return a == b
}
