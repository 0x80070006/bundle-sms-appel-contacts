package bundle.core.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {
    /** Stands in for Android's E.164 formatter with the phone's region set to France. */
    private fun franceE164(number: String): String? {
        val digits = number.filter { it.isDigit() }
        return when {
            number.trim().startsWith("+") -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            digits.length == 10 && digits.startsWith("0") -> "+33" + digits.drop(1)
            else -> null
        }
    }

    @Test fun numberKeyMatchesFrenchNumbersHoweverWritten() {
        val key = numberKey("06 12 34 56 78", ::franceE164)
        assertEquals("33612345678", key)
        assertEquals(key, numberKey("+33 6 12 34 56 78", ::franceE164))
        assertEquals(key, numberKey("0033 612 345 678", ::franceE164))
    }

    @Test fun lastTenDigitsWouldNotMatchFrenchNumbers() {
        // Why numberKey no longer keeps only the last 10 digits.
        assertEquals("0612345678", "0612345678".takeLast(10))
        assertEquals("3612345678", "33612345678".takeLast(10))
    }

    @Test fun numberKeyFallsBackToLastTenDigitsWhenUnparsed() {
        assertEquals("5551234567", numberKey("+1 (555) 123-4567") { null })
        assertEquals("5551234567", numberKey("555.123.4567") { null })
    }

    @Test fun numberKeyOfShortCodesIsUnchanged() {
        assertEquals("36179", numberKey("36179", ::franceE164))
    }

    @Test fun initialOfSkipsNonLetters() {
        assertEquals("É", initialOf("  élodie"))
        assertNull(initialOf("+33 6"))
        assertNull(initialOf(null))
    }

    @Test fun foldForSearchRemovesAccents() {
        assertEquals("jose francois", foldForSearch("José François"))
    }

    @Test fun formatDurationSwitchesToHours() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("12:34", formatDuration(754))
        assertEquals("1:00:01", formatDuration(3601))
    }
}
