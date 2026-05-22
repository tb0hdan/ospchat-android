package com.ospchat.android.seed

import android.util.Log
import com.ospchat.android.seed.catalog.PackageDescriptor
import com.ospchat.android.seed.catalog.SeedCatalog
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Stand-alone HTTP file server for Seed Mode. Bound to `0.0.0.0:8080` on
 * the hotspot interface; serves a self-contained landing page plus one
 * `GET /download/{id}` route per catalog entry. Independent of the peer
 * messaging server in `ospchat-shared` — this server has no auth and is
 * intentionally browser-friendly.
 */
internal class SeedServer(
    private val repository: SeedRepository,
) {
    private var engine: ApplicationEngine? = null

    /**
     * Starts the embedded Ktor server. Throws if the port is in use (the
     * caller — `SeedForegroundService` — catches and surfaces to the UI).
     */
    fun start(hotspotIp: String) {
        if (engine != null) {
            Log.w(TAG, "start() called while already running; ignoring")
            return
        }
        val server =
            embeddedServer(CIO, host = BIND_HOST, port = PORT) {
                install(PartialContent)
                install(AutoHeadResponse)
                routing {
                    get("/") {
                        call.respondText(
                            text = renderLandingPage(),
                            contentType = ContentType.Text.Html,
                        )
                    }
                    get("/health") {
                        call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                    }
                    get("/download/{id}") {
                        val id = call.parameters["id"].orEmpty()
                        val descriptor =
                            SeedCatalog.DEFAULT.firstOrNull { it.id == id }
                        if (descriptor == null) {
                            call.respondText(
                                """{"error":"unknown_package","id":"$id"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.NotFound,
                            )
                            return@get
                        }
                        val served = repository.servedFileFor(id)
                        if (served == null || !served.file.exists()) {
                            call.respondText(
                                """{"error":"not_cached","id":"$id"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.ServiceUnavailable,
                            )
                            return@get
                        }
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment
                                .withParameter(ContentDisposition.Parameters.FileName, served.downloadName)
                                .toString(),
                        )
                        call.respondFile(served.file)
                    }
                }
            }
        engine = server
        server.start(wait = false)
    }

    fun stop() {
        engine?.let {
            runCatching { it.stop(0L, 0L) }
                .onFailure { t -> Log.w(TAG, "Engine.stop() threw", t) }
        }
        engine = null
    }

    private fun renderLandingPage(): String {
        val rows =
            SeedCatalog.DEFAULT.joinToString("\n") { descriptor ->
                renderPackageRow(descriptor)
            }
        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>OSPChat — Install</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
           max-width: 640px; margin: 2em auto; padding: 0 1em; color: #1a1a1a; }
    h1 { font-size: 1.4em; }
    p.lead { color: #555; }
    ul { list-style: none; padding: 0; }
    li { padding: 0.6em 0; border-top: 1px solid #eee; }
    a.download { display: inline-block; font-weight: 600; color: #0066cc; text-decoration: none; }
    a.download:hover { text-decoration: underline; }
    .meta { color: #777; font-size: 0.9em; margin-left: 0.5em; }
    .unavailable { color: #999; font-size: 0.9em; }
    .recommended { background: #fff3cd; padding: 0.6em; border-radius: 4px;
                   margin-bottom: 1em; display: none; }
    .recommended.show { display: block; }
  </style>
</head>
<body>
  <h1>OSPChat</h1>
  <p class="lead">Pick the installer for your device. After the download
    finishes, open the file to install OSPChat.</p>
  <div id="rec" class="recommended">
    Recommended for this device: <strong id="recName"></strong> —
    <a id="recLink" class="download" href="#">Download</a>
  </div>
  <ul>
$rows
  </ul>
  <script>
    (function() {
      var ua = navigator.userAgent || "";
      var hint = null, label = null;
      if (/Android/i.test(ua))            { hint = "android";       label = "Android"; }
      else if (/Windows NT/i.test(ua))    { hint = "windows";       label = "Windows"; }
      else if (/Mac OS X/i.test(ua) && /Apple/i.test(navigator.vendor || "") && /arm|ARM/.test(ua))
                                          { hint = "macos-arm64";   label = "macOS (Apple Silicon)"; }
      else if (/Mac OS X/i.test(ua))      { hint = "macos-x86_64";  label = "macOS"; }
      else if (/Linux/i.test(ua))         { hint = "linux-deb";     label = "Linux"; }
      if (hint) {
        var rec = document.getElementById("rec");
        document.getElementById("recName").textContent = label;
        document.getElementById("recLink").href = "/download/" + hint;
        rec.classList.add("show");
      }
    })();
  </script>
</body>
</html>
"""
    }

    private fun renderPackageRow(descriptor: PackageDescriptor): String {
        val served = repository.servedFileFor(descriptor.id)
        return if (served != null && served.file.exists()) {
            val mb = "%.1f".format(served.file.length() / 1_048_576.0)
            """    <li><a class="download" href="/download/${descriptor.id}">${escape(descriptor.displayName)}</a>""" +
                """<span class="meta">${escape(served.downloadName)} · $mb MB</span></li>"""
        } else {
            """    <li>${escape(descriptor.displayName)} """ +
                """<span class="unavailable">— not yet downloaded on this seed</span></li>"""
        }
    }

    private fun escape(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    companion object {
        const val PORT: Int = 8080
        private const val BIND_HOST = "0.0.0.0"
        private const val TAG = "SeedServer"

        /**
         * Helper to fabricate the URL for a known hotspot IP without binding
         * the server — used by the ViewModel to generate the QR before the
         * service has had a chance to publish `boundUrl`.
         */
        fun urlFor(hotspotIp: String): String = "http://$hotspotIp:$PORT/"
    }
}
