package io.github.alfu32.workerml

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont

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
    val font: BitmapFont by lazy { BitmapFont().apply {
        this.color = Color.WHITE
    } }
}
