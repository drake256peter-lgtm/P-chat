package com.example.data.payment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToLong

data class CountryCurrencyInfo(
    val countryName: String,
    val flag: String,
    val currencyCode: String,
    val currencyName: String,
    val symbol: String,
    val minorUnitDecimals: Int,
    val aliases: List<String> = emptyList()
)

data class FxRatesSnapshot(
    val ratesFromUsd: Map<String, Double>,
    val isLive: Boolean,
    val sourceLabel: String,
    val updatedAtLabel: String,
    val fetchedAtEpochMs: Long
) {
    fun rateFor(currencyCode: String): Double {
        val upper = currencyCode.trim().uppercase(Locale.US)
        return if (upper == "USD") {
            1.0
        } else {
            ratesFromUsd[upper] ?: CurrencyConversionEngine.BASELINE_USD_RATES[upper] ?: 1.0
        }
    }
}

data class FxTransferQuote(
    val sendUsdCents: Long,
    val senderCurrency: String = "USD",
    val recipientCountry: String,
    val recipientFlag: String,
    val recipientCurrency: CountryCurrencyInfo,
    val exchangeRate: Double,
    val isLiveRate: Boolean,
    val rateSourceLabel: String,
    val recipientLocalAmountMinor: Long,
    val fxConversionFeeCents: Long,
    val providerRailFeeCents: Long,
    val networkSettlementFeeCents: Long,
    val totalFeeCents: Long,
    val totalDebitedCents: Long,
    val provider: ApprovedPaymentProvider
)

object CurrencyConversionEngine {

