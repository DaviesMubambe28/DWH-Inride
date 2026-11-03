package zm.co.dhwinride.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PilotPolicyTest {
    @Test
    fun `support contacts match the approved monitored channels`() {
        assertEquals("+260770001203", PilotPolicy.SUPPORT_PRIMARY)
        assertEquals("+260975461121", PilotPolicy.SUPPORT_SECONDARY)
        assertEquals("gershmukuka1@gmail.com", PilotPolicy.SUPPORT_EMAIL)
    }
}
