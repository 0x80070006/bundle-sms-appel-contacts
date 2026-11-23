package bundle.core.sync

import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

/** Something in a WebDAV folder. */
data class DavEntry(val name: String, val isFolder: Boolean)

/** The server answered with an error ([code] is the HTTP status). */
class DavException(val code: Int, message: String) : IOException(message)

/** The server refused the user name or the password (401 or 403). */
class AuthException : IOException("the server refused the user name or password")

/** The few WebDAV operations the sync needs. Paths are relative to the account's root, without a leading slash. */
interface WebDav {
    /** Makes the folder and any missing parent; fine if it's already there. */
    fun ensureFolder(path: String)

    /** The files and folders directly inside [path]; empty if it doesn't exist. */
    fun list(path: String): List<DavEntry>

    /** The file's content, or null if there's no such file. */
    fun get(path: String): ByteArray?

    /** Writes the file, replacing any existing one. */
    fun put(path: String, data: ByteArray)

    /** Writes the file only if it doesn't exist yet; false if it did (nothing is written then). */
    fun putIfAbsent(path: String, data: ByteArray): Boolean

    /** Deletes the file; fine if it's already gone. */
    fun delete(path: String)
}

/**
 * [WebDav] over HTTPS for a Nextcloud account (also works with other WebDAV servers). [base] is the
 * account's WebDAV address, such as `https://cloud.example.com/remote.php/dav/files/alice/`.
 * Plain http is refused except to this machine (`localhost`, `127.0.0.1`, the emulator's `10.0.2.2`),
 * where it never crosses a network.
 */
class OkHttpWebDav(base: String, private val user: String, private val password: String, client: OkHttpClient? = null) : WebDav {
    private val root = base.trimEnd('/').toHttpUrl().also { url ->
        require(url.isHttps || url.host in LOCAL_HOSTS) { "Use an https address (plain http is only allowed to this machine)" }
    }
    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val auth = Credentials.basic(user, password, Charsets.UTF_8)

    private fun url(path: String) = root.newBuilder().apply { path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) } }.build()

    private fun request(path: String, method: String, body: ByteArray? = null, vararg headers: Pair<String, String>): Request {
        val builder = Request.Builder().url(url(path)).header("Authorization", auth).header("User-Agent", "BundleSync")
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.method(method, body?.toRequestBody(null) ?: if (method in NEEDS_BODY) ByteArray(0).toRequestBody(null) else null).build()
    }

    private inline fun <T> call(request: Request, handle: (okhttp3.Response) -> T): T =
        http.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw AuthException()
            handle(response)
        }

    override fun ensureFolder(path: String) {
        var current = ""
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            current = if (current.isEmpty()) segment else "$current/$segment"
            call(request(current, "MKCOL")) { r ->
                // 201 made, 405 already there (Nextcloud), 301/302 trailing-slash redirects on some servers.
                if (r.code !in listOf(200, 201, 204, 301, 302, 405)) throw DavException(r.code, "MKCOL $current: ${r.code}")
            }
        }
    }

    override fun list(path: String): List<DavEntry> {
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val request = request(path, "PROPFIND", body.toByteArray(), "Depth" to "1", "Content-Type" to "application/xml")
        return call(request) { r ->
            when (r.code) {
                404 -> emptyList()
                207 -> parseListing(r.body?.string().orEmpty(), url(path).encodedPath)
                else -> throw DavException(r.code, "PROPFIND $path: ${r.code}")
            }
        }
    }

    override fun get(path: String): ByteArray? = call(request(path, "GET")) { r ->
        when (r.code) {
            200 -> r.body?.bytes() ?: ByteArray(0)
            404 -> null
            else -> throw DavException(r.code, "GET $path: ${r.code}")
        }
    }

    override fun put(path: String, data: ByteArray) {
        call(request(path, "PUT", data)) { r -> if (r.code !in listOf(200, 201, 204)) throw DavException(r.code, "PUT $path: ${r.code}") }
    }

    override fun putIfAbsent(path: String, data: ByteArray): Boolean =
        call(request(path, "PUT", data, "If-None-Match" to "*")) { r ->
            when (r.code) {
                200, 201, 204 -> true
                412 -> false
                else -> throw DavException(r.code, "PUT $path: ${r.code}")
            }
        }

    override fun delete(path: String) {
        call(request(path, "DELETE")) { r -> if (r.code !in listOf(200, 204, 404)) throw DavException(r.code, "DELETE $path: ${r.code}") }
    }

    internal companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")
        private val NEEDS_BODY = setOf("PUT", "POST", "PROPPATCH", "REPORT")

        /** The entries of a PROPFIND answer, except the folder itself. A hostile server's XML can't expand entities. */
        fun parseListing(xml: String, folderPath: String): List<DavEntry> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://javax.xml.XMLConstants/feature/secure-processing", true) }
            }
            val document = factory.newDocumentBuilder().parse(xml.byteInputStream())
            val responses = document.getElementsByTagNameNS("DAV:", "response")
            val self = decode(folderPath).trimEnd('/')
            val out = ArrayList<DavEntry>()
            for (i in 0 until responses.length) {
                val node = responses.item(i) as org.w3c.dom.Element
                val href = node.getElementsByTagNameNS("DAV:", "href").item(0)?.textContent?.trim() ?: continue
                val path = decode(href.substringBefore('?').let { if (it.startsWith("http")) java.net.URI(it).rawPath else it }).trimEnd('/')
                if (path == self || path.isEmpty()) continue
                val folder = node.getElementsByTagNameNS("DAV:", "collection").length > 0
                out += DavEntry(path.substringAfterLast('/'), folder)
            }
            return out
        }

        private fun decode(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

        @Suppress("unused")
        private fun encode(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}

/** Nextcloud addresses. */
object Nextcloud {
    /**
     * The account's WebDAV folder from what the user typed: a server (`cloud.example.com`,
     * `https://cloud.example.com/nextcloud`) or the WebDAV address Nextcloud shows in its file
     * settings, which is kept as it is. A missing scheme means https.
     */
    fun davBase(input: String, user: String): String {
        var url = input.trim()
        if (!url.contains("://")) url = "https://$url"
        if (url.contains("/remote.php/dav/files/")) return url.trimEnd('/') + "/"
        if (url.trimEnd('/').endsWith("/remote.php/webdav")) return url.trimEnd('/') + "/"
        return url.trimEnd('/') + "/remote.php/dav/files/" + URLEncoder.encode(user.trim(), "UTF-8").replace("+", "%20") + "/"
    }
}
