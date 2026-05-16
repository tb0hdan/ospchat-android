package com.ospchat.android.net.client

import com.ospchat.android.data.discovery.Peer
import com.ospchat.android.net.dto.IncomingMessageDto
import com.ospchat.android.net.dto.ReadReceiptDto
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper that knows how to POST to a peer over HTTP. The underlying
 * [HttpClient] is supplied by [com.ospchat.android.di.NetworkModule] so tests
 * can substitute a `MockEngine`.
 *
 * The HttpClient is process-bound (singleton). We deliberately do not expose
 * a `close()` here: there is no service-scoped lifecycle for it on Android,
 * and the engine threads are reclaimed when the process exits.
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
