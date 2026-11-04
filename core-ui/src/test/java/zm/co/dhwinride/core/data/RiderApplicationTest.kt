package zm.co.dhwinride.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import zm.co.dhwinride.core.ui.readRiderDocument
import zm.co.dhwinride.core.ui.safeRiderDocumentFailureMessage

class RiderApplicationTest {
    @Test
    fun `application normalizes identifiers without granting access`() {
        val details = RiderApplicationDetails(" Honda ", " Ace ", 2024, " chg-123 ", " Blue ", " dl-123 ").normalized()
        assertEquals("Honda", details.motorcycleMake)
        assertEquals("CHG-123", details.motorcyclePlate)
        assertEquals("DL-123", details.drivingLicenceNumber)
        assertEquals(RiderAccessState.Pending, riderAccessState("passenger", null, "pending"))
        assertEquals(RiderAccessState.Pending, riderAccessState("rider", "pending", "approved"))
        assertEquals(RiderAccessState.Active, riderAccessState("rider", "approved", "approved"))
    }

    @Test
    fun `invalid motorcycle and licence details fail before signup`() {
        assertInvalid {
            RiderApplicationDetails("", "Ace", 2024, "CHG-123", "Blue", "DL-123").normalized()
        }
        assertInvalid {
            RiderApplicationDetails("Honda", "Ace", 1949, "CHG-123", "Blue", "DL-123").normalized()
        }
        assertInvalid {
            RiderApplicationDetails("Honda", "Ace", 2024, "CHG-123", "Blue", "").normalized()
        }
    }

    @Test
    fun `private document reads stop at the size limit`() {
        assertEquals(10, readRiderDocument(ByteArrayInputStream(ByteArray(10)), maxBytes = 10).size)
        assertInvalid { readRiderDocument(ByteArrayInputStream(ByteArray(11)), maxBytes = 10) }
    }

    @Test
    fun `default document limit matches the five megabyte private bucket`() {
        assertInvalid {
            readRiderDocument(ByteArrayInputStream(ByteArray(5 * 1024 * 1024 + 1)))
        }
    }

    @Test
    fun `document failures hide backend details`() {
        assertEquals(
            "The document could not be submitted. Check your connection and try again.",
            safeRiderDocumentFailureMessage("new row violates row-level security policy for path user-id/license.pdf"),
        )
        assertEquals(
            "Choose a document that is 5 MB or smaller.",
            safeRiderDocumentFailureMessage("Choose a document that is 5 MB or smaller."),
        )
    }

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid rider application details to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
