package io.github.alfu32.workerml

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.atan2

// --- Drawable interface and Model ---
interface Drawable {
    fun draw(model: Model, canvas: CanvasComponent, batch: Batch,)
    fun draw(model: Model, canvas: CanvasComponent, shapeRenderer: ShapeRenderer)
    fun getBoundingBox(): Box
}

class LinkDrawable(var from: BoxDrawable, var to: BoxDrawable) : Drawable {
    fun copy(): LinkDrawable = LinkDrawable(from, to)
    override fun getBoundingBox(): Box {
        val fromBox = from.getBoundingBox()
        val toBox = to.getBoundingBox()
        val minX = minOf(fromBox.minX, toBox.minX)
        val minY = minOf(fromBox.minY, toBox.minY)
        val maxX = maxOf(fromBox.maxX, toBox.maxX)
        val maxY = maxOf(fromBox.maxY, toBox.maxY)
        return Box(minX, minY, maxX, maxY)
    }
    fun getDirection() : Float {
        val p1=from.position
        val p2=to.position
        return (atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble()) * 180f - Math.PI).toFloat()
    }
    override fun draw(model: Model, canvas: CanvasComponent, batch: Batch,) {
        val linkOrderFrom = from.getOrderFrom(this)
        val linkOrderTo = to.getOrderTo(this)
        println(this.getDirection())
        val fromCenter = Point(from.position.x + from.size.x, 10f + from.position.y - linkOrderFrom * 10f)
        val toCenter = Point(to.position.x, 10f + to.position.y - linkOrderTo * 10f)
        batch.color = Color.DARK_GRAY
        Assets.font.draw(batch, "${to.metadata.name} -> ${to.metadata.name}", toCenter.x, toCenter.y)
        Assets.font.draw(batch, "${getDirection()} DEG", fromCenter.x, fromCenter.y)
    }
    override fun draw(model: Model, canvas: CanvasComponent, shapeRenderer: ShapeRenderer) {
        // Draw a red line between the centers of the two boxes.
        val linkOrderFrom = from.getOrderFrom(this)
        val linkOrderTo = to.getOrderTo(this)
        println(this.getDirection())
        val fromCenter = Point(from.position.x + from.size.x, 10f + from.position.y - linkOrderFrom * 10f)
        val toCenter = Point(to.position.x, 10f + to.position.y - linkOrderTo * 10f)
        shapeRenderer.color = Color.RED
        shapeRenderer.line(fromCenter.x, fromCenter.y, toCenter.x, toCenter.y)
    }
}

class BoxDrawable(var position: Point, val size: Point = Point(150f, 50f), val metadata: BoxMetadata) : Drawable {
    var links:MutableList<LinkDrawable> = mutableListOf()
    var offset_y = 0f
    override fun getBoundingBox() = Box(position.x, position.y-offset_y, position.x + size.x, position.y + size.y+offset_y)
    override fun draw(model: Model, canvas: CanvasComponent, batch: Batch) {
        batch.color = Color.DARK_GRAY
        Assets.font.draw(batch, metadata.name, position.x + 5, position.y + size.y - 5)
    }
    override fun draw(model: Model, canvas: CanvasComponent, shapeRenderer: ShapeRenderer) {
        // Draw a blue rectangle to represent the box.
        /// batch.color = Color.BLUE
        /// batch.draw(Assets.whitePixel, position.x, position.y, size, size)
        /// batch.color = Color.DARK_GRAY
        /// batch.draw(Assets.whitePixel, position.x+2, position.y+2, size-4, size-4)

        // Draw the metadata text (e.g., the name) inside the box.
        shapeRenderer.color= Color.WHITE
        shapeRenderer.rect(position.x, position.y-offset_y, size.x, size.y+offset_y)
    }

    fun addLink(link: LinkDrawable) {
        links.add(link)
        links.sortBy { it.getDirection() }
        offset_y= Math.max(links.filter { it.from == this }.size,links.filter { it.to == this }.size) * 10f
    }
    fun removeLink(link: LinkDrawable) {
        links.remove(link)
        links.sortBy { it.getDirection() }
        offset_y= Math.max(links.filter { it.from == this }.size,links.filter { it.to == this }.size) * 10f
    }

    fun getOrderFrom(linkDrawable: LinkDrawable): Int {
        return links.filter { it.from == this }.indexOf(linkDrawable)
    }
    fun getOrderTo(linkDrawable: LinkDrawable): Int {
        return links.filter { it.to == this }.indexOf(linkDrawable)
    }
}
