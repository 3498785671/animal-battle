package com.animal.battle.entity

/** 掉落物：经验球 / 金币 / 血包 / 金色经验球 / 吸铁石 */
class Pickup {
    enum class Type { EXP, COIN, HEART, GOLD_EXP, MAGNET }

    var type = Type.EXP
    var x = 0f
    var y = 0f
    var value = 1
    var alive = false
    var life = 30f        // 漂浮后自动消失
    var attracted = false // 是否被磁力吸向玩家
    var bobPhase = 0f     // 上下浮动动画相位

    fun spawn(type: Type, x: Float, y: Float, value: Int) {
        this.type = type
        this.x = x
        this.y = y
        this.value = value
        this.alive = true
        this.life = 30f
        this.attracted = false
        this.bobPhase = (Math.random() * Math.PI * 2).toFloat()
    }
}
