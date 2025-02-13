package io.github.alfu32.workerml

// --- Accessory classes ---
data class Point(var x: Float, var y: Float){
    public fun add(dx:Float, dy:Float): Point {
        return Point(x + dx, y + dy)
    }
}