    private const val LIVE_FX_ENDPOINT = "https://open.er-api.com/v6/latest/USD"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    val SUPPORTED_COUNTRIES: List<CountryCurrencyInfo> = listOf(
        CountryCurrencyInfo("Uganda", "🇺🇬", "UGX", "Ugandan Shilling", "UGX", 0, listOf("uganda", "kampala")),
        CountryCurrencyInfo("Kenya", "🇰🇪", "KES", "Kenyan Shilling", "KSh", 2, listOf("kenya", "nairobi")),
        CountryCurrencyInfo("Tanzania", "🇹🇿", "TZS", "Tanzanian Shilling", "TSh", 0, listOf("tanzania", "dar es salaam", "dodoma", "zanzibar")),
        CountryCurrencyInfo("Rwanda", "🇷🇼", "RWF", "Rwandan Franc", "RWF", 0, listOf("rwanda", "kigali")),
        CountryCurrencyInfo("Nigeria", "🇳🇬", "NGN", "Nigerian Naira", "₦", 2, listOf("nigeria", "lagos", "abuja")),
        CountryCurrencyInfo("South Africa", "🇿🇦", "ZAR", "South African Rand", "R", 2, listOf("south africa", "johannesburg", "cape town")),
        CountryCurrencyInfo("Ghana", "🇬🇭", "GHS", "Ghanaian Cedi", "GH₵", 2, listOf("ghana", "accra")),
        CountryCurrencyInfo("Ethiopia", "🇪🇹", "ETB", "Ethiopian Birr", "Br", 2, listOf("ethiopia", "addis ababa")),
        CountryCurrencyInfo("Egypt", "🇪🇬", "EGP", "Egyptian Pound", "E£", 2, listOf("egypt", "cairo", "alexandria")),
        CountryCurrencyInfo("Morocco", "🇲🇦", "MAD", "Moroccan Dirham", "MAD", 2, listOf("morocco", "casablanca", "rabat")),
        CountryCurrencyInfo("Senegal", "🇸🇳", "XOF", "West African CFA Franc", "CFA", 0, listOf("senegal", "dakar", "ivory coast", "cote d'ivoire", "benin", "togo", "mali")),
        CountryCurrencyInfo("Cameroon", "🇨🇲", "XAF", "Central African CFA Franc", "FCFA", 0, listOf("cameroon", "douala", "yaounde", "gabon", "congo")),
        CountryCurrencyInfo("United Kingdom", "🇬🇧", "GBP", "British Pound", "£", 2, listOf("united kingdom", "uk", "britain", "great britain", "england", "london")),
        CountryCurrencyInfo("European Union", "🇪🇺", "EUR", "Euro", "€", 2, listOf("europe", "eurozone", "germany", "france", "spain", "italy", "netherlands", "portugal", "belgium", "ireland", "austria", "finland", "greece")),
        CountryCurrencyInfo("Switzerland", "🇨🇭", "CHF", "Swiss Franc", "CHF", 2, listOf("switzerland", "zurich", "geneva")),
        CountryCurrencyInfo("Poland", "🇵🇱", "PLN", "Polish Zloty", "zł", 2, listOf("poland", "warsaw", "krakow")),
        CountryCurrencyInfo("Sweden", "🇸🇪", "SEK", "Swedish Krona", "kr", 2, listOf("sweden", "stockholm")),
        CountryCurrencyInfo("Norway", "🇳🇴", "NOK", "Norwegian Krone", "kr", 2, listOf("norway", "oslo")),
        CountryCurrencyInfo("Denmark", "🇩🇰", "DKK", "Danish Krone", "kr", 2, listOf("denmark", "copenhagen")),
        CountryCurrencyInfo("Turkey", "🇹🇷", "TRY", "Turkish Lira", "₺", 2, listOf("turkey", "turkiye", "istanbul", "ankara")),
        CountryCurrencyInfo("Russia", "🇷🇺", "RUB", "Russian Ruble", "₽", 2, listOf("russia", "moscow")),
        CountryCurrencyInfo("United Arab Emirates", "🇦🇪", "AED", "UAE Dirham", "AED", 2, listOf("uae", "united arab emirates", "dubai", "abu dhabi")),
        CountryCurrencyInfo("Saudi Arabia", "🇸🇦", "SAR", "Saudi Riyal", "SAR", 2, listOf("saudi arabia", "riyadh", "jeddah")),
        CountryCurrencyInfo("Qatar", "🇶🇦", "QAR", "Qatari Riyal", "QAR", 2, listOf("qatar", "doha")),
        CountryCurrencyInfo("Israel", "🇮🇱", "ILS", "Israeli New Shekel", "₪", 2, listOf("israel", "tel aviv", "jerusalem")),
        CountryCurrencyInfo("India", "🇮🇳", "INR", "Indian Rupee", "₹", 2, listOf("india", "mumbai", "delhi", "bangalore")),
        CountryCurrencyInfo("Pakistan", "🇵🇰", "PKR", "Pakistani Rupee", "Rs", 2, listOf("pakistan", "karachi", "lahore", "islamabad")),
        CountryCurrencyInfo("Bangladesh", "🇧🇩", "BDT", "Bangladeshi Taka", "৳", 2, listOf("bangladesh", "dhaka")),
        CountryCurrencyInfo("China", "🇨🇳", "CNY", "Chinese Yuan", "¥", 2, listOf("china", "beijing", "shanghai", "prc")),
        CountryCurrencyInfo("Japan", "🇯🇵", "JPY", "Japanese Yen", "¥", 0, listOf("japan", "tokyo", "osaka")),
        CountryCurrencyInfo("South Korea", "🇰🇷", "KRW", "South Korean Won", "₩", 0, listOf("south korea", "korea", "seoul")),
        CountryCurrencyInfo("Singapore", "🇸🇬", "SGD", "Singapore Dollar", "S$", 2, listOf("singapore")),
        CountryCurrencyInfo("Indonesia", "🇮🇩", "IDR", "Indonesian Rupiah", "Rp", 0, listOf("indonesia", "jakarta", "bali")),
        CountryCurrencyInfo("Malaysia", "🇲🇾", "MYR", "Malaysian Ringgit", "RM", 2, listOf("malaysia", "kuala lumpur")),
        CountryCurrencyInfo("Thailand", "🇹🇭", "THB", "Thai Baht", "฿", 2, listOf("thailand", "bangkok")),
        CountryCurrencyInfo("Vietnam", "🇻🇳", "VND", "Vietnamese Dong", "₫", 0, listOf("vietnam", "hanoi", "ho chi minh")),
        CountryCurrencyInfo("Philippines", "🇵🇭", "PHP", "Philippine Peso", "₱", 2, listOf("philippines", "manila")),
        CountryCurrencyInfo("Australia", "🇦🇺", "AUD", "Australian Dollar", "A$", 2, listOf("australia", "sydney", "melbourne")),
        CountryCurrencyInfo("New Zealand", "🇳🇿", "NZD", "New Zealand Dollar", "NZ$", 2, listOf("new zealand", "auckland", "wellington")),
        CountryCurrencyInfo("Canada", "🇨🇦", "CAD", "Canadian Dollar", "CA$", 2, listOf("canada", "toronto", "vancouver")),
        CountryCurrencyInfo("Mexico", "🇲🇽", "MXN", "Mexican Peso", "MX$", 2, listOf("mexico", "mexico city")),
        CountryCurrencyInfo("Brazil", "🇧🇷", "BRL", "Brazilian Real", "R$", 2, listOf("brazil", "sao paulo", "rio")),
        CountryCurrencyInfo("Colombia", "🇨🇴", "COP", "Colombian Peso", "COL$", 0, listOf("colombia", "bogota", "medellin")),
        CountryCurrencyInfo("Chile", "🇨🇱", "CLP", "Chilean Peso", "CLP$", 0, listOf("chile", "santiago")),
        CountryCurrencyInfo("Argentina", "🇦🇷", "ARS", "Argentine Peso", "AR$", 2, listOf("argentina", "buenos aires")),
        CountryCurrencyInfo("Peru", "🇵🇪", "PEN", "Peruvian Sol", "S/", 2, listOf("peru", "lima")),
        CountryCurrencyInfo("United States", "🇺🇸", "USD", "US Dollar", "$", 2, listOf("united states", "usa", "us", "america", "global"))
    )

