package com.ospchat.android.net.client

import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.net.dto.IncomingMessageDto
import com.ospchat.android.net.dto.ReadReceiptDto
import io.ktor.client.HttpClient
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
         * Streams the attachment binary for [messageId] from [peer]. Invokes
         * [consume] with an [InputStream] over the response body; the stream
         * is closed when the lambda returns.
         */
        suspend fun <T> fetchAttachment(
            peer: Peer,
            messageId: String,
            consume: suspend (InputStream) -> T,
        ): T {
            val response: HttpResponse =
                http.get("http://${peer.host}:${peer.port}/v1/attachments/$messageId")
            if (!response.status.isSuccess()) {
                error("Peer rejected attachment fetch: HTTP ${response.status.value}")
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
