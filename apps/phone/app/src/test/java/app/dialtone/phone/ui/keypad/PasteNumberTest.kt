package app.dialtone.phone.ui.keypad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PasteNumberTest {
    @Test fun `keeps only what a number is dialled with`() {
        assertEquals("+33612345678", dialableOf("+33 6 12-34-56-78"))
        assertEquals("0612345678", dialableOf("Tél. : 06.12.34.56.78"))
        assertEquals("*#06#", dialableOf(" *#06# "))
    }

    @Test fun `text without a number pastes nothing`() {
        assertNull(dialableOf("bonjour"))
        assertNull(dialableOf("à 18h"))
    }
}
