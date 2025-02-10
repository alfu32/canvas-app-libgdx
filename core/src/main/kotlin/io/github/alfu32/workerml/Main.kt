package io.github.alfu32.workerml

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTextButton
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.async.KtxAsync

// A simple asset holder to create a 1x1 white pixel texture.
object Assets {
    val whitePixel: Texture by lazy {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        pixmap.setColor(Color.WHITE)
        pixmap.fill()
        val texture = Texture(pixmap)
        pixmap.dispose()
        texture
    }
}

// --- Accessory classes ---
data class Point(val x: Float, val y: Float){
    public fun add(dx:Float, dy:Float):Point{
        return Point(x+dx,y+dy)
    }
}
data class Box(val minX: Float, val minY: Float, var maxX: Float, var maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    fun contains(point: Point): Boolean = point.x in minX..maxX && point.y in minY..maxY
    fun intersects(other: Box): Boolean =
        !(other.minX > maxX || other.maxX < minX || other.minY > maxY || other.maxY < minY)
}

// --- Drawable interface and Model ---
interface Drawable {
    fun draw(model: Model, canvas: CanvasComponent, batch: Batch)
    fun getBoundingBox(): Box
}

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

// --- Canvas Component ---
data class CanvasEvent(
    val drawablesUnderPointer: List<Drawable>,
    val viewport: Box,
    val screenPoint: Point,
    val modelPoint: Point,
    val type: String
) {
}

class CanvasComponent(val model: Model) : Actor() {

    // The current viewport in model coordinates.
    var viewport: Box = Box(0f,0f,640f,640f,)

    // Event callbacks.
    var onPointerDragged: ((CanvasEvent) -> Unit)? = null
    var onPointerDown: ((CanvasEvent) -> Unit)? = null
    var onPointerUp: ((CanvasEvent) -> Unit)? = null
    var onMouseMove: ((CanvasEvent) -> Unit)? = null
    var onZoomFinished: ((CanvasEvent) -> Unit)? = null

    init {
        addListener(object : InputListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                val screenPoint = Point(x, y)
                val modelPoint = screenToModel(screenPoint)
                val underDrawables = model.getDrawablesUnderPoint(modelPoint)
                onPointerDown?.invoke(CanvasEvent(underDrawables, viewport, screenPoint, modelPoint,"pointer-down"))
                return true
            }
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                val screenPoint = Point(x, y)
                val modelPoint = screenToModel(screenPoint)
                val underDrawables = model.getDrawablesUnderPoint(modelPoint)
                onPointerUp?.invoke(CanvasEvent(underDrawables, viewport, screenPoint, modelPoint,"pointer-up"))
            }

            override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val screenPoint = Point(x, y)
                val modelPoint = screenToModel(screenPoint)
                val underDrawables = model.getDrawablesUnderPoint(modelPoint)
                onPointerDragged?.invoke(CanvasEvent(underDrawables, viewport, screenPoint, modelPoint,"pointer-dragged"))
            }
            override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
                val screenPoint = Point(x, y)
                val modelPoint = screenToModel(screenPoint)
                val underDrawables = model.getDrawablesUnderPoint(modelPoint)
                onMouseMove?.invoke(CanvasEvent(underDrawables, viewport, screenPoint, modelPoint,"mouse-move"))
                return true
            }
            override fun scrolled(event: InputEvent?, x: Float, y: Float,amountX:Float,amountY:Float): Boolean {
                val screenPoint = Point(x, y)
                val modelPoint = screenToModel(screenPoint)
                val underDrawables = model.getDrawablesUnderPoint(modelPoint)
                zoomAboutPoint(Math.signum(amountY) * 1.2f,modelPoint)
                return true
            }
        })
    }

    override fun setSize(width: Float, height: Float) {
        super.setSize(width, height)
        val (max,may)=listOf(viewport.minX+width,viewport.minY+height)
        viewport.maxX=max
        viewport.maxY=may
    }

    // Converts a screen (local) point into model coordinates.
    fun screenToModel(screenPoint: Point): Point {
        val localX = screenPoint.x
        val localY = screenPoint.y
        val modelX = viewport.minX + (localX / width) * viewport.width
        val modelY = viewport.minY + (localY / height) * viewport.height
        return Point(modelX, modelY)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        batch?.let { b ->
            // Draw a dark gray background so the canvas is visible.
            b.color = Color.DARK_GRAY
            b.draw(Assets.whitePixel, x, y, width, height)
            b.color = Color.WHITE
            super.draw(b, parentAlpha)
            model.draw(model, this, b)
        }
    }

    // Zooms the viewport about a given screen point.
    fun zoomAboutPoint(zoomFactor: Float, focalScreenPoint: Point) {
        val focalModelPoint = screenToModel(focalScreenPoint)
        val newWidth = viewport.width / zoomFactor
        val newHeight = viewport.height / zoomFactor
        val newMinX = focalModelPoint.x - (focalModelPoint.x - viewport.minX) / zoomFactor
        val newMinY = focalModelPoint.y - (focalModelPoint.y - viewport.minY) / zoomFactor
        viewport = Box(newMinX, newMinY, newMinX + newWidth, newMinY + newHeight)
        val underDrawables = model.getDrawablesUnderPoint(focalModelPoint)
        onZoomFinished?.invoke(CanvasEvent(underDrawables, viewport, focalScreenPoint, focalModelPoint,"zoom-finished"))
    }
}

