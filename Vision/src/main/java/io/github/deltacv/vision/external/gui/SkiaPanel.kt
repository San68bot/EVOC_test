package io.github.deltacv.vision.external.gui

import org.jetbrains.skiko.ClipComponent
import org.jetbrains.skiko.SkiaLayer
import java.awt.Color
import java.awt.Component
import javax.swing.JLayeredPane

class SkiaPanel(private val layer: SkiaLayer) : JLayeredPane() {

    init {
        layout = null
        background = Color.white
    }

    override fun add(component: Component): Component {
        layer.clipComponents.add(ClipComponent(component))
        return super.add(component, Integer.valueOf(0))
    }

    override fun doLayout() {
        layer.setBounds(0, 0, width, height)
    }

    override fun addNotify() {
        super.addNotify()
        super.add(layer, Integer.valueOf(10))
    }

    override fun removeNotify() {
        layer.dispose()
        super.removeNotify()
    }
}