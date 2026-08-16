package com.animal.battle.entity

import com.animal.battle.data.GameConfig

/**
 * 玩家（狐狸）。持有战斗属性和主动技能槽。
 */
class Player {

    var x = 0f
    var y = 0f

    // 输入方向（摇杆，归一化）
    var inputX = 0f
    var inputY = 0f

    var hp = 0f
    var maxHp = 0f

    // 战斗属性
    var baseAttack = 0f
    var attackMult = 1f        // 局内攻击乘数
    var baseAttackSpeed = 0f
    var attackSpeedMult = 1f
    var baseBullets = 1
    var bonusBullets = 0
    var baseMoveSpeed = 0f
    var moveSpeedMult = 1f
    var armor = 0f             // 减伤比例 0~1
    var pickupRange = 0f
    var lifesteal = 0f         // 击杀回血
    var expMult = 1f           // 经验获取乘数
    var coinMult = 1f          // 金币获取乘数
    var cooldownMult = 1f      // 技能冷却缩减乘数
    var pierce = 0             // 子弹穿透次数
    var critChance = 0f        // 暴击率
    val critMult = 2f          // 暴击伤害倍数
    var explosive = false      // 子弹命中时小范围爆炸

    // 成长
    var level = 1
    var exp = 0
    var expToNext = 0

    // 攻击计时
    var attackTimer = 0f

    // 受击无敌
    var invincibleTimer = 0f

    // 技能槽
    val skills = arrayOfNulls<Skill>(3)

    // ===== 便捷属性 =====
    val attack: Float get() = baseAttack * attackMult
    val attackSpeed: Float get() = baseAttackSpeed * attackSpeedMult
    val bullets: Int get() = baseBullets + bonusBullets
    val moveSpeed: Float get() = baseMoveSpeed * moveSpeedMult
    val radius: Float get() = GameConfig.PLAYER_RADIUS

    val isDead: Boolean get() = hp <= 0f

    fun takeDamage(amount: Float): Boolean {
        if (invincibleTimer > 0f) return false
        val reduced = amount * (1f - armor)
        hp -= reduced
        invincibleTimer = GameConfig.PLAYER_HURT_INVINCIBLE
        if (hp <= 0f) hp = 0f
        return true
    }

    fun heal(amount: Float) {
        hp = (hp + amount).coerceAtMost(maxHp)
    }

    fun gainExp(amount: Int): Int {
        exp += (amount * expMult).toInt()
        var gained = 0
        while (exp >= expToNext) {
            exp -= expToNext
            level++
            expToNext = GameConfig.expToNextLevel(level)
            gained++
        }
        return gained
    }
}

/** 主动技能实例 */
class Skill(val def: GameConfig.SkillDef) {
    var currentCooldown = 0f
    val isReady: Boolean get() = currentCooldown <= 0f
    fun startCooldown(mult: Float) {
        currentCooldown = def.cooldown * mult
    }
}
