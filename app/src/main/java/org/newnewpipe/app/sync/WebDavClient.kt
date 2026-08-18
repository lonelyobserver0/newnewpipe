package org.newnewpipe.app.sync

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal WebDAV client built on OkHttp.
 *
 * Implements the subset of RFC 4918 needed by the sync layer:
 * PROPFIND (allprop), GET, PUT, MKCOL and DELETE.
 *
 * - HTTPS is mandatory: plain-HTTP base URLs are rejected at construction time
 *   (credentials are never sent in clear text).
 * - Every request carries HTTP Basic authentication (RFC 7617).
 * - No extra dependencies: the PROPFIND multistatus XML is parsed with the
 *   JAXP [DocumentBuilder] available both on the JVM and on Android.
 *
 * Paths are relative to the base URL and are percent-encoded as needed
 * (use the href values returned by [propfind] verbatim, or plain relative
 * paths such as `"backups/subscriptions.json"`).
 */
class WebDavClient(
    baseUrl: String,
    username: String,
    password: String,
    client: OkHttpClient = defaultClient(),
) {

    /** A resource entry returned by a PROPFIND response. */
    data class Resource(
        val href: String,
        val isCollection: Boolean,
        val contentLength: Long?,
        val lastModified: String?,
        val displayName: String?,
    )

    private val base: HttpUrl = parseBaseUrl(baseUrl)

    private val httpClient: OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            val authenticated = chain.request().newBuilder()
                .header("Authorization", Credentials.basic(username, password))
                .build()
            chain.proceed(authenticated)
        }
        .build()

    /**
     * Lists the properties of the resource at [path].
     *
     * @param depth the WebDAV Depth header: "0" (the resource itself),
     *   "1" (immediate children, default) or "infinity".
     * @return the resources reported by the server, one per DAV:response.
     */
    fun propfind(path: String, depth: String = "1"): List<Resource> {
        val request = Request.Builder()
            .url(urlFor(path))
            .method("PROPFIND", PROPFIND_REQUEST.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth)
            .build()
        return httpClient.newCall(request).execute().use { response ->
            requireStatus(response, 207, action = "PROPFIND $path")
            parseMultistatus(response.body.string())
        }
    }

    /** Downloads the resource at [path] and returns its raw bytes. */
    fun get(path: String): ByteArray {
        val request = Request.Builder()
            .url(urlFor(path))
            .get()
            .build()
        return httpClient.newCall(request).execute().use { response ->
            requireStatus(response, 200, action = "GET $path")
            response.body.bytes()
        }
    }

    /** Uploads [data] to [path], creating or overwriting the resource. */
    fun put(path: String, data: ByteArray, contentType: String = "application/octet-stream") {
        val request = Request.Builder()
            .url(urlFor(path))
            .put(data.toRequestBody(contentType.toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            requireStatus(response, 200, 201, 204, action = "PUT $path")
        }
    }

    /** Creates the collection (directory) at [path]. Fails with [WebDavException] if it exists. */
    fun mkcol(path: String) {
        val request = Request.Builder()
            .url(urlFor(path))
            .method("MKCOL", null)
            .build()
        httpClient.newCall(request).execute().use { response ->
            requireStatus(response, 201, action = "MKCOL $path")
        }
    }

    /** Deletes the resource or collection at [path]. */
    fun delete(path: String) {
        val request = Request.Builder()
            .url(urlFor(path))
            .delete()
            .build()
        httpClient.newCall(request).execute().use { response ->
            requireStatus(response, 200, 204, action = "DELETE $path")
        }
    }

    private fun urlFor(path: String): HttpUrl {
        val trimmed = path.trimStart('/')
        return if (trimmed.isEmpty()) {
            base
        } else {
            base.newBuilder().addPathSegments(trimmed).build()
        }
    }

    private fun requireStatus(response: Response, vararg expected: Int, action: String) {
        if (response.code !in expected) {
            throw WebDavException(
                "$action failed: HTTP ${response.code} ${response.message}",
                statusCode = response.code,
            )
        }
    }

    private fun parseMultistatus(xml: String): List<Resource> {
        if (xml.isBlank()) return emptyList()
        val document = documentBuilder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response")
        val result = ArrayList<Resource>(responses.length)
        for (i in 0 until responses.length) {
            val response = responses.item(i) as Element
            val href = firstText(response, "href") ?: continue
            var isCollection = false
            var contentLength: Long? = null
            var lastModified: String? = null
            var displayName: String? = null
            val propstats = response.getElementsByTagNameNS(DAV_NAMESPACE, "propstat")
            for (j in 0 until propstats.length) {
                val propstat = propstats.item(j) as Element
                val status = firstText(propstat, "status") ?: continue
                if (statusCodeOf(status) != 200) continue
                val props = propstat.getElementsByTagNameNS(DAV_NAMESPACE, "prop")
                if (props.length == 0) continue
                val prop = props.item(0) as Element
                isCollection = prop.getElementsByTagNameNS(DAV_NAMESPACE, "collection").length > 0
                contentLength = firstText(prop, "getcontentlength")?.toLongOrNull()
                lastModified = firstText(prop, "getlastmodified")
                displayName = firstText(prop, "displayname")
            }
            result.add(Resource(href, isCollection, contentLength, lastModified, displayName))
        }
        return result
    }

    private fun firstText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagNameNS(DAV_NAMESPACE, tag)
        return if (nodes.length > 0) {
            nodes.item(0).textContent.trim().ifEmpty { null }
        } else {
            null
        }
    }

    private fun statusCodeOf(statusLine: String): Int? =
        statusLine.trim().split(" ").getOrNull(1)?.toIntOrNull()

    companion object {
        private const val DAV_NAMESPACE = "DAV:"
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
        private val PROPFIND_REQUEST =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<D:propfind xmlns:D=\"DAV:\"><D:allprop/></D:propfind>"

        private val documentBuilder: DocumentBuilder by lazy {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Harden against XXE when the parser supports it (the JVM does;
                // Android's Expat ignores unsupported features).
                setFeatureQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
                setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)
            }
            factory.newDocumentBuilder()
        }

        private fun DocumentBuilderFactory.setFeatureQuietly(name: String, value: Boolean) {
            try {
                setFeature(name, value)
            } catch (ignored: Exception) {
                // Unsupported feature on this parser: keep the default behaviour.
            }
        }

        private fun parseBaseUrl(baseUrl: String): HttpUrl {
            val url = baseUrl.toHttpUrl()
            require(url.scheme == "https") {
                "WebDAV sync requires an HTTPS base URL, got scheme '${url.scheme}'"
            }
            return url
        }

        /** Default client with sane timeouts; callers may pass their own [OkHttpClient]. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Thrown when a WebDAV request fails at the HTTP level
 * (unexpected status code or transport error).
 */
class WebDavException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
