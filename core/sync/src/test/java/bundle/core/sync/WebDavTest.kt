package bundle.core.sync

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebDavTest {
    private lateinit var server: MockWebServer
    private lateinit var dav: OkHttpWebDav

    @Before fun start() {
        server = MockWebServer().also { it.start() }
        dav = OkHttpWebDav(server.url("/remote.php/dav/files/alice/").toString(), "alice", "app-password")
    }

    @After fun stop() = server.shutdown()

    @Test fun `every request carries the credentials and goes to the account's folder`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hello"))
        assertEquals("hello", String(dav.get("BundleSync/a b.bsync")!!))
        val r = server.takeRequest()
        assertEquals("/remote.php/dav/files/alice/BundleSync/a%20b.bsync", r.path)
        assertTrue(r.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test fun `a missing file is null, a refused login is an auth error`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(dav.get("x"))
        server.enqueue(MockResponse().setResponseCode(401))
        try { dav.get("x"); fail() } catch (e: AuthException) { }
    }

    @Test fun `folders are made one level at a time and an existing one is fine`() {
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(405))
        dav.ensureFolder("BundleSync/devices")
        assertEquals("MKCOL", server.takeRequest().method)
        val second = server.takeRequest()
        assertEquals("MKCOL", second.method)
        assertEquals("/remote.php/dav/files/alice/BundleSync/devices", second.path)
    }

    @Test fun `a listing gives the files and leaves out the folder itself`() {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
                <d:response><d:href>/remote.php/dav/files/alice/BundleSync/devices/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                <d:response><d:href>/remote.php/dav/files/alice/BundleSync/devices/P%C3%A9ixel.bsync</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
                <d:response><d:href>/remote.php/dav/files/alice/BundleSync/devices/old/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                </d:multistatus>""",
            ),
        )
        val entries = dav.list("BundleSync/devices")
        assertEquals(listOf(DavEntry("Péixel.bsync", false), DavEntry("old", true)).size, entries.size)
        assertEquals("Depth: 1", "Depth: " + server.takeRequest().getHeader("Depth"))
        assertTrue(entries.any { it.name == "Péixel.bsync" && !it.isFolder })
        assertTrue(entries.any { it.name == "old" && it.isFolder })
    }

    @Test fun `a listing of a folder that does not exist is empty`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(dav.list("nope").isEmpty())
    }

    @Test fun `a put sends the bytes, and put-if-absent says whether it was written`() {
        val data = byteArrayOf(1, 2, 3)
        server.enqueue(MockResponse().setResponseCode(201))
        dav.put("f", data)
        assertArrayEquals(data, server.takeRequest().body.readByteArray())
        server.enqueue(MockResponse().setResponseCode(201))
        assertTrue(dav.putIfAbsent("v", data))
        assertEquals("*", server.takeRequest().getHeader("If-None-Match"))
        server.enqueue(MockResponse().setResponseCode(412))
        assertFalse(dav.putIfAbsent("v", data))
    }

    @Test fun `a server error is reported`() {
        server.enqueue(MockResponse().setResponseCode(500))
        try { dav.put("f", byteArrayOf(1)); fail() } catch (e: DavException) { assertEquals(500, e.code) }
    }

    @Test fun `plain http to a real server is refused`() {
        try { OkHttpWebDav("http://cloud.example.com/remote.php/dav/files/alice/", "a", "b"); fail() } catch (e: IllegalArgumentException) { }
        OkHttpWebDav("https://cloud.example.com/remote.php/dav/files/alice/", "a", "b")
        OkHttpWebDav("http://10.0.2.2:8080/dav/", "a", "b")
    }

    @Test fun `an xml bomb in a listing is rejected`() {
        val bomb = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "aaaa"><!ENTITY b "&a;&a;&a;&a;">]><d:multistatus xmlns:d="DAV:">&b;</d:multistatus>"""
        server.enqueue(MockResponse().setResponseCode(207).setBody(bomb))
        try { dav.list("x"); fail() } catch (e: Exception) { /* refused, not expanded */ }
    }
}
