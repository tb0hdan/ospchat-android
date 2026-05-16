package com.ospchat.android.net.server

import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.data.messages.MessageDao
import com.ospchat.android.data.messages.MessageRepository
import com.ospchat.android.net.ApiVersion
import com.ospchat.android.net.dto.ErrorDto
import com.ospchat.android.net.dto.IncomingMessageDto
import com.ospchat.android.net.dto.InfoDto
import com.ospchat.android.net.dto.ReadReceiptDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Identity captured at server startup. Passed in explicitly rather than read
 * back from `IdentityRepository` inside the handler so that what we serve via
 * `/v1/info` is guaranteed to match what was advertised over NSD when the
 * server started.
 */
internal data class ServerIdentity(
    val uuid: String,
    val nickname: String,
)

internal fun Routing.installMessageRoutes(
    identity: ServerIdentity,
    discoveryRepository: DiscoveryRepository,
    messageRepository: MessageRepository,
    messageDao: MessageDao,
) {
    route("/v1") {
        get("/info") {
            call.respond(
                InfoDto(
                    uuid = identity.uuid,
                    nickname = identity.nickname,
                    apiVersion = ApiVersion.V1,
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
    }
}

/**
 * Looks up the peer by [fromUuid] in the live NSD snapshot and verifies the
 * request's source IP matches the peer's advertised host. Responds with the
 * appropriate 4xx and returns `null` if the check fails; returns the [Peer]
 * otherwise.
 */
private suspend fun ApplicationCall.verifiedPeerOrRespond(
    fromUuid: String,
    discoveryRepository: DiscoveryRepository,
): Peer? {
    val known = discoveryRepository.findPeer(fromUuid)
    if (known == null) {
        respond(HttpStatusCode.NotFound, ErrorDto(ErrorCodes.UNKNOWN_PEER))
        return null
    }
    val remoteAddress = request.origin.remoteAddress
    if (!remoteAddress.matchesPeerHost(known.host)) {
        respond(HttpStatusCode.Unauthorized, ErrorDto(ErrorCodes.ADDRESS_MISMATCH))
        return null
    }
    return known
}

internal object ErrorCodes {
    const val BAD_REQUEST = "bad_request"
    const val UNKNOWN_PEER = "unknown_peer"
    const val ADDRESS_MISMATCH = "address_mismatch"
    const val INTERNAL_ERROR = "internal_error"
}

/**
 * Compare the request's remote address with the host we recorded from NSD.
 * Both are expected to be IP literals (Ktor CIO returns the connecting
 * socket's `InetAddress.hostAddress`; NSD also returns the raw address).
 * IPv6 scope IDs (e.g. `fe80::1%wlan0`) are stripped on either side.
 */
private fun String.matchesPeerHost(advertised: String): Boolean {
    if (this == advertised) return true
    val a = substringBefore('%')
    val b = advertised.substringBefore('%')
    return a == b
}
