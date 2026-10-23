package sk.matej.spajza.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import sk.matej.spajza.R
import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.unit.MeasureUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** A recipe amount with its unit, e.g. "200 g", "1½ ks", "2 PL", "podľa chuti". */
@Composable
fun amountText(amount: Double, unit: MeasureUnit): String {
    val locale = currentLocale()
    return when (unit) {
        MeasureUnit.G -> massText(amount, locale)
        MeasureUnit.KG -> massText(amount * 1000, locale)
        MeasureUnit.ML -> volumeText(amount, locale)
        MeasureUnit.L -> volumeText(amount * 1000, locale)
        MeasureUnit.PCS -> "${NumberFormat.quarters(amount)} ${stringResource(R.string.unit_pcs)}"
        MeasureUnit.TBSP -> "${NumberFormat.quarters(amount)} ${stringResource(R.string.unit_tbsp)}"
        MeasureUnit.TSP -> "${NumberFormat.quarters(amount)} ${stringResource(R.string.unit_tsp)}"
        MeasureUnit.CUP -> "${NumberFormat.quarters(amount)} ${pluralStringResource(R.plurals.unit_cup, ceil(amount).toInt())}"
        MeasureUnit.PINCH -> {
            val pinches = max(1, ceil(amount - 1e-6).toInt())
            "$pinches ${pluralStringResource(R.plurals.unit_pinch, pinches)}"
        }
        MeasureUnit.TO_TASTE -> stringResource(R.string.unit_to_taste)
    }
}

/** A pantry or shopping amount in the ingredient's pantry unit, e.g. "1,5 kg", "6 ks". */
@Composable
fun pantryAmountText(amount: Double, unit: PantryUnit): String {
    val locale = currentLocale()
    return when (unit) {
        PantryUnit.G -> massText(amount, locale)
        PantryUnit.ML -> volumeText(amount, locale)
        PantryUnit.PCS -> "${NumberFormat.quarters(amount)} ${stringResource(R.string.unit_pcs)}"
    }
}

@Composable
private fun massText(grams: Double, locale: Locale): String =
    if (grams >= 1000) "${NumberFormat.decimal(grams / 1000, locale)} ${stringResource(R.string.unit_kg)}"
    else "${NumberFormat.whole(grams)} ${stringResource(R.string.unit_g)}"

@Composable
private fun volumeText(millilitres: Double, locale: Locale): String =
    if (millilitres >= 1000) "${NumberFormat.decimal(millilitres / 1000, locale)} ${stringResource(R.string.unit_l)}"
    else "${NumberFormat.whole(millilitres)} ${stringResource(R.string.unit_ml)}"

/** "25 min", "1 h", "1 h 20 min". */
@Composable
fun durationText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.time_minutes, minutes)
    minutes % 60 == 0 -> stringResource(R.string.time_hours, minutes / 60)
    else -> stringResource(R.string.time_hours_minutes, minutes / 60, minutes % 60)
}

@Composable
fun expiryText(expiry: Expiry): String = when (expiry.state) {
    ExpiryState.EXPIRED -> stringResource(R.string.pantry_expired)
    ExpiryState.TODAY -> stringResource(R.string.pantry_expires_today)
    ExpiryState.SOON, ExpiryState.LATER -> pluralStringResource(R.plurals.pantry_expires_in, expiry.daysLeft.toInt(), expiry.daysLeft.toInt())
}
