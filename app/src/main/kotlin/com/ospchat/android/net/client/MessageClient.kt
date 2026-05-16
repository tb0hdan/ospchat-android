package com.ospchat.android.net.client

import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.net.dto.IncomingMessageDto
import com.ospchat.android.net.dto.InfoDto
import com.ospchat.android.net.dto.ReadReceiptDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper that knows how to POST to a peer over HTTP. The underlying
 * [HttpClient] is supplied by [com.ospchat.android.di.NetworkModule] so tests
 * can substitute a `MockEngine`.
 */
@Singleton
class MessageClient
    @Inject
    constructor(
        private val http: HttpClient,
    ) {
        suspend fun send(
            peer: Peer,
            body: IncomingMessageDto,
        ) {
            postJson(peer, "/v1/messages", body)
        }

        suspend fun sendReadReceipt(
            peer: Peer,
            body: ReadReceiptDto,
        ) {
            postJson(peer, "/v1/read-receipts", body)
        }

        /**
         * Tells [peer] that our `/v1/info` has changed — they should re-pull
         * to pick up nickname / avatarHash changes. The body is empty; the
         * caller is identified on the receiver side by source IP.
         */
        suspend fun notifyRefresh(peer: Peer) {
            val response: HttpResponse =
                http.post("http://${peer.host}:${peer.port}/v1/notify-refresh")
            if (!response.status.isSuccess()) {
                error("Peer rejected /v1/notify-refresh: HTTP ${response.status.value}")
            }
        }

        /**
         * Streams the attachment binary for [messageId] from [peer]. Invokes
         * [consume] with an [InputStream] over the response body; the stream
         * is closed when the lambda returns.
         */
        suspend fun <T> fetchAttachment(
            peer: Peer,
            messageId: String,
            consume: suspend (InputStream) -> T,
        ): T = streamFromPeer(peer, "/v1/attachments/$messageId", consume)

        suspend fun getInfo(peer: Peer): InfoDto {
            val response: HttpResponse =
                http.get("http://${peer.host}:${peer.port}/v1/info")
            if (!response.status.isSuccess()) {
                error("Peer rejected /v1/info: HTTP ${response.status.value}")
            }
            return response.body()
        }

        /**
         * Streams the peer's custom avatar bytes through [consume]. Throws if
         * the peer responds with anything other than 2xx (e.g. 404 when they
         * have no custom avatar set).
         */
        suspend fun <T> fetchAvatar(
            peer: Peer,
            consume: suspend (InputStream) -> T,
        ): T = streamFromPeer(peer, "/v1/avatar", consume)

        private suspend fun <T> streamFromPeer(
            peer: Peer,
            path: String,
            consume: suspend (InputStream) -> T,
        ): T {
            val response: HttpResponse =
                http.get("http://${peer.host}:${peer.port}$path")
            if (!response.status.isSuccess()) {
                error("Peer rejected $path: HTTP ${response.status.value}")
            }
            return response.bodyAsChannel().toInputStream().use { consume(it) }
        }

        private suspend inline fun <reified T> postJson(
            peer: Peer,
            path: String,
            body: T,
        ) {
            val response: HttpResponse =
                http.post("http://${peer.host}:${peer.port}$path") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            if (!response.status.isSuccess()) {
                error("Peer rejected $path: HTTP ${response.status.value}")
            }
        }
    }
