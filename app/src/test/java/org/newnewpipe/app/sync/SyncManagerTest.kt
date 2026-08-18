package org.newnewpipe.app.sync

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class SyncManagerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        // Bind explicitly to IPv4 loopback (see WebDavClientTest).
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        server.useHttps(handshakeCertificates.sslSocketFactory(), false)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun skipped_whenDisabledOrNotConfigured() = runBlocking {
        val disabled = SyncSettings(FakePrefs()) // enabled = false
        val unconfigured = SyncSettings(FakePrefs()).apply { enabled = true }

        assertTrue(SyncManager(disabled).syncNow(emptyList()) is SyncResult.Skipped)
        assertTrue(SyncManager(unconfigured).syncNow(emptyList()) is SyncResult.Skipped)
    }

    @Test
    fun firstSync_remote404_uploadsLocalSnapshot() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(405)) // MKCOL: already exists
        server.enqueue(MockResponse().setResponseCode(201))

        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"A","updated_at_ms":1000}]}"""
        val codec = FakeCodec("subscriptions.json", local = local)
        val result = syncNow(codec)

        assertEquals(1, result.entities.size)
        val entity = result.entities[0]
        assertTrue(entity.ok)
        assertTrue(entity.uploaded)
        assertFalse(entity.downloaded)
        // Nothing remote: the local snapshot is applied (canonically re-serialized).
        assertEquals(SubscriptionMerger.parse(local), SubscriptionMerger.parse(codec.applied))

        val requests = server.requestCount
        assertEquals(3, requests) // GET + MKCOL + PUT
        assertEquals("GET", server.takeRequest().method)
        assertEquals("MKCOL", server.takeRequest().method)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/dav/NewNewPipe/sync/subscriptions.json", put.path)
        assertEquals(
            SubscriptionMerger.parse(local),
            SubscriptionMerger.parse(put.body.readByteArray().toString(StandardCharsets.UTF_8)),
        )
    }

    @Test
    fun mergeConflict_lwwWinnerAndLocalOnlyItems_goThroughTheWire() {
        val remote = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Rinominato","updated_at_ms":2000},
            {"service_id":0,"url":"https://youtube.com/@b","name":"B","updated_at_ms":1500}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setBody(remote))
        server.enqueue(MockResponse().setResponseCode(405)) // MKCOL: already exists
        server.enqueue(MockResponse().setResponseCode(201))

        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"Vecchio","updated_at_ms":1000}]}"""
        val codec = FakeCodec("subscriptions.json", local = local)
        val result = syncNow(codec)

        val entity = result.entities[0]
        assertTrue(entity.toString(), entity.ok)
        assertTrue(entity.uploaded)
        assertTrue(entity.downloaded)
        val applied = SubscriptionMerger.parse(codec.applied)
        assertEquals(
            listOf(
                SyncSubscription(0, "https://youtube.com/@a", "Rinominato", 2000),
                SyncSubscription(0, "https://youtube.com/@b", "B", 1500),
            ),
            applied,
        )
        // The PUT body is exactly the merged snapshot (after GET and MKCOL).
        server.takeRequest() // GET
        server.takeRequest() // MKCOL
        val put = server.takeRequest()
        assertEquals(
            codec.applied,
            put.body.readByteArray().toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun remoteMissingAndLocalEmpty_nothingUploaded() {
        server.enqueue(MockResponse().setResponseCode(404))

        val codec = FakeCodec("subscriptions.json", local = null)
        val result = syncNow(codec)

        val entity = result.entities[0]
        assertTrue(entity.ok)
        assertFalse(entity.uploaded)
        assertFalse(entity.downloaded)
        assertNull(codec.applied)
        assertEquals(1, server.requestCount) // only the GET
    }

    @Test
    fun passphrase_encryptsPayloadOnTheWire_andDecryptsOnPull() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(201))

        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"A","updated_at_ms":1000}]}"""
        val codec = FakeCodec("subscriptions.json", local = local)

        val encrypted = syncNow(managerWith { blobPassphrase = "hunter2" }, codec)
        val entity = encrypted.entities[0]
        assertTrue(entity.ok)

        server.takeRequest() // GET 404
        server.takeRequest() // MKCOL
        val put = server.takeRequest()
        val storedBody = put.body.readByteArray()
        // The stored payload is NOT the plaintext JSON.
        assertFalse(storedBody.toString(StandardCharsets.UTF_8).contains("subscriptions"))
        // And it decrypts back to the local snapshot.
        assertEquals(
            SubscriptionMerger.parse(local),
            SubscriptionMerger.parse(
                SyncCipher("hunter2").decrypt(storedBody).toString(StandardCharsets.UTF_8),
            ),
        )

        // Second sync: the server now holds the encrypted blob; a fresh local
        // side must be able to read it back through the manager.
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(storedBody)))
        server.enqueue(MockResponse().setResponseCode(405)) // MKCOL: already exists
        server.enqueue(MockResponse().setResponseCode(201))

        val freshCodec = FakeCodec("subscriptions.json", local = null)
        val second = syncNow(managerWith { blobPassphrase = "hunter2" }, freshCodec)
        val secondEntity = second.entities[0]
        assertTrue(secondEntity.ok)
        assertTrue(secondEntity.downloaded)
        assertEquals(SubscriptionMerger.parse(local), SubscriptionMerger.parse(freshCodec.applied))
    }

    @Test
    fun wrongPassphrase_onPull_isRecordedAsErrorNotCrash() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(201))

        val local = """{"subscriptions":[
            {"service_id":0,"url":"https://youtube.com/@a","name":"A","updated_at_ms":1000}]}"""
        val codec = FakeCodec("subscriptions.json", local = local)
        syncNow(managerWith { blobPassphrase = "right" }, codec)
        server.takeRequest() // GET 404
        server.takeRequest() // MKCOL
        val storedBody = server.takeRequest().body.readByteArray()

        // Same server data, but the passphrase changed on this device.
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(storedBody)))
        server.enqueue(MockResponse().setResponseCode(405)) // MKCOL: already exists
        val freshCodec = FakeCodec("subscriptions.json", local = null)
        val result = syncNow(managerWith { blobPassphrase = "wrong" }, freshCodec)

        val entity = result.entities[0]
        assertFalse(entity.ok)
        assertNotNull(entity.error)
    }

    @Test
    fun failingEntity_doesNotAbortTheOthers() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(201))

        val broken = FakeCodec("broken.json", local = "{}", failSnapshot = true)
        val healthy = FakeCodec("subscriptions.json", local = null)
        val result = syncNow(broken, healthy)

        assertEquals(2, result.entities.size)
        assertFalse(result.entities[0].ok)
        assertNotNull(result.entities[0].error)
        assertTrue(result.entities[1].ok)
    }

    private fun syncNow(vararg codecs: SyncCodec): SyncResult.Completed =
        syncNow(managerWith { }, *codecs)

    private fun syncNow(manager: SyncManager, vararg codecs: SyncCodec): SyncResult.Completed =
        runBlocking { manager.syncNow(codecs.toList()) as SyncResult.Completed }

    private fun managerWith(configure: SyncSettings.() -> Unit): SyncManager {
        val settings = SyncSettings(FakePrefs())
        settings.enabled = true
        settings.serverUrl = "https://127.0.0.1:${server.port}/dav/"
        settings.username = "alice"
        settings.password = "secret"
        settings.configure()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(
                handshakeCertificates.sslSocketFactory(),
                handshakeCertificates.trustManager,
            )
            .hostnameVerifier { _, _ -> true }
            .build()
        return SyncManager(settings, client)
    }

    private class FakeCodec(
        override val fileName: String,
        var local: String?,
        var failSnapshot: Boolean = false,
    ) : SyncCodec {
        var applied: String? = null

        override fun localSnapshot(): String? {
            if (failSnapshot) throw IllegalStateException("boom")
            return local
        }

        override fun merge(local: String?, remote: String?): String? =
            SubscriptionMerger.merge(local, remote)

        override fun apply(merged: String?) {
            applied = merged
        }
    }

    private companion object {
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
    }
}