// --- Box and Link Drawables with metadata ---
data class BoxMetadata(val name: String, val textContent: String)

class BoxDrawable(var position: Point, val size: Float = 50f, val metadata: BoxMetadata) : Drawable {
    override fun getBoundingBox() = Box(position.x, position.y, position.x + size, position.y + size)
    override fun draw(model: Model, canvas: CanvasComponent, batch: Batch) {
        // Draw a blue rectangle to represent the box.
        batch.color = Color.BLUE
        batch.draw(Assets.whitePixel, position.x, position.y, size, size)
        batch.color = Color.WHITE
    }
}

class LinkDrawable(var from: BoxDrawable, var to: BoxDrawable) : Drawable {
    fun copy():LinkDrawable = LinkDrawable(from,to)
    override fun getBoundingBox(): Box {
        val fromBox = from.getBoundingBox()
        val toBox = to.getBoundingBox()
        val minX = minOf(fromBox.minX, toBox.minX)
        val minY = minOf(fromBox.minY, toBox.minY)
        val maxX = maxOf(fromBox.maxX, toBox.maxX)
        val maxY = maxOf(fromBox.maxY, toBox.maxY)
        return Box(minX, minY, maxX, maxY)
    }
    override fun draw(model: Model, canvas: CanvasComponent, batch: Batch) {
        // Draw a red line between the centers of the two boxes.
        val fromCenter = Point(from.position.x + from.size / 2, from.position.y + from.size / 2)
        val toCenter = Point(to.position.x + to.size / 2, to.position.y + to.size / 2)
        batch.color = Color.RED
        val dx = toCenter.x - fromCenter.x
        val dy = toCenter.y - fromCenter.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        val angle = kotlin.math.atan2(dy, dx) * 57.2958f // radians to degrees
        // Draw a rotated rectangle (using the white pixel tinted red) to simulate a line.
        batch.draw(Assets.whitePixel, fromCenter.x, fromCenter.y, 0f, 0.5f, length, 2f, 1f, 1f, angle, 0, 0, 1, 1, false, false)
        batch.color = Color.WHITE
    }
}

// --- LogoScreen that integrates canvas, model, and UI ---
class CanvasScreen : KtxScreen {
    private val batch = SpriteBatch()
    private val batch2 = SpriteBatch()
    private val stage = Stage(ScreenViewport(), batch)
    private val uiStage = Stage(ScreenViewport(), batch)

    private val model = Model()
    private val canvas = CanvasComponent(model)

    // Modes for user actions.
    enum class Mode { IDLE, ADD_BOX, ADD_LINK }
    private var mode: Mode = Mode.IDLE
    private var linkSource: BoxDrawable = BoxDrawable(Point(0f,0f),5f,BoxMetadata("BoxSource", "Content for box source"))
    private var linkTarget: BoxDrawable = BoxDrawable(Point(0f,0f),5f,BoxMetadata("BoxTarget", "Content for box target"))
    private var link:LinkDrawable = LinkDrawable(linkSource, linkTarget)
    private var linkStep=0
    private var boxCounter = 1
    private val json = Json()
    private lateinit var statusBar:VisLabel
    private lateinit var table:Table

        private fun addStatus(message:Any) {
        when(message) {
            is String -> print("$message ")
            else -> print("${message.javaClass.name.split(".").last()}:${json.toJson(message)} ")
        }
    }
    private fun endStatus(message:Any) {
        when(message) {
            is String -> println(message)
            else -> println("${message.javaClass.name.split(".").last()}:${json.toJson(message)}")
        }
    }

