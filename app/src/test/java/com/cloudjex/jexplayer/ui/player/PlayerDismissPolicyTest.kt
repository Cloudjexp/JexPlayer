package com.cloudjex.jexplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDismissPolicyTest {

    // 1000 px de alto; el umbral de distancia cae en 250 px y el de velocidad
    // se pasa explícitamente para no depender de la densidad del dispositivo.
    private val heightPx = 1_000f
    private val velocityThreshold = 2_750f

    private fun dismiss(
        offsetPx: Float,
        velocityPxPerSec: Float,
        height: Float = heightPx
    ) = shouldDismissPlayer(
        offsetPx = offsetPx,
        velocityPxPerSec = velocityPxPerSec,
        heightPx = height,
        velocityThresholdPxPerSec = velocityThreshold
    )

    @Test
    fun distanceThresholdDismisses() {
        assertTrue(dismiss(offsetPx = heightPx * 0.26f, velocityPxPerSec = 0f))
    }

    @Test
    fun belowDistanceThresholdDoesNotDismiss() {
        assertFalse(dismiss(offsetPx = heightPx * 0.24f, velocityPxPerSec = 0f))
    }

    @Test
    fun exactDistanceThresholdDismisses() {
        assertTrue(dismiss(offsetPx = heightPx * PLAYER_DISMISS_DISTANCE_FRACTION, velocityPxPerSec = 0f))
    }

    @Test
    fun velocityThresholdDismisses() {
        assertTrue(dismiss(offsetPx = 0f, velocityPxPerSec = velocityThreshold))
    }

    @Test
    fun belowVelocityThresholdDoesNotDismiss() {
        assertFalse(dismiss(offsetPx = 0f, velocityPxPerSec = velocityThreshold - 1f))
    }

    @Test
    fun bothThresholdsDismiss() {
        assertTrue(
            dismiss(
                offsetPx = heightPx * 0.5f,
                velocityPxPerSec = velocityThreshold * 2f
            )
        )
    }

    @Test
    fun noThresholdDoesNotDismiss() {
        assertFalse(
            dismiss(
                offsetPx = heightPx * 0.1f,
                velocityPxPerSec = velocityThreshold / 2f
            )
        )
    }

    @Test
    fun negativeVelocityDoesNotDismiss() {
        assertFalse(dismiss(offsetPx = heightPx * 0.1f, velocityPxPerSec = -velocityThreshold))
    }

    @Test
    fun zeroHeightDoesNotDismiss() {
        assertFalse(dismiss(offsetPx = 500f, velocityPxPerSec = velocityThreshold * 10f, height = 0f))
    }

    @Test
    fun progressStaysWithinBounds() {
        assertEquals(0f, playerDismissProgress(offsetPx = -50f, heightPx = 800), 0.0001f)
        assertEquals(0.5f, playerDismissProgress(offsetPx = 400f, heightPx = 800), 0.0001f)
        assertEquals(1f, playerDismissProgress(offsetPx = 900f, heightPx = 800), 0.0001f)
        assertEquals(0f, playerDismissProgress(offsetPx = 400f, heightPx = 0), 0.0001f)
    }

    @Test
    fun contentFadesOutDuringTheMorph() {
        assertEquals(1f, playerContentAlpha(0f), 0.0001f)
        assertEquals(0f, playerContentAlpha(1f), 0.0001f)
        assertTrue(playerContentAlpha(0.5f) < playerContentAlpha(0.2f))
    }

    @Test
    fun artworkStaysOpaqueUntilTheMiniTakesOver() {
        assertEquals(1f, playerArtworkAlpha(0f), 0.0001f)
        assertEquals(1f, playerArtworkAlpha(0.8f), 0.0001f)
        assertEquals(0f, playerArtworkAlpha(1f), 0.0001f)
    }

    @Test
    fun artworkTravelReachesTheMiniBeforeTheHandoff() {
        // La portada llega al mini al 75 %: el cruce final ocurre ya alineado.
        assertEquals(0f, playerMorphTravel(0f), 0.0001f)
        assertEquals(0.5f, playerMorphTravel(0.375f), 0.0001f)
        assertEquals(1f, playerMorphTravel(0.75f), 0.0001f)
        assertEquals(1f, playerMorphTravel(1f), 0.0001f)
    }

    @Test
    fun backgroundFadesOut() {
        assertEquals(1f, playerBackgroundAlpha(0f), 0.0001f)
        assertEquals(0f, playerBackgroundAlpha(0.9f), 0.0001f)
    }

    @Test
    fun miniPlayerAppearsAtTheEnd() {
        assertEquals(0f, miniPlayerAlpha(0f), 0.0001f)
        assertEquals(0f, miniPlayerAlpha(0.7f), 0.0001f)
        assertEquals(1f, miniPlayerAlpha(1f), 0.0001f)
        assertTrue(miniPlayerAlpha(0.9f) > miniPlayerAlpha(0.8f))
    }

    @Test
    fun backdropIsZeroWithPlayerAndWithMini() {
        // Cero al cancelar (reproductor completo) y cero al llegar al mini:
        // el mini-player nunca queda desenfocado ni oscurecido.
        assertEquals(0f, playerBackdropEffect(0f), 0.0001f)
        assertEquals(0f, playerBackdropEffect(0.78f), 0.0001f)
        assertEquals(0f, playerBackdropEffect(1f), 0.0001f)
    }

    @Test
    fun backdropPeaksDuringTheMorph() {
        assertTrue(playerBackdropEffect(0.5f) > 0.9f)
        assertTrue(playerBackdropEffect(0.2f) < playerBackdropEffect(0.4f))
        assertTrue(playerBackdropEffect(0.7f) < playerBackdropEffect(0.5f))
    }
}
