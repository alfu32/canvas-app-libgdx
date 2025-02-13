package io.github.alfu32.workerml

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTextButton
import ktx.app.KtxGame
import ktx.app.KtxScreen
import ktx.async.KtxAsync
import kotlin.math.atan2

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

    fun draw(batch: Batch?, parentAlpha: Float,shapeRenderer:ShapeRenderer) {
        batch?.let { b ->
            // Draw a dark gray background so the canvas is visible.
            b.color = Color.DARK_GRAY
            b.draw(Assets.whitePixel, x, y, width, height)
            b.color = Color.WHITE
            super.draw(b, parentAlpha)
            model.draw(model, this, b)
        }
    }
    fun draw(shapeRenderer:ShapeRenderer?, parentAlpha: Float) {
        shapeRenderer?.let { b ->
            model.draw(model, this, shapeRenderer)
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

fun calculateAngle(p1: Point, p2: Point): Float {
    return (atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble()) * 180f - Math.PI).toFloat()
}

// --- LogoScreen that integrates canvas, model, and UI ---
class CanvasScreen : KtxScreen {
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer().apply {
        projectionMatrix = batch.projectionMatrix
    }
    private val stage = Stage(ScreenViewport(), batch)
    private val uiStage = Stage(ScreenViewport(), batch)

    private val model = Model()
    private val canvas = CanvasComponent(model)

    // Modes for user actions.
    enum class Mode { IDLE, ADD_BOX, ADD_LINK }
    private var mode: Mode = Mode.IDLE
    private var linkSource: BoxDrawable = BoxDrawable(Point(0f,0f),Point(5f,5f),BoxMetadata("BoxSource", "Content for box source"))
    private var linkTarget: BoxDrawable = BoxDrawable(Point(0f,0f),Point(5f,5f),BoxMetadata("BoxTarget", "Content for box target"))
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
                    val newBox = BoxDrawable(event.modelPoint.add(-25f,-25f), metadata =  metadata)
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
                            val cp = link.copy()
                            // cp.from=link.from
                            // cp.to=box
                            model.addDrawable(cp)
                            cp.from.addLink(cp)
                            cp.to.addLink(cp)
                            // mode = Mode.IDLE
                            link.from=linkSource
                            link.to=linkTarget
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

        shapeRenderer.projectionMatrix = batch.projectionMatrix
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        canvas.model.draw(model,canvas,shapeRenderer)
        link.draw(model,canvas,shapeRenderer)
        shapeRenderer.end()

        batch.begin()
        canvas.model.draw(model,canvas,batch)
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
