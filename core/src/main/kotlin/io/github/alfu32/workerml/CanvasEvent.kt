package io.github.alfu32.workerml

// --- Canvas Component ---
data class CanvasEvent(
    val drawablesUnderPointer: List<Drawable>,
    val viewport: Box,
    val screenPoint: Point,
    val modelPoint: Point,
    val type: String
) {
}
