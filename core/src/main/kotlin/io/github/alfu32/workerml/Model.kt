package io.github.alfu32.workerml

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

class Model : Drawable {
    private val drawables = mutableListOf<Drawable>()
    fun addDrawable(drawable: Drawable) { drawables.add(drawable) }
    fun removeDrawable(drawable: Drawable) { drawables.remove(drawable) }
    fun getDrawablesUnderPoint(point: Point): List<Drawable> = drawables.filter { it.getBoundingBox().contains(point) }
    override fun draw(model: Model, canvas: CanvasComponent, batch: Batch) {
        for (drawable in drawables) {
            if (drawable.getBoundingBox().intersects(canvas.viewport)) {
                drawable.draw(this, canvas, batch)
            }
        }
    }
    override fun draw(model: Model, canvas: CanvasComponent, shapeRenderer: ShapeRenderer) {
        for (drawable in drawables) {
            if (drawable.getBoundingBox().intersects(canvas.viewport)) {
                drawable.draw(this, canvas,shapeRenderer)
            }
        }
    }
    override fun getBoundingBox(): Box {
        if (drawables.isEmpty()) return Box(0f, 0f, 0f, 0f)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (drawable in drawables) {
            val box = drawable.getBoundingBox()
            if (box.minX < minX) minX = box.minX
            if (box.minY < minY) minY = box.minY
            if (box.maxX > maxX) maxX = box.maxX
            if (box.maxY > maxY) maxY = box.maxY
        }
        return Box(minX, minY, maxX, maxY)
    }
}
