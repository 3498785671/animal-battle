package com.animal.battle.entity

/** 子弹（自动普攻弹幕 / 技能弹幕） */
class Bullet {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var damage = 0f
    var radius = 6f
    var pierce = 0      // 剩余穿透次数（0 = 命中即消失）
    var alive = false
    var color = 0xFFFFD54F.toInt()
    var friendly = true // 目前全部为友方子弹
    var life = 3f       // 最大存活时间

    fun spawn(
        x: Float, y: Float, vx: Float, vy: Float,
        damage: Float, pierce: Int = 0,
        friendly: Boolean = true,
        color: Int = 0xFFFFD54F.toInt(),
        radius: Float = 6f,
        life: Float = 3f,
    ) {
        this.x = x
        this.y = y
        this.vx = vx
        this.vy = vy
        this.damage = damage
        this.pierce = pierce
        this.radius = radius
        this.color = color
        this.friendly = friendly
        this.alive = true
        this.life = life
    }
}
