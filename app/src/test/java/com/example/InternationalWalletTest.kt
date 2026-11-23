package com.example

import com.example.data.payment.ApprovedPaymentProvider
import com.example.data.payment.CurrencyConversionEngine
import com.example.data.payment.PaymentProviderGateway
import com.example.data.payment.SettlementVerificationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternationalWalletTest {

    @Test
    fun countryIdentification_autoSelectsRecipientLocalCurrency() {
        val uganda = CurrencyConversionEngine.resolveCountryCurrency("Uganda", "🇺🇬")
        assertEquals("UGX", uganda.currencyCode)
        assertEquals(0, uganda.minorUnitDecimals)

        val kenyaByFlag = CurrencyConversionEngine.resolveCountryCurrency("", "🇰🇪")
        assertEquals("KES", kenyaByFlag.currencyCode)

        val ukAlias = CurrencyConversionEngine.resolveCountryCurrency("UK", "")
        assertEquals("GBP", ukAlias.currencyCode)

        val china = CurrencyConversionEngine.resolveCountryCurrency("China", "🇨🇳")
        assertEquals("CNY", china.currencyCode)

        val germanyEu = CurrencyConversionEngine.resolveCountryCurrency("Germany", "🇩🇪")
        assertEquals("EUR", germanyEu.currencyCode)

        val japan = CurrencyConversionEngine.resolveCountryCurrency("Japan", "🇯🇵")
        assertEquals("JPY", japan.currencyCode)
        assertEquals(0, japan.minorUnitDecimals)
    }

    @Test
    fun quoteCalculation_computesExactRecipientLocalAmountAndItemizedFees() {
        val snapshot = CurrencyConversionEngine.defaultSnapshot(1_700_000_000_000L)
        val uganda = CurrencyConversionEngine.resolveCountryCurrency("Uganda", "🇺🇬")
        val provider = PaymentProviderGateway.defaultProviderForCurrency("UGX")

        val quote = CurrencyConversionEngine.calculateInternationalQuote(
            sendUsdCents = 5_000L, // $50.00 USD
            recipientCountry = uganda,
            provider = provider,
            snapshot = snapshot
        )

        assertEquals(5_000L, quote.sendUsdCents)
        assertEquals("UGX", quote.recipientCurrency.currencyCode)
        // 50.0 * 3720.0 = 186,000 UGX exact
        assertEquals(186_000L, quote.recipientLocalAmountMinor)
        assertEquals(
            quote.fxConversionFeeCents + quote.providerRailFeeCents + quote.networkSettlementFeeCents,
            quote.totalFeeCents
        )
        assertEquals(quote.sendUsdCents + quote.totalFeeCents, quote.totalDebitedCents)
        assertTrue(quote.totalFeeCents > 0L)
    }

    @Test
    fun parseFxJson_mergesValidRatesAndRejectsCorruptEntries() {
        val raw = """
            {
              "result": "success",
              "rates": {
                "UGX": 3745.5,
                "GBP": 0.785,
                "BAD_NEG": -5.0,
                "BAD_ZERO": 0.0
              }
            }
        """.trimIndent()
        val parsed = CurrencyConversionEngine.parseFxJsonResponse(raw, 1_700_000_000_000L)
        assertTrue(parsed.isLive)
        assertEquals(3745.5, parsed.ratesFromUsd["UGX"] ?: 0.0, 0.001)
        assertEquals(0.785, parsed.ratesFromUsd["GBP"] ?: 0.0, 0.0001)
        assertFalse(parsed.ratesFromUsd.containsKey("BAD_NEG"))
        assertFalse(parsed.ratesFromUsd.containsKey("BAD_ZERO"))
    }

    @Test
    fun settlementVerification_failsClosedOnInsufficientBalanceOrUnapprovedProvider() {
        runBlocking {
            val snapshot = CurrencyConversionEngine.defaultSnapshot(1_700_000_000_000L)
            val uk = CurrencyConversionEngine.resolveCountryCurrency("United Kingdom", "🇬🇧")
            val approved = PaymentProviderGateway.defaultProviderForCurrency("GBP")
            val quote = CurrencyConversionEngine.calculateInternationalQuote(
                sendUsdCents = 10_000L, // $100 USD
                recipientCountry = uk,
                provider = approved,
                snapshot = snapshot
            )

            // Exact send amount without fee coverage must be rejected before balance mutation
            val insufficientRes = PaymentProviderGateway.processAndVerifySettlement(
                senderUid = "alice",
                recipientUid = "bob",
                quote = quote,
                senderAvailableUsdCents = 10_000L,
                skipNetworkDelayForTest = true
            )
            assertTrue(insufficientRes is SettlementVerificationResult.Rejected)

            // Unapproved provider rejected
            val rogueProvider = ApprovedPaymentProvider(
                id = "unapproved_gw",
                displayName = "RoguePay",
                badge = "X",
                regulatoryLicenseId = "NONE",
                settlementSla = "N/A",
                flatFeeCents = 0L,
                fxMarkupPercent = 0.0,
                supportedCurrencies = emptySet(),
                isApproved = false
            )
            val rogueQuote = quote.copy(provider = rogueProvider)
            val rogueRes = PaymentProviderGateway.processAndVerifySettlement(
                senderUid = "alice",
                recipientUid = "bob",
                quote = rogueQuote,
                senderAvailableUsdCents = 50_000L,
                skipNetworkDelayForTest = true
            )
            assertTrue(rogueRes is SettlementVerificationResult.Rejected)

            // Valid balance covering principal + fees produces verified settlement & receipt IDs
            val verifiedRes = PaymentProviderGateway.processAndVerifySettlement(
                senderUid = "alice",
                recipientUid = "bob",
                quote = quote,
                senderAvailableUsdCents = quote.totalDebitedCents,
                skipNetworkDelayForTest = true
            )
            assertTrue(verifiedRes is SettlementVerificationResult.Verified)
            val verified = verifiedRes as SettlementVerificationResult.Verified
            assertTrue(verified.settlementRef.startsWith("STL-"))
            assertTrue(verified.receiptId.startsWith("RCT-"))
            assertEquals(approved.displayName, verified.providerName)
        }
    }

    @Test
    fun expandedCountryCurrencies_allHaveRatesSearchAndApprovedProviders() {
        assertTrue(CurrencyConversionEngine.SUPPORTED_COUNTRIES.size >= 45)
        CurrencyConversionEngine.SUPPORTED_COUNTRIES.forEach { info ->
            val rate = CurrencyConversionEngine.BASELINE_USD_RATES[info.currencyCode]
            assertTrue("Missing baseline FX rate for ${info.currencyCode}", rate != null && rate > 0.0)
            val providers = PaymentProviderGateway.providersForCurrency(info.currencyCode)
            assertTrue("No approved payment providers for ${info.currencyCode}", providers.isNotEmpty())
        }

        assertEquals("TZS", CurrencyConversionEngine.resolveCountryCurrency("Tanzania", "🇹🇿").currencyCode)
        assertEquals("RWF", CurrencyConversionEngine.resolveCountryCurrency("Rwanda", "🇷🇼").currencyCode)
        assertEquals("CHF", CurrencyConversionEngine.resolveCountryCurrency("Switzerland", "🇨🇭").currencyCode)
        assertEquals("TRY", CurrencyConversionEngine.resolveCountryCurrency("Turkey", "🇹🇷").currencyCode)
        assertEquals("PHP", CurrencyConversionEngine.resolveCountryCurrency("Philippines", "🇵🇭").currencyCode)
        assertEquals("COP", CurrencyConversionEngine.resolveCountryCurrency("Colombia", "🇨🇴").currencyCode)

        val searched = CurrencyConversionEngine.searchCountries("tzs")
        assertTrue(searched.any { it.countryName == "Tanzania" && it.currencyCode == "TZS" })
    }
}
