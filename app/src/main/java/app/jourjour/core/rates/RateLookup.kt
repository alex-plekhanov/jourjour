package app.jourjour.core.rates

import app.jourjour.core.money.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** A rate as it came back: what it is, and which day it belongs to. */
data class FetchedRate(
    val rate: Money,
    /** Units of the source currency the rate is quoted for. Always a power of ten. */
    val multiplier: Long,
    /** The day the source published it, which need not be the day that was asked for. */
    val date: LocalDate,
)

/** Why a lookup came back with nothing. Each maps to one line the form can show. */
enum class RateLookupError {
    /** The pair is not one the source publishes — many currencies never are. */
    UNSUPPORTED_PAIR,

    /** No network, a refused connection, a timeout: nothing was learnt, try again later. */
    UNREACHABLE,

    /** A reply that arrived but could not be read. */
    MALFORMED,
}

/**
 * Looks one exchange rate up on the internet.
 *
 * This is the only thing in the app that goes online, and it is a convenience rather than
 * a source of truth: a rate here is whatever the person decides to reckon in, and typing
 * one by hand is the normal way to fill the field. So a failed lookup is reported and
 * nothing else happens — no retries, no cache, no background refresh.
 *
 * Frankfurter republishes what central banks publish. It has no key and no quota, and it
 * answers for one pair at a time, which is exactly how the form asks.
 */
@Singleton
class RateLookup @Inject constructor() {

    /**
     * @param date the day to ask about, or null for the latest published rate. A day in
     * the future has none, so the caller asks for the latest instead.
     */
    suspend fun fetch(
        from: String,
        to: String,
        date: LocalDate?,
    ): Result<FetchedRate> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(ENDPOINT)
            append(from.lowercase())
            append('/')
            append(to.lowercase())
            if (date != null) append("?date=").append(date)
        }
        try {
            read(URL(url))
        } catch (e: IOException) {
            Result.failure(RateLookupFailure(RateLookupError.UNREACHABLE, e))
        }
    }

    private fun read(url: URL): Result<FetchedRate> {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                // A pair the source does not carry, and a date it has nothing for, both
                // come back as ordinary refusals rather than as failures of the network.
                return Result.failure(RateLookupFailure(RateLookupError.UNSUPPORTED_PAIR))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseRateReply(body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.frankfurter.dev/v2/rate/"
        const val TIMEOUT_MS = 10_000
    }
}

/** Reads one reply. Apart from the socket, this is the whole of what a lookup does. */
internal fun parseRateReply(body: String): Result<FetchedRate> = runCatching {
    val fields = Json.parseToJsonElement(body).jsonObject
    // Read as text rather than as a number: the reply writes the rate in decimal, and
    // going through a binary float would be the one place in the app where a figure stops
    // being exactly what was published.
    val quoted = BigDecimal(fields.getValue("rate").jsonPrimitive.content)
    val day = LocalDate.parse(fields.getValue("date").jsonPrimitive.content)
    val (scaled, multiplier) = quoteInWholeUnits(quoted)
    FetchedRate(
        rate = Money.of(scaled.setScale(Money.SCALE, RoundingMode.HALF_UP)),
        multiplier = multiplier,
        date = day,
    )
}.recoverCatching { throw RateLookupFailure(RateLookupError.MALFORMED, it) }

/**
 * Quotes a rate for as many units as it takes to get a digit in front of the decimal point.
 *
 * A rate below one loses its precision twice over: three decimals leave 0.00878 with barely
 * one significant figure, and nobody reads a rate written that way anyway. Quoting the same
 * thing per thousand units — 1000 JPY = 8.78 USD — is both readable and exact.
 */
internal fun quoteInWholeUnits(rate: BigDecimal): Pair<BigDecimal, Long> {
    if (rate <= BigDecimal.ZERO) return rate to 1L
    var scaled = rate
    var multiplier = 1L
    while (scaled < BigDecimal.ONE && multiplier < MAX_MULTIPLIER) {
        scaled = scaled.multiply(BigDecimal.TEN)
        multiplier *= 10
    }
    return scaled to multiplier
}

/** Past this a currency is worth so little that no quote of it would read. */
private const val MAX_MULTIPLIER = 1_000_000L

/** Carries [RateLookupError] out of a [Result]. */
class RateLookupFailure(
    val error: RateLookupError,
    cause: Throwable? = null,
) : Exception(cause)
