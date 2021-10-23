package io.github.deltacv.easyvision.node

import io.github.deltacv.easyvision.EasyVision
import io.github.deltacv.easyvision.gui.style.hexColor
import io.github.deltacv.easyvision.gui.style.rgbaColor

enum class Category(val properName: String,
                    val color: Int = EasyVision.imnodesStyle.titleBar,
                    val colorSelected: Int = EasyVision.imnodesStyle.titleBarHovered) {

    FLOW("Pipeline Flow",
        hexColor("#00838f"), // material cyan
        hexColor("#0097a7")),

    CODE("Coding"),
    HIGH_LEVEL_CV("High Level"),

    COLOR_OP("Basic Color Operations",
        hexColor("#ff6f00"), // material amber
        hexColor("#ff8f00")),

    SHAPE_DET("Basic Shape Detection",
        hexColor("#3949ab"), // material indigo
        hexColor("#3f51b5")),

    OVERLAY("Overlay Drawing",
        hexColor("#00897b"), // material cyan
        hexColor("#009688")),

    MATH("Math"),
    MISC("Miscellaneous")

}