    val BASELINE_USD_RATES: Map<String, Double> = mapOf(
        "USD" to 1.0,
        "UGX" to 3720.0,
        "KES" to 129.50,
        "TZS" to 2690.0,
        "RWF" to 1365.0,
        "NGN" to 1580.0,
        "ZAR" to 18.15,
        "GHS" to 15.60,
        "ETB" to 122.0,
        "EGP" to 49.30,
        "MAD" to 9.85,
        "XOF" to 604.0,
        "XAF" to 604.0,
        "GBP" to 0.79,
        "EUR" to 0.92,
        "CHF" to 0.88,
        "PLN" to 4.02,
        "SEK" to 10.65,
        "NOK" to 10.90,
        "DKK" to 6.87,
        "TRY" to 34.40,
        "RUB" to 96.50,
        "AED" to 3.6725,
        "SAR" to 3.75,
        "QAR" to 3.64,
        "ILS" to 3.72,
        "INR" to 84.40,
        "PKR" to 277.80,
        "BDT" to 119.50,
        "CNY" to 7.24,
        "JPY" to 154.20,
        "KRW" to 1395.0,
        "SGD" to 1.34,
        "IDR" to 15850.0,
        "MYR" to 4.44,
        "THB" to 34.50,
        "VND" to 25350.0,
        "PHP" to 58.60,
        "AUD" to 1.54,
        "NZD" to 1.68,
        "CAD" to 1.40,
        "MXN" to 20.15,
        "BRL" to 5.78,
        "COP" to 4380.0,
        "CLP" to 970.0,
        "ARS" to 995.0,
        "PEN" to 3.78
    )

    fun searchCountries(query: String): List<CountryCurrencyInfo> {
        val q = query.trim().lowercase(Locale.US)
        if (q.isEmpty()) return SUPPORTED_COUNTRIES
        return SUPPORTED_COUNTRIES.filter { info ->
            info.countryName.lowercase(Locale.US).contains(q) ||
                info.currencyCode.lowercase(Locale.US).contains(q) ||
                info.currencyName.lowercase(Locale.US).contains(q) ||
                info.aliases.any { it.contains(q) }
        }
    }

    @Volatile
    private var cachedSnapshot: FxRatesSnapshot? = null

    fun defaultSnapshot(nowMs: Long = System.currentTimeMillis()): FxRatesSnapshot {
        val formatter = SimpleDateFormat("HH:mm", Locale.US)
        return FxRatesSnapshot(
            ratesFromUsd = BASELINE_USD_RATES,
            isLive = false,
            sourceLabel = "Reference FX Rates",
            updatedAtLabel = formatter.format(Date(nowMs)),
            fetchedAtEpochMs = nowMs
        )
    }

    suspend fun getLiveOrCachedRates(forceRefresh: Boolean = false): FxRatesSnapshot {
        val now = System.currentTimeMillis()
        val cached = cachedSnapshot
        if (!forceRefresh && cached != null && (now - cached.fetchedAtEpochMs) < CACHE_TTL_MS && cached.isLive) {
            return cached
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(LIVE_FX_ENDPOINT)
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        error("FX HTTP $code")
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parseFxJsonResponse(body, now)
                } finally {
                    conn.disconnect()
                }
            }.getOrElse {
                cached ?: defaultSnapshot(now)
            }
        }
    }

    internal fun parseFxJsonResponse(rawJson: String, nowMs: Long = System.currentTimeMillis()): FxRatesSnapshot {
        val ratesBlockMatch = Regex("\"rates\"\\s*:\\s*\\{([^}]*)\\}").find(rawJson)
            ?: error("Missing rates object")
        val ratesBody = ratesBlockMatch.groupValues[1]
        val pairRegex = Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        val merged = BASELINE_USD_RATES.toMutableMap()
        var parsedAny = false
        for (match in pairRegex.findAll(ratesBody)) {
            val code = match.groupValues[1].uppercase(Locale.US)
            val rate = match.groupValues[2].toDoubleOrNull() ?: Double.NaN
            if (rate.isFinite() && rate > 0.0001 && rate < 10_000_000.0) {
                merged[code] = rate
                parsedAny = true
            }
        }
        if (!parsedAny) error("No valid FX rates found in response")
        merged["USD"] = 1.0
        val formatter = SimpleDateFormat("HH:mm", Locale.US)
        val snapshot = FxRatesSnapshot(
            ratesFromUsd = merged.toMap(),
            isLive = true,
            sourceLabel = "Live FX • open.er-api.com",
            updatedAtLabel = formatter.format(Date(nowMs)),
            fetchedAtEpochMs = nowMs
        )
        cachedSnapshot = snapshot
        return snapshot
    }

    fun resolveCountryCurrency(
        country: String?,
        countryFlag: String? = null
    ): CountryCurrencyInfo {
        val normalizedCountry = country.orEmpty().trim().lowercase(Locale.US)
        val normalizedFlag = countryFlag.orEmpty().trim()

        if (normalizedFlag.isNotEmpty()) {
            SUPPORTED_COUNTRIES.firstOrNull { it.flag == normalizedFlag }?.let { return it }
        }
        if (normalizedCountry.isNotEmpty()) {
            SUPPORTED_COUNTRIES.firstOrNull {
                it.countryName.equals(normalizedCountry, ignoreCase = true) ||
                    it.currencyCode.equals(normalizedCountry, ignoreCase = true)
            }?.let { return it }

            SUPPORTED_COUNTRIES.firstOrNull { info ->
                info.aliases.any { alias -> normalizedCountry.contains(alias) } ||
                    normalizedCountry.contains(info.countryName.lowercase(Locale.US))
            }?.let { return it }
        }
        return SUPPORTED_COUNTRIES.last() // United States / USD default
    }

    fun currencyByCode(currencyCode: String): CountryCurrencyInfo {
        val upper = currencyCode.trim().uppercase(Locale.US)
        return SUPPORTED_COUNTRIES.firstOrNull { it.currencyCode == upper }
            ?: SUPPORTED_COUNTRIES.last()
    }

    fun calculateInternationalQuote(
        sendUsdCents: Long,
        recipientCountry: CountryCurrencyInfo,
        provider: ApprovedPaymentProvider,
        snapshot: FxRatesSnapshot
    ): FxTransferQuote {
        require(sendUsdCents >= 100L) { "Minimum transfer amount is $1.00 USD." }

        val targetCode = recipientCountry.currencyCode
        val isForeignCurrency = targetCode != "USD"
        val isCrossBorder = isForeignCurrency ||
            (!recipientCountry.countryName.equals("United States", ignoreCase = true) &&
                !recipientCountry.countryName.equals("Global", ignoreCase = true))

        val rate = if (!isForeignCurrency) {
            1.0
        } else {
            snapshot.ratesFromUsd[targetCode] ?: BASELINE_USD_RATES[targetCode] ?: 1.0
        }

        val fxFeeCents = if (isForeignCurrency) {
            ((sendUsdCents * provider.fxMarkupPercent) / 100.0).roundToLong().coerceAtLeast(10L)
        } else {
            0L
        }
        val railFeeCents = if (isCrossBorder) provider.flatFeeCents else 0L
        val settlementFeeCents = if (isCrossBorder) {
            (sendUsdCents * 0.0025).roundToLong().coerceAtLeast(5L)
        } else {
            0L
        }

        val totalFeeCents = fxFeeCents + railFeeCents + settlementFeeCents
        val totalDebitedCents = sendUsdCents + totalFeeCents

        val sendUsdMajor = sendUsdCents / 100.0
        val convertedMajor = sendUsdMajor * rate
        val minorFactor = if (recipientCountry.minorUnitDecimals == 0) 1.0 else 100.0
        val recipientLocalMinor = (convertedMajor * minorFactor).roundToLong().coerceAtLeast(1L)

        return FxTransferQuote(
            sendUsdCents = sendUsdCents,
            senderCurrency = "USD",
            recipientCountry = recipientCountry.countryName,
            recipientFlag = recipientCountry.flag,
            recipientCurrency = recipientCountry,
            exchangeRate = rate,
            isLiveRate = snapshot.isLive,
            rateSourceLabel = "${snapshot.sourceLabel} (${snapshot.updatedAtLabel})",
            recipientLocalAmountMinor = recipientLocalMinor,
            fxConversionFeeCents = fxFeeCents,
            providerRailFeeCents = railFeeCents,
            networkSettlementFeeCents = settlementFeeCents,
            totalFeeCents = totalFeeCents,
            totalDebitedCents = totalDebitedCents,
            provider = provider
        )
    }

    fun convertUsdCentsToLocalMinor(
        usdCents: Long,
        rate: Double,
        decimals: Int
    ): Long {
        if (usdCents <= 0L) return 0L
        val major = (usdCents / 100.0) * rate
        val minorFactor = if (decimals == 0) 1.0 else 100.0
        return (major * minorFactor).roundToLong().coerceAtLeast(0L)
    }

    fun formatLocalAmount(minorAmount: Long, currencyInfo: CountryCurrencyInfo): String {
        val numberFormatter = NumberFormat.getNumberInstance(Locale.US)
        return if (currencyInfo.minorUnitDecimals == 0) {
            "${currencyInfo.symbol} ${numberFormatter.format(minorAmount)}"
        } else {
            numberFormatter.minimumFractionDigits = 2
            numberFormatter.maximumFractionDigits = 2
            val major = minorAmount / 100.0
            val formatted = numberFormatter.format(major)
            if (currencyInfo.symbol.length > 2) {
                "${currencyInfo.symbol} $formatted"
            } else {
                "${currencyInfo.symbol}$formatted"
            }
        }
    }

    fun formatExchangeRate(rate: Double, targetCurrencyCode: String): String {
        val info = currencyByCode(targetCurrencyCode)
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = if (info.minorUnitDecimals == 0 && rate >= 100.0) 0 else 2
            maximumFractionDigits = if (rate >= 100.0) 2 else 4
        }
        return "1 USD = ${formatter.format(rate)} $targetCurrencyCode"
    }
}
