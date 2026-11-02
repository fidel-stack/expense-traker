package com.xyrospace.expensetracker.sms

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmsSenderFilterTest {

    private lateinit var previousLocale: Locale

    @Before
    fun useNonAsciiUppercasingLocale() {
        previousLocale = Locale.getDefault()
        // Turkish upper-casing maps "i" to "İ"; matching must not depend on the device locale.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    private val defaultSenders = SmsAllowlist.defaultSenders()

    private fun matches(raw: String?): Boolean = SmsSenderFilter.isAllowed(raw, defaultSenders)

    @Test
    fun defaultsContainSeventeenUniqueSenderIdentities() {
        val expected = setOf(
            "MPESA", "COOPBANK", "CO-OPBANK", "EQUITY BANK", "EQUITEL", "KCB", "KCB_BANK",
            "KCBBANK", "ABSA", "ABSA_BANK", "NCBA", "NCBABANK", "STANCHART", "FAMILYBANK",
            "DTB", "STANBIC", "I&M BANK",
        )

        assertEquals(17, defaultSenders.size)
        assertEquals(expected, defaultSenders.map { it.senderId }.toSet())
        assertEquals(17, defaultSenders.map { it.senderId }.toSet().size)
    }

    @Test
    fun equityBankCaseVariantsCollapseIntoOneDisplayName() {
        val equity = defaultSenders.filter { it.senderId == "EQUITY BANK" }
        assertEquals(1, equity.size)
        assertEquals("EQUITY BANK", equity.single().displayName)
    }

    @Test
    fun matchingIsCaseInsensitiveAndLocaleIndependent() {
        assertTrue(matches("MPESA"))
        assertTrue(matches("mpesa"))
        assertTrue(matches("MpEsA"))
        assertTrue(matches("CoopBank"))
        assertTrue(matches("COOPBANK"))
        assertTrue(matches("Equity Bank"))
        assertTrue(matches("equity bank"))
        assertTrue(matches("kcb_bank"))
        assertTrue(matches("equitel"))
        assertTrue(matches("I&M Bank"))
        assertEquals("EQUITEL", SmsSenderId.normalize("equitel"))
    }

    @Test
    fun matchingIsExactAfterNormalization() {
        assertFalse(matches("MPESA-ALERT"))
        assertFalse(matches("FAKE-MPESA"))
        assertFalse(matches("KCB_BANK_EXTRA"))
        assertFalse(matches("MPESA ALERT"))
        assertFalse(matches("MPESAEXTRA"))
        assertFalse(matches("EquityBank"))
        assertFalse(matches("CO-OP-BANK"))
    }

    @Test
    fun surroundingWhitespaceIsIgnoredButInternalWhitespaceIsNot() {
        assertTrue(matches("  mpesa  "))
        assertTrue(matches("\tKCB\n"))
        assertFalse(matches("EQUITY  BANK"))
    }

    @Test
    fun punctuationIsNeverSilentlyRemoved() {
        assertFalse(matches("MPESA."))
        assertFalse(matches("MPESA_"))
        assertFalse(matches("-MPESA"))
        assertFalse(matches("CO OPBANK"))
    }

    @Test
    fun emptyAndUnknownSendersAreRejected() {
        assertFalse(matches(null))
        assertFalse(matches(""))
        assertFalse(matches("   "))
        assertFalse(matches("UNKNOWNBANK"))
    }

    @Test
    fun validationRejectsEmptyOverlongAndWildcardInput() {
        assertInvalid("", SmsSenderValidationError.EMPTY)
        assertInvalid("    ", SmsSenderValidationError.EMPTY)
        assertInvalid("A".repeat(SmsSenderId.MAX_LENGTH + 1), SmsSenderValidationError.TOO_LONG)
        assertInvalid("*", SmsSenderValidationError.WILDCARD_PATTERN)
        assertInvalid("MPESA?", SmsSenderValidationError.WILDCARD_PATTERN)
        assertInvalid("MPESA*", SmsSenderValidationError.WILDCARD_PATTERN)
        assertInvalid("100%BANK", SmsSenderValidationError.WILDCARD_PATTERN)
    }

    @Test
    fun validationRejectsMalformedCharacters() {
        assertInvalid("MPESA|ALERT", SmsSenderValidationError.INVALID_CHARACTERS)
        assertInvalid("---", SmsSenderValidationError.INVALID_CHARACTERS)
        assertInvalid("BANK\u0000", SmsSenderValidationError.INVALID_CHARACTERS)
        assertInvalid("\uD83D\uDE00BANK", SmsSenderValidationError.INVALID_CHARACTERS)
    }

    @Test
    fun validationTrimsInputAndPreservesDisplayName() {
        val valid = SmsSenderId.validateSender("  newbank  ")
        assertTrue(valid is SmsSenderValidation.Valid)
        val sender = (valid as SmsSenderValidation.Valid).sender
        assertEquals("newbank", sender.displayName)
        assertEquals("NEWBANK", sender.senderId)
        assertTrue(SmsSenderFilter.isAllowed("NewBank", listOf(sender)))
    }

    @Test
    fun addingDuplicateSenderIgnoresCasing() {
        val candidate = (SmsSenderId.validateSender("mpesa") as SmsSenderValidation.Valid).sender
        val updated = SmsAllowlist.addSender(defaultSenders, candidate)
        assertEquals(defaultSenders.size, updated.size)
        assertEquals(1, updated.count { it.senderId == "MPESA" })
        assertTrue(SmsAllowlist.containsSender(defaultSenders, "mPeSa"))
    }

    @Test
    fun addedSenderMatchesRegardlessOfCasing() {
        val candidate = (SmsSenderId.validateSender("NEWBANK") as SmsSenderValidation.Valid).sender
        val updated = SmsAllowlist.addSender(defaultSenders, candidate)
        assertEquals(defaultSenders.size + 1, updated.size)
        assertTrue(SmsSenderFilter.isAllowed("newbank", updated))
        assertEquals("NEWBANK", SmsSenderFilter.findAllowedSender("newbank", updated)?.senderId)
    }

    @Test
    fun removedSenderStopsMatching() {
        val updated = SmsAllowlist.removeSender(defaultSenders, "mpesa")
        assertEquals(defaultSenders.size - 1, updated.size)
        assertFalse(SmsSenderFilter.isAllowed("MPESA", updated))
        assertFalse(SmsAllowlist.containsSender(updated, "MPESA"))
    }

    @Test
    fun encodedAllowlistRoundTripsWithDisplayNames() {
        val encoded = SmsAllowlist.encodeAll(defaultSenders)
        val decoded = SmsAllowlist.decodeAll(encoded)

        assertEquals(defaultSenders.size, decoded.size)
        assertEquals(defaultSenders.toSet(), decoded.toSet())
        // Decoding is deterministic and sorted for stable presentation.
        assertEquals(decoded, SmsAllowlist.decodeAll(encoded))
    }

    @Test
    fun malformedPersistedEntriesAreIgnored() {
        val decoded = SmsAllowlist.decodeAll(
            setOf(
                "not-an-entry",
                "MPESA\u001Fmpesa",
                "KCB\u001FKCB",
                "EMPTY\u001F   ",
                "WILDCARD\u001FBAD*ID",
            ),
        )
        assertEquals(listOf("KCB", "MPESA"), decoded.map { it.senderId }.sorted())
    }

    private fun assertInvalid(input: String, expected: SmsSenderValidationError) {
        val result = SmsSenderId.validateSender(input)
        assertTrue("Expected $input to be invalid", result is SmsSenderValidation.Invalid)
        assertEquals(expected, (result as SmsSenderValidation.Invalid).error)
    }
}
