package app.jourjour.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount of something (litres, pieces, kilograms) for categories that track quantity.
 * Held the same way as [Money] — a whole number of thousandths: 42.35 L becomes 42_350.
 */
@JvmInline
value class Quantity(val milli: Long) : Comparable<Quantity> {

    val isZero: Boolean get() = milli == 0L

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(milli, SCALE)

    override fun compareTo(other: Quantity): Int = milli.compareTo(other.milli)

    companion object {
        const val SCALE = 3

        val ZERO = Quantity(0)

        /** Rounded to thousandths; throws [ArithmeticException] for what does not fit, as [Money.of] does. */
        fun of(value: BigDecimal): Quantity =
            Quantity(value.setScale(SCALE, RoundingMode.HALF_UP).unscaledValue().longValueExact())
    }
}

fun Long?.asQuantity(): Quantity? = this?.let(::Quantity)

/**
 * Price per unit, shown for reference only. Never stored — it is derived when displayed,
 * so it cannot drift away from the amount and quantity it came from.
 * Returns null when there is no quantity, or it is zero.
 */
fun unitPrice(amount: Money?, quantity: Quantity?): Money? {
    if (amount == null || quantity == null || quantity.isZero) return null
    // A tiny quantity under a large amount can put the price beyond what fits; shown for
    // reference only, it is better left out than let crash the row.
    return runCatching {
        Money.of(amount.toBigDecimal().divide(quantity.toBigDecimal(), Money.SCALE, RoundingMode.HALF_UP))
    }.getOrNull()
}
