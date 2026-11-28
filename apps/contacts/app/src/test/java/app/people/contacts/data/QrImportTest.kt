package app.people.contacts.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrImportTest {
    @Test fun `a vcard is kept as is`() {
        val v = "BEGIN:VCARD\nVERSION:3.0\nFN:Léa\nEND:VCARD"
        assertEquals(v, QrImport.toVCard(v))
    }

    @Test fun `a mecard becomes a vcard`() {
        val v = QrImport.toVCard("MECARD:N:Moreau,Léa;TEL:0611223344;EMAIL:lea@x.fr;;")!!
        assertTrue(v.contains("FN:Léa Moreau"))
        assertTrue(v.contains("TEL:0611223344"))
        assertTrue(v.contains("EMAIL:lea@x.fr"))
    }

    @Test fun `other codes are not contacts`() {
        assertNull(QrImport.toVCard("https://example.com"))
    }
}
