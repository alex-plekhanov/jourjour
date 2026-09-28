package app.jourjour.core.money

import app.jourjour.data.entity.Currency
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A monetary amount, without a currency attached.
 *
 * Held as a whole number of thousandths of a currency unit: 1250.50 becomes 1_250_500.
 * Integers rule out the rounding drift of `Double`, which piles up once a trip's entries
 * are summed. The 1/1000 scale is the same for every currency and covers even the
 * three-decimal ones (KWD, BHD, JOD); how many decimals to show a person is a separate
 * question answered by [Currency.decimals].
 */
@JvmInline
value class Money(val milli: Long) : Comparable<Money> {

    operator fun plus(other: Money) = Money(milli + other.milli)
    operator fun minus(other: Money) = Money(milli - other.milli)
    operator fun unaryMinus() = Money(-milli)

    val isZero: Boolean get() = milli == 0L
    val isNegative: Boolean get() = milli < 0L

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(milli, SCALE)

    override fun compareTo(other: Money): Int = milli.compareTo(other.milli)

    companion object {
        /** Decimal places of the internal representation. */
        const val SCALE = 3

        val ZERO = Money(0)

        /**
         * Rounded to thousandths. Throws [ArithmeticException] for a value the ledger
         * cannot hold: silently wrapping it would store a different amount than was typed.
         */
        fun of(value: BigDecimal): Money =
            Money(value.setScale(SCALE, RoundingMode.HALF_UP).unscaledValue().longValueExact())

        fun ofUnits(units: Long): Money = Money(units * 1_000L)
    }
}

fun Long?.asMoney(): Money? = this?.let(::Money)