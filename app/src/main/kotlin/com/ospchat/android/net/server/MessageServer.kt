package com.ospchat.android.net.server

import com.ospchat.android.data.discovery.DiscoveryRepository
import com.ospchat.android.data.messages.MessageDao
import com.ospchat.android.data.messages.MessageRepository
import com.ospchat.android.net.dto.ErrorDto
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Embedded Ktor HTTP server. Binds to an ephemeral port and exposes the
 * OSPChat peer API (see `docs/api/openapi.yaml`).
 *
 * Start / stop are made safe against concurrent invocation and against the
 * launching coroutine being cancelled mid-bind: [engine] is assigned before
 * we suspend, and a `try`-block ensures we tear the engine down if anything
 * after that throws (including [kotlinx.coroutines.CancellationException]).
 *
 * [start] is idempotent: if the server has already been started, it returns
 * the previously-bound port instead of throwing. This protects against a
 * second `onStartCommand` arriving at the foreground service (e.g. after
 * `PeersScreen`'s `LaunchedEffect` re-fires on navigation back).
 */
@Singleton
class MessageServer
    @Inject
    constructor(
        private val discoveryRepository: DiscoveryRepository,
        private val messageRepository: MessageRepository,
        private val messageDao: MessageDao,
    ) {
        @Volatile private var engine: ApplicationEngine? = null

        @Volatile private var boundPort: Int = 0
        private val lock = Any()

        suspend fun start(
            uuid: String,
            nickname: String,
        ): Int {
            synchronized(lock) {
                if (engine != null) return boundPort
            }
            val identity = ServerIdentity(uuid = uuid, nickname = nickname)

            val server =
                embeddedServer(CIO, port = 0, host = "0.0.0.0") {
                    install(ContentNegotiation) { json() }
                    install(StatusPages) {
                        // Malformed body / missing fields → structured 400 that
                        // matches the OpenAPI Error schema, instead of Ktor's
                        // default plain-text response.
                        exception<BadRequestException> { call, cause ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorDto(error = ErrorCodes.BAD_REQUEST, detail = cause.message),
                            )
                        }
                        exception<Throwable> { call, cause ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorDto(error = ErrorCodes.INTERNAL_ERROR, detail = cause.message),
                            )
                        }
                    }
                    routing {
                        installMessageRoutes(
                            identity = identity,
                            discoveryRepository = discoveryRepository,
                            messageRepository = messageRepository,
                            messageDao = messageDao,
                        )
                    }
                }

            synchronized(lock) {
                if (engine != null) {
                    runCatching { server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MS) }
                    return boundPort
                }
                engine = server
            }
            try {
                server.start(wait = false)
                val port = server.resolvedConnectors().first().port
                boundPort = port
                return port
            } catch (t: Throwable) {
                synchronized(lock) {
                    if (engine === server) {
                        engine = null
                        boundPort = 0
                    }
                }
                runCatching { server.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MS) }
                throw t
            }
        }

        fun stop() {
            val current =
                synchronized(lock) {
                    val e = engine
                    engine = null
                    boundPort = 0
                    e
                }
            current?.stop(gracePeriodMillis = 0, timeoutMillis = STOP_TIMEOUT_MS)
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 1_000L
        }
    }
