package org.newnewpipe.app.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NewNewPipeThemeTest {

    // region light

    @Test
    fun `light scheme usa primary indaco 3D5AFE`() {
        assertEquals(Color(0xFF3D5AFE), IndacoLightColorScheme.primary)
        assertEquals(Color.White, IndacoLightColorScheme.onPrimary)
        assertEquals(Color(0xFF00105C), IndacoLightColorScheme.onPrimaryContainer)
    }

    @Test
    fun `light scheme ha fondo chiaro e testo scuro`() {
        assertEquals(Color(0xFFFBF8FF), IndacoLightColorScheme.background)
        assertEquals(Color(0xFFFBF8FF), IndacoLightColorScheme.surface)
        assertEquals(Color(0xFF1B1B21), IndacoLightColorScheme.onBackground)
        assertEquals(Color(0xFF1B1B21), IndacoLightColorScheme.onSurface)
        assertNotEquals(IndacoLightColorScheme.background, IndacoLightColorScheme.onSurface)
    }

    // region dark

    @Test
    fun `dark scheme usa primary indaco chiaro su fondo scuro`() {
        assertEquals(Color(0xFFB9C1FF), IndacoDarkColorScheme.primary)
        assertEquals(Color(0xFF00208B), IndacoDarkColorScheme.onPrimary)
        assertEquals(Color(0xFF1B1B21), IndacoDarkColorScheme.background)
        assertEquals(Color(0xFF1B1B21), IndacoDarkColorScheme.surface)
        assertEquals(Color(0xFFE4E1E9), IndacoDarkColorScheme.onSurface)
        assertNotEquals(IndacoLightColorScheme, IndacoDarkColorScheme)
    }

    @Test
    fun `dark scheme differisce dalla variante black`() {
        assertNotEquals(IndacoDarkColorScheme.background, IndacoBlackColorScheme.background)
    }

    // region black

    @Test
    fun `black scheme usa nero puro per background e surface`() {
        assertEquals(Color.Black, IndacoBlackColorScheme.background)
        assertEquals(Color.Black, IndacoBlackColorScheme.surface)
        assertEquals(Color.White, IndacoBlackColorScheme.onBackground)
        assertEquals(Color.White, IndacoBlackColorScheme.onSurface)
    }

    @Test
    fun `black scheme mantiene la palette indaco dark e superficie quasi nera`() {
        // variante dark: primary in tonalità chiara, come IndacoDarkColorScheme
        assertEquals(IndacoDarkColorScheme.primary, IndacoBlackColorScheme.primary)
        assertEquals(Color(0xFF1A1A1A), IndacoBlackColorScheme.surfaceVariant)
        assertEquals(Color(0xFF3A3A3A), IndacoBlackColorScheme.outline)
    }
}
