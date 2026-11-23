package bundle.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class NextcloudTest {
    @Test fun `a server address becomes the account's webdav folder`() {
        assertEquals("https://cloud.example.com/remote.php/dav/files/alice/", Nextcloud.davBase("cloud.example.com", "alice"))
        assertEquals("https://cloud.example.com/nc/remote.php/dav/files/alice/", Nextcloud.davBase("https://cloud.example.com/nc/", "alice"))
        assertEquals("https://c.fr/remote.php/dav/files/jean%20dupont/", Nextcloud.davBase("https://c.fr", "jean dupont"))
    }

    @Test fun `a webdav address copied from Nextcloud is kept`() {
        assertEquals("https://c.fr/remote.php/dav/files/bob/", Nextcloud.davBase("https://c.fr/remote.php/dav/files/bob", "alice"))
    }
}
