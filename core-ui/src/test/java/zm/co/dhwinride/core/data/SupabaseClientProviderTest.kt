package zm.co.dhwinride.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseClientProviderTest {

    @Test
    fun `placeholder credentials are rejected`() {
        assertFalse(
            SupabaseClientProvider.isConfigured(
                "https://your-project.supabase.co",
                "your-publishable-key",
            ),
        )
    }

    @Test
    fun `non-placeholder credentials enable the Supabase repositories`() {
        assertTrue(
            SupabaseClientProvider.isConfigured(
                "https://project-ref.supabase.co",
                "publishable-key",
            ),
        )
    }
}
