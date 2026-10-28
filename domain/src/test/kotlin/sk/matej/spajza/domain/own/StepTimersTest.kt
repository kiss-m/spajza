package sk.matej.spajza.domain.own

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepTimersTest {
    @Test
    fun `waiting times in Slovak, Czech and English`() {
        assertEquals(1200, StepTimers.detect("Varte 20 minút, kým zmäknú."))
        assertEquals(600, StepTimers.detect("Duste 10 min."))
        assertEquals(300, StepTimers.detect("Vařte 5 minut."))
        assertEquals(3600, StepTimers.detect("Bake for 1 hour until golden."))
        assertEquals(5400, StepTimers.detect("Duste 1,5 hodiny pod pokrievkou."))
        assertEquals(1800, StepTimers.detect("Nechajte cesto odpočívať pol hodiny."))
        assertEquals(45, StepTimers.detect("Mixujte 45 sekúnd."))
    }

    @Test
    fun `ranges count to their end and the longest wait wins`() {
        assertEquals(1800, StepTimers.detect("Pečte 25–30 minút, kým nezozlatne."))
        assertEquals(1080, StepTimers.detect("Opekajte 2 minúty, potom prilejte vodu a varte 18 minút."))
    }

    @Test
    fun `steps without a wait have no timer`() {
        assertNull(StepTimers.detect("Cibuľu nakrájajte nadrobno."))
        assertNull(StepTimers.detect("Rúru predhrejte na 180 °C a plech vystelte papierom."))
        assertNull(StepTimers.detect("Nakrájajte na 2 cm kocky."))
    }
}
