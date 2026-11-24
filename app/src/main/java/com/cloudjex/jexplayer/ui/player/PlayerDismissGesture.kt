package com.cloudjex.jexplayer.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Fracción de la altura del reproductor que hay que arrastrar para cerrarlo. */
internal const val PLAYER_DISMISS_DISTANCE_FRACTION = 0.25f

/** Velocidad hacia abajo, en dp/s, que cierra el reproductor al soltar. */
internal const val PLAYER_DISMISS_VELOCITY_DP_PER_SEC = 1_000f

private const val CLOSE_ANIMATION_MS = 200

/**
 * Progreso del morph: 0 = reproductor completo, 1 = integrado en el mini-player.
 */
internal fun playerDismissProgress(offsetPx: Float, heightPx: Int): Float =
    if (heightPx <= 0) 0f else (offsetPx / heightPx).coerceIn(0f, 1f)

/** Barra superior, textos, controles y waveform se apagan durante el arrastre. */
internal fun playerContentAlpha(progress: Float): Float =
    1f - smoothstep(0.05f, 0.35f, progress)

/**
 * Portada del morph: se mantiene opaca hasta que el mini-player ya la relevó,
 * para que el cruce ocurra siempre a la misma posición.
 */
internal fun playerArtworkAlpha(progress: Float): Float =
    1f - smoothstep(0.85f, 0.95f, progress)

/** Fondo del reproductor: deja ver la app y el mini-player cada vez más. */
internal fun playerBackgroundAlpha(progress: Float): Float =
    1f - smoothstep(0.25f, 0.85f, progress)

/** El mini-player aparece a medida que el reproductor se integra en él. */
internal fun miniPlayerAlpha(progress: Float): Float = smoothstep(0.75f, 0.95f, progress)

/**
 * Avance de la portada hacia el mini-player. Llega a su destino al 75 % para
 * que el cruce final ocurra con las dos portadas ya superpuestas.
 */
internal fun playerMorphTravel(progress: Float): Float =
    (progress / 0.75f).coerceIn(0f, 1f)

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Decide si soltar el panel debe cerrar el reproductor.
 *
 * Los umbrales son inclusivos: alcanzar exactamente el 25 % o la velocidad
 * límite cierra. El umbral de velocidad llega ya en píxeles por segundo porque
 * convertir dp a px depende de la densidad del dispositivo; así la función sigue
 * siendo pura y testeable en JVM.
 */
internal fun shouldDismissPlayer(
    offsetPx: Float,
    velocityPxPerSec: Float,
    heightPx: Float,
    velocityThresholdPxPerSec: Float
): Boolean {
    if (heightPx <= 0f) return false
    // Un gesto que termina subiendo pide quedarse; nunca cierra.
    if (velocityPxPerSec < 0f) return false
    if (velocityPxPerSec >= velocityThresholdPxPerSec) return true
    return offsetPx >= heightPx * PLAYER_DISMISS_DISTANCE_FRACTION
}

/**
 * Estado del panel de cierre: cuánto se ha separado el reproductor y cuánto mide.
 *
 * El offset se actualiza de forma síncrona desde el gesto (sin una corrutina por
 * frame) y solo se anima al soltar. Se lee desde `graphicsLayer`, no desde la
 * composición, para no recomponer el reproductor en cada desplazamiento.
 */
