package org.newnewpipe.app.sync

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.Base64

class WebDavClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    @Before
    fun setUp() {
        server = MockWebServer()
        // Bind explicitly to IPv4 loopback: `localhost` can resolve to ::1 on
        // some machines while the server binds 127.0.0.1 (ConnectException).
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        server.useHttps(handshakeCertificates.sslSocketFactory(), false)

        val clientForTest = OkHttpClient.Builder()
            .sslSocketFactory(
                handshakeCertificates.sslSocketFactory(),
                handshakeCertificates.trustManager,
            )
            .hostnameVerifier { _, _ -> true }
            .build()

        client = WebDavClient(
            baseUrl = "https://127.0.0.1:${server.port}/dav/",
            username = "alice",
            password = "secret",
            client = clientForTest,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun propfindParsesMultistatus() {
        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml")
                .setBody(MULTISTATUS_XML)
        )

        val resources = client.propfind("/")

        assertEquals(2, resources.size)

        val root = resources[0]
        assertTrue(root.isCollection)
        assertEquals("/dav/", root.href)
        assertEquals("dav", root.displayName)

        val file = resources[1]
        assertFalse(file.isCollection)
        assertEquals("/dav/reports.json", file.href)
        assertEquals(42L, file.contentLength)
        assertEquals("reports.json", file.displayName)

        val recorded = server.takeRequest()
        assertEquals("PROPFIND", recorded.method)
        assertEquals("/dav/", recorded.path)
        assertEquals("1", recorded.getHeader("Depth"))
        assertEquals("Basic " + base64("alice:secret"), recorded.getHeader("Authorization"))
    }

    @Test
    fun putSendsBodyContentTypeAndAuth() {
        server.enqueue(MockResponse().setResponseCode(201))
        val payload = "{\"hello\":\"world\"}".toByteArray(Charsets.UTF_8)

        client.put("backups/subs.json", payload, contentType = "application/json")

        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/dav/backups/subs.json", recorded.path)
        assertArrayEquals(payload, recorded.body.readByteArray())
        assertEquals("application/json", recorded.getHeader("Content-Type"))
        assertEquals("Basic " + base64("alice:secret"), recorded.getHeader("Authorization"))
    }

    @Test
    fun getDownloadsRawBytes() {
        val expected = "subscriptions data".toByteArray(Charsets.UTF_8)
        server.enqueue(MockResponse().setResponseCode(200).setBody("subscriptions data"))

        val result = client.get("backups/subs.json")

        assertArrayEquals(expected, result)
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/dav/backups/subs.json", recorded.path)
    }

    @Test
    fun mkcolAndDeleteRoundtrip() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(204))

        client.mkcol("backups")
        client.delete("backups/old.json")

        val mkcol = server.takeRequest()
        assertEquals("MKCOL", mkcol.method)
        assertEquals("/dav/backups", mkcol.path)

        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/dav/backups/old.json", delete.path)
    }

    @Test
    fun unexpectedStatusThrowsWebDavExceptionWithCode() {
        server.enqueue(MockResponse().setResponseCode(404))

        val exception = assertThrows(WebDavException::class.java) { client.get("missing.txt") }

        assertEquals(404, exception.statusCode)
    }

    @Test
    fun plainHttpBaseUrlIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavClient("http://example.com/dav", "alice", "secret")
        }
    }

    private companion object {
        val MULTISTATUS_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>dav</D:displayname>
                    <D:resourcetype><D:collection/></D:resourcetype>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/reports.json</D:href>
                <D:propstat>
                  <D:prop>
                    <D:displayname>reports.json</D:displayname>
                    <D:getcontentlength>42</D:getcontentlength>
                    <D:getlastmodified>Mon, 18 Aug 2026 08:00:00 GMT</D:getlastmodified>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        // Self-signed certificate trusted by both the test client and the server.
        val handshakeCertificates: HandshakeCertificates by lazy {
            val heldCertificate = HeldCertificate.Builder()
                .commonName("localhost")
                .addSubjectAlternativeName("localhost")
                .addSubjectAlternativeName("127.0.0.1")
                .build()
            HandshakeCertificates.Builder()
                .heldCertificate(heldCertificate)
                .addTrustedCertificate(heldCertificate.certificate)
                .build()
        }

        fun base64(value: String): String =
            Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    }
}
