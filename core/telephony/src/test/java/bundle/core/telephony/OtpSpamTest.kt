package bundle.core.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpSpamTest {
    @Test fun findsCodes() {
        assertEquals("482913", Otp.find("Votre code de vérification est 482913. Ne le partagez pas."))
        assertEquals("123456", Otp.find("G-123456 is your Google verification code."))
        assertEquals("123456", Otp.find("Code de connexion : 123 456"))
        assertEquals("4821", Otp.find("Your PIN code: 4821"))
    }

    @Test fun ignoresNonCodes() {
        assertNull(Otp.find("Rendez-vous à 18h, appelle-moi au 0612345678"))
        assertNull(Otp.find("Votre solde est de 1250 €"))
        assertNull(Otp.find(null))
    }

    @Test fun telemarketing() {
        assertTrue(SpamRules.isFrenchTelemarketing("01 62 12 34 56"))
        assertTrue(SpamRules.isFrenchTelemarketing("+33 9 48 12 34 56"))
        assertTrue(SpamRules.isFrenchTelemarketing("0033377123456"))
        assertFalse(SpamRules.isFrenchTelemarketing("06 12 34 56 78"))
        assertFalse(SpamRules.isFrenchTelemarketing("01 61 12 34 56"))
    }
}