@Stable
internal class PlayerDismissState(
    private val scope: CoroutineScope,
    private val onDismiss: () -> Unit
) {
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Altura disponible del reproductor; la fija `onSizeChanged`. */
    var heightPx by mutableIntStateOf(0)

    private var settleJob: Job? = null
    private var isClosing = false

    /** Deja el panel como recién abierto; se llama al abrir el reproductor. */
    fun reset() {
        settleJob?.cancel()
        settleJob = null
        isClosing = false
        offsetPx = 0f
    }

    /**
     * Mueve el panel y devuelve el delta que el gesto consumió.
     *
     * Hacia abajo consumimos todo: debajo del reproductor no hay nada que hacer
     * scroll y no queremos que aparezca el estiramiento. Hacia arriba
     * consumimos solo hasta devolverlo a su sitio; el resto vuelve a la lista.
     */
    fun dragBy(deltaPx: Float): Float {
        // Un dedo nuevo cancela un cierre en curso y devuelve el control al gesto.
        isClosing = false
        settleJob?.cancel()
        // Antes de medir no hay nada que desplazar: que el gesto siga su curso.
        if (heightPx <= 0) return 0f
        val before = offsetPx
        if (deltaPx > 0f) {
            offsetPx = (before + deltaPx).coerceAtMost(heightPx.toFloat())
            return deltaPx
        }
        if (before <= 0f) return 0f
        offsetPx = (before + deltaPx).coerceAtLeast(0f)
        return offsetPx - before
    }

    /** Se soltó el dedo: cierra o vuelve suavemente a su sitio. */
    fun release(velocityPxPerSec: Float, velocityThresholdPxPerSec: Float) {
        val start = offsetPx
        settleJob?.cancel()
        if (shouldDismissPlayer(
                start,
                velocityPxPerSec,
                heightPx.toFloat(),
                velocityThresholdPxPerSec
            )
        ) {
            isClosing = true
            settleJob = scope.launch {
                animate(
                    start,
                    heightPx.toFloat(),
                    animationSpec = tween(CLOSE_ANIMATION_MS)
                ) { value, _ ->
                    offsetPx = value
                }
                // Solo se retira el overlay cuando el panel ya está fuera de
                // pantalla: así no hay corte ni fondo vacío a mitad de gesto.
                onDismiss()
            }
        } else if (start > 0f) {
            settleJob = scope.launch {
                animate(
                    start,
                    0f,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMedium
                    )
                ) { value, _ ->
                    offsetPx = value
                }
            }
        }
    }

    /**
     * Cierre pedido con la flecha o con atrás: misma salida que el gesto, sin
     * duplicar la animación si ya hay un cierre en curso.
     */
    fun close() {
        if (isClosing) return
        val height = heightPx.toFloat()
        if (height <= 0f) {
            onDismiss()
            return
        }
        isClosing = true
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(
                offsetPx,
                height,
                animationSpec = tween(CLOSE_ANIMATION_MS)
            ) { value, _ ->
                offsetPx = value
            }
            onDismiss()
        }
    }
}

@Composable
internal fun rememberPlayerDismissState(onDismiss: () -> Unit): PlayerDismissState {
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    return remember(scope) { PlayerDismissState(scope) { currentOnDismiss() } }
}

@Composable
internal fun rememberPlayerDismissConnection(
    state: PlayerDismissState,
    scrollState: ScrollState
): NestedScrollConnection {
    val density = LocalDensity.current
    return remember(state, scrollState, density) {
        PlayerDismissNestedScrollConnection(
            state = state,
            isAtTop = { scrollState.value == 0 },
            velocityThresholdPxPerSec = with(density) {
                PLAYER_DISMISS_VELOCITY_DP_PER_SEC.dp.toPx()
            }
        )
    }
}

/**
 * Traduce el scroll del contenido en el gesto de cierre.
 *
 * Va colgado de la columna que contiene el contenido normal del reproductor, no
 * de la raíz: así el `LazyColumn` de las letras nunca llega hasta aquí y su
 * desplazamiento queda intacto.
 */
internal class PlayerDismissNestedScrollConnection(
    private val state: PlayerDismissState,
    private val isAtTop: () -> Boolean,
    private val velocityThresholdPxPerSec: Float
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        return consume(available.y)
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        return consume(available.y)
    }

    private fun consume(dy: Float): Offset {
        if (dy == 0f) return Offset.Zero
        // Con el panel ya desplazado el gesto es suyo en ambas direcciones.
        if (state.offsetPx > 0f) return Offset(0f, state.dragBy(dy))
        if (dy > 0f && isAtTop()) return Offset(0f, state.dragBy(dy))
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val dy = available.y
        if (state.offsetPx <= 0f && (dy <= 0f || !isAtTop())) {
            // Fling normal del contenido: no se toca.
            return Velocity.Zero
        }
        // ponytail: sin onPostFling; un fling que llega al tope con inercia no
        // continúa en el cierre (hace falta un segundo gesto). Añadirlo si el
        // uso real lo pide.
        state.release(dy, velocityThresholdPxPerSec)
        return available
    }
}
