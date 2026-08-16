package com.animal.battle.ui

import android.graphics.RectF
import kotlin.math.sqrt

/** 虚拟摇杆：把触摸转换为归一化移动输入（-1 ~ 1） */
class Joystick {
    var baseX = 0f
    var baseY = 0f
    var baseRadius = 90f
    var knobX = 0f
    var knobY = 0f
    var knobRadius = 42f
    var active = false
    var pointerId = -1
    var inputX = 0f
    var inputY = 0f

    fun setBase(x: Float, y: Float) {
        baseX = x
        baseY = y
        knobX = x
        knobY = y
    }

    fun begin(x: Float, y: Float, id: Int): Boolean {
        val dx = x - baseX
        val dy = y - baseY
        val r = baseRadius * 1.7f
        if (dx * dx + dy * dy <= r * r) {
            active = true
            pointerId = id
            update(x, y)
            return true
        }
        return false
    }

    fun update(x: Float, y: Float) {
        var dx = x - baseX
        var dy = y - baseY
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        if (d > baseRadius) {
            dx = dx / d * baseRadius
            dy = dy / d * baseRadius
        }
        knobX = baseX + dx
        knobY = baseY + dy
        inputX = dx / baseRadius
        inputY = dy / baseRadius
    }

    fun end(id: Int) {
        if (id == pointerId) {
            active = false
            pointerId = -1
            knobX = baseX
            knobY = baseY
            inputX = 0f
            inputY = 0f
        }
    }
}

/** 通用 UI 按钮 */
class UIButton(
    val id: String,
    val rect: RectF,
    val text: String,
    val subtext: String = "",
    val enabled: Boolean = true,
    val color: Int = 0xFF5C9E31.toInt(),
) {
    fun hit(x: Float, y: Float): Boolean = rect.contains(x, y)
}
