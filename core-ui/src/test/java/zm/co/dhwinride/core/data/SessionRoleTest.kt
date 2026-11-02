package zm.co.dhwinride.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRoleTest {
    @Test
    fun `matching app role is accepted`() {
        assertTrue(sessionRoleMatches("passenger", "passenger"))
        assertTrue(sessionRoleMatches("rider", "rider"))
    }

    @Test
    fun `cross app and administrative roles are rejected`() {
        assertFalse(sessionRoleMatches("rider", "passenger"))
        assertFalse(sessionRoleMatches("passenger", "rider"))
        assertFalse(sessionRoleMatches("admin", "passenger"))
        assertFalse(sessionRoleMatches("admin", "rider"))
    }
}
