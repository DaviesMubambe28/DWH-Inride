package zm.co.dhwinride.core.push

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushDeliveryGateTest {
    @Test
    fun `a repeated provider event is suppressed`() {
        assertFalse(shouldDisplayPush("event-1", setOf("event-1")))
    }

    @Test
    fun `new and provider messages without event ids remain displayable`() {
        assertTrue(shouldDisplayPush("event-2", setOf("event-1")))
        assertTrue(shouldDisplayPush(null, setOf("event-1")))
    }

    @Test
    fun `local delivery is disabled by default`() {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        every { context.getSharedPreferences("dhw_push", Context.MODE_PRIVATE) } returns preferences
        every { preferences.getBoolean("push_session_active", false) } returns false

        assertFalse(FcmTokenRegistrar.localDeliveryEnabled(context))
    }

    @Test
    fun `sign out gate disables delivery before remote cleanup`() {
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences("dhw_push", Context.MODE_PRIVATE) } returns preferences
        every { preferences.edit() } returns editor
        every { editor.putBoolean("push_session_active", false) } returns editor
        every { editor.apply() } returns Unit

        FcmTokenRegistrar.disableLocalDelivery(context)

        verify(exactly = 1) { editor.putBoolean("push_session_active", false) }
        verify(exactly = 1) { editor.apply() }
    }
}
