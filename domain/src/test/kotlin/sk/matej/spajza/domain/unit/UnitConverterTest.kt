package sk.matej.spajza.domain.unit

import sk.matej.spajza.domain.model.PantryUnit
import sk.matej.spajza.domain.testing.ingredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnitConverterTest {
    private val flour = ingredient("flour", densityGPerMl = 0.53)
    private val oil = ingredient("oil", pantryUnit = PantryUnit.ML, densityGPerMl = 0.92)
    private val milk = ingredient("milk", pantryUnit = PantryUnit.ML, densityGPerMl = 1.03)
    private val egg = ingredient("egg", pantryUnit = PantryUnit.PCS, gramsPerPiece = 50.0)
    private val rice = ingredient("rice")

    @Test
    fun `mass units convert directly`() {
        assertEquals(250.0, UnitConverter.toGrams(250.0, MeasureUnit.G, rice))
        assertEquals(1500.0, UnitConverter.toGrams(1.5, MeasureUnit.KG, rice))
    }

    @Test
    fun `volume units use density`() {
        assertEquals(206.0, UnitConverter.toGrams(200.0, MeasureUnit.ML, milk), DELTA)
        assertEquals(515.0, UnitConverter.toGrams(0.5, MeasureUnit.L, milk), DELTA)
        assertEquals(27.6, UnitConverter.toGrams(2.0, MeasureUnit.TBSP, oil), DELTA)
        assertEquals(4.6, UnitConverter.toGrams(1.0, MeasureUnit.TSP, oil), DELTA)
        assertEquals(132.5, UnitConverter.toGrams(1.0, MeasureUnit.CUP, flour), DELTA)
    }

    @Test
    fun `pieces use grams per piece`() {
        assertEquals(150.0, UnitConverter.toGrams(3.0, MeasureUnit.PCS, egg))
    }

    @Test
    fun `pinch and to taste need no ingredient data`() {
        assertEquals(1.0, UnitConverter.toGrams(2.0, MeasureUnit.PINCH, rice))
        assertEquals(0.0, UnitConverter.toGrams(1.0, MeasureUnit.TO_TASTE, rice))
    }

    @Test
    fun `missing density fails loudly`() {
        val error = assertFailsWith<MissingConversionException> {
            UnitConverter.toGrams(1.0, MeasureUnit.TBSP, rice)
        }
        assertEquals("rice", error.ingredientId)
        assertEquals(MeasureUnit.TBSP, error.unit)
    }

    @Test
    fun `missing piece weight fails loudly`() {
        assertFailsWith<MissingConversionException> {
            UnitConverter.toGrams(2.0, MeasureUnit.PCS, flour)
        }
    }

    @Test
    fun `grams convert to and from the pantry unit`() {
        assertEquals(120.0, UnitConverter.gramsToPantryUnit(120.0, rice))
        assertEquals(100.0, UnitConverter.gramsToPantryUnit(103.0, milk), DELTA)
        assertEquals(2.0, UnitConverter.gramsToPantryUnit(100.0, egg), DELTA)
        assertEquals(103.0, UnitConverter.pantryUnitToGrams(100.0, milk), DELTA)
        assertEquals(300.0, UnitConverter.pantryUnitToGrams(6.0, egg), DELTA)
    }

    private companion object {
        const val DELTA = 1e-9
    }
}