    override fun show() {
        VisUI.load()
        statusBar = VisLabel("Status:").apply {}
        // Set up the canvas to cover the whole screen.
        canvas.setSize(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        stage.addActor(canvas)

        // Process canvas pointer events.
        canvas.onPointerDragged = { event ->
            addStatus(event.type)
            addStatus(event.screenPoint)
            endStatus(event.modelPoint)
        }
        // Process canvas pointer events.
        canvas.onMouseMove = {event ->
            addStatus(event.type)
            addStatus(event.screenPoint)
            endStatus(event.modelPoint)
            linkSource.position=event.modelPoint
            linkTarget.position=event.modelPoint
        }
        // Process canvas pointer events.
        canvas.onZoomFinished = {event ->
            addStatus(event.type)
            addStatus(event.screenPoint)
            addStatus(event.modelPoint)
            endStatus(event.viewport)
        }
        canvas.onPointerDown = { event ->
            when (mode) {
                Mode.ADD_BOX -> {
                    // Create a new box at the clicked (model) location.
                    val metadata = BoxMetadata("Box $boxCounter", "Content for box $boxCounter")
                    boxCounter++
                    val newBox = BoxDrawable(event.modelPoint.add(-25f,-25f), 50f, metadata)
                    addStatus(event.type)
                    addStatus(event.screenPoint)
                    endStatus(event.modelPoint)
                    model.addDrawable(newBox)
                    // mode = Mode.IDLE
                }
                Mode.ADD_LINK -> {
                    // In link mode, select a box on the first click and create a link on the second.
                    val box = event.drawablesUnderPointer.firstOrNull { it is BoxDrawable } as? BoxDrawable
                    if (box != null) {
                        if (linkStep == 0) {
                            link.from=linkSource
                            link.to=linkTarget
                            link.from = box
                            linkStep=1
                        } else {
                            link.to=box
                            model.addDrawable(link.copy())
                            // mode = Mode.IDLE
                            linkStep=0
                        }
                    }
                }
                else -> { /* Other interactions can be handled here. */ }
            }
        }

        // Create a UI table that does NOT cover the entire screen.
        table = Table()
        // Instead of filling the entire stage, pack the table to its contents.
        table.add(VisTextButton("Add Box").apply {
            val button = this
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    endStatus("pick point to place box")
                    mode = if(mode==Mode.ADD_BOX) {
                        button.focusLost()
                        Mode.IDLE
                    } else {
                        Mode.ADD_BOX
                    }
                }
            })
        }).padRight(10f)
        table.add(VisTextButton("Link Boxes").apply {
            val button = this
            addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent?, actor: Actor?) {
                    endStatus("pick the first box to link")
                    mode = if(mode==Mode.ADD_LINK) {
                        button.focusLost()
                        Mode.IDLE
                    }else {
                        linkStep=0
                        Mode.ADD_LINK
                    }
                }
            })
        }).padRight(10f)
        table.add(statusBar).padRight(10f)
        table.pack()
        // Position the table at the top-left corner.
        table.setPosition(10f, Gdx.graphics.height - table.height - 10f)
        uiStage.addActor(table)

        // Use an InputMultiplexer so both UI and canvas receive events.
        // (Since the UI table now only occupies a small region, it won't block canvas clicks.)
        val multiplexer = com.badlogic.gdx.InputMultiplexer(uiStage, stage)
        Gdx.input.inputProcessor = multiplexer
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta)
        uiStage.act(delta)
        stage.draw()
        uiStage.draw()
        batch.begin()
        link.draw(model,canvas,batch)
        batch.end()
    }

    override fun resize(width: Int, height: Int) {
        endStatus("resized: { width: $width, height:$height }")
        stage.viewport.update(width, height, true)
        uiStage.viewport.update(width, height, true)
        canvas.setSize(width.toFloat(), height.toFloat())
        table.setPosition(10f, Gdx.graphics.height - table.height - 10f)
    }

    override fun hide() {
        dispose()
    }

    override fun dispose() {
        stage.dispose()
        uiStage.dispose()
        batch.dispose()
        VisUI.dispose()
    }
}

// --- Main game class wrapped in a KtxGame ---
class Main : KtxGame<KtxScreen>() {
    override fun create() {
        KtxAsync.initiate()
        val screen = CanvasScreen()
        addScreen(screen)
        setScreen<CanvasScreen>()
    }
}
