package com.animal.battle.entity

/** 粒子：用于爆炸、拖尾、命中、升级等特效 */
class Particle {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 0f
    var size = 4f
    var color = 0xFFFFFFFF.toInt()
    var alive = false
    var shrink = true        // 是否随生命衰减缩小
    var gravity = 0f
    var drag = 0.9f          // 每帧速度衰减

    fun spawn(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int, drag: Float = 0.92f, gravity: Float = 0f) {
        this.x = x
        this.y = y
        this.vx = vx
        this.vy = vy
        this.life = life
        this.maxLife = life
        this.size = size
        this.color = color
        this.drag = drag
        this.gravity = gravity
        this.shrink = true
        this.alive = true
    }

    fun update(dt: Float) {
        life -= dt
        if (life <= 0f) { alive = false; return }
        vx *= drag
        vy = vy * drag + gravity * dt
        x += vx * dt
        y += vy * dt
    }
}
