package io.github.alfu32.workerml

data class Box(val minX: Float, val minY: Float, var maxX: Float, var maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    fun contains(point: Point): Boolean = point.x in minX..maxX && point.y in minY..maxY
    fun intersects(other: Box): Boolean =
        !(other.minX > maxX || other.maxX < minX || other.minY > maxY || other.maxY < minY)
}
