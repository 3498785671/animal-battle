package com.animal.battle.entity

import com.animal.battle.data.GameConfig

/**
 * 敌人实体。所有实例通过对象池复用，避免频繁 GC。
 */
class Enemy {

    var type: GameConfig.EnemyType = GameConfig.EnemyType.WOLF
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var hp = 0f
    var maxHp = 0f
    var radius = 0f
    var speed = 0f
    var damage = 0f
    var exp = 0
    var coin = 0
    var alive = false

    var attackCooldown = 0f   // 攻击玩家冷却
    var hitFlash = 0f         // 受击白闪（>0 显示白色）
    var angle = 0f            // 朝向角，用于绘制
    var chargeTimer = 0f      // 野猪冲锋计时
    var isCharging = false
    var knockbackX = 0f
    var knockbackY = 0f
    var bornTimer = 0f        // 出生后的缩放/无敌过渡
    var wobblePhase = 0f      // 蝙蝠摆动相位
    var weaponHitCooldown = 0f // 环绕武器命中冷却
    var eliteFireTimer = 0f   // 精英怪散射弹幕冷却

    fun spawn(type: GameConfig.EnemyType, x: Float, y: Float, hpScale: Float = 1f, speedScale: Float = 1f) {
        this.type = type
        this.x = x
        this.y = y
        this.maxHp = type.hp * hpScale
        this.hp = maxHp
        this.radius = type.radius
        this.speed = type.speed * speedScale
        this.damage = type.damage
        this.exp = type.exp
        this.coin = type.coin
        this.alive = true
        this.attackCooldown = 0f
        this.hitFlash = 0f
        this.chargeTimer = 0f
        this.isCharging = false
        this.knockbackX = 0f
        this.knockbackY = 0f
        this.bornTimer = 0.15f
        this.vx = 0f
        this.vy = 0f
        this.wobblePhase = (Math.random() * Math.PI * 2).toFloat()
        this.weaponHitCooldown = 0f
        this.eliteFireTimer = 0f
    }

    fun takeDamage(amount: Float) {
        if (!alive) return
        hp -= amount
        hitFlash = 0.1f
        if (hp <= 0) alive = false
    }
}
