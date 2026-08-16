package com.animal.battle.data

import kotlin.math.roundToInt

/**
 * 全局数值与配置。
 * 采用逻辑坐标系：世界宽 720，高按屏幕比例自适应，绘制时统一 scale 缩放。
 */
object GameConfig {

    // 世界逻辑尺寸（宽固定，高由 GameView 根据屏幕比例计算）
    const val WORLD_W = 720f

    // ===== 玩家基础属性 =====
    const val PLAYER_BASE_HP = 100f
    const val PLAYER_BASE_ATTACK = 12f
    const val PLAYER_BASE_ATTACK_SPEED = 1.6f   // 发/秒
    const val PLAYER_BASE_BULLETS = 1
    const val PLAYER_BASE_MOVE_SPEED = 280f     // 逻辑单位/秒
    const val PLAYER_BASE_PICKUP_RANGE = 70f
    const val PLAYER_RADIUS = 24f
    const val PLAYER_HURT_INVINCIBLE = 0.5f     // 受击无敌时间

    // ===== 敌人类型 =====
    enum class EnemyType(
        val hp: Float,
        val speed: Float,
        val damage: Float,
        val radius: Float,
        val exp: Int,
        val color: Int,
        val coin: Int,
    ) {
        WOLF(30f, 65f, 8f, 17f, 1, 0xFF9E9E9E.toInt(), 1),      // 狼：快、脆
        BEAR(140f, 32f, 20f, 28f, 4, 0xFF8D6E63.toInt(), 3),     // 熊：慢、肉
        BOAR(60f, 85f, 12f, 19f, 2, 0xFF6D4C41.toInt(), 2),      // 野猪：冲锋
        SNAKE(45f, 47f, 10f, 15f, 2, 0xFF66BB6A.toInt(), 2),     // 蛇：远程减速
        ELITE(400f, 45f, 25f, 45f, 15, 0xFFB71C1C.toInt(), 8),   // 精英：体型更大
        HEDGEHOG(60f, 35f, 22f, 18f, 3, 0xFF546E7A.toInt(), 2),  // 刺猬：慢、高伤
        BAT(26f, 88f, 6f, 13f, 2, 0xFF7E57C2.toInt(), 2),        // 蝙蝠：快、脆
        BOSS(1600f, 34f, 35f, 60f, 80, 0xFF7B1FA2.toInt(), 40),  // Boss
    }

    // 各敌人出现所需的生存秒数（解锁时间）
    val ENEMY_UNLOCK_TIME = mapOf(
        EnemyType.WOLF to 0f,
        EnemyType.BOAR to 30f,
        EnemyType.SNAKE to 70f,
        EnemyType.BEAR to 120f,
        EnemyType.HEDGEHOG to 150f,
        EnemyType.ELITE to 180f,
        EnemyType.BAT to 210f,
    )

    // ===== 环绕武器五阶（白→绿→蓝→紫→红，随等级自动递进）=====
    object WeaponTiers {
        val orbColors = intArrayOf(
            0xFFEEEEEE.toInt(),  // 白
            0xFF66BB6A.toInt(),  // 绿
            0xFF42A5F5.toInt(),  // 蓝
            0xFFAB47BC.toInt(),  // 紫
            0xFFEF5350.toInt(),  // 红
        )
        val orbNames = arrayOf("white", "green", "blue", "purple", "red")

        fun tierFor(level: Int) = when {
            level >= 20 -> 4
            level >= 15 -> 3
            level >= 10 -> 2
            level >= 5 -> 1
            else -> 0
        }
        fun count(tier: Int) = 3 + tier
        fun orbRadius(tier: Int) = (8 + tier * 2).toFloat()
        fun orbitRadius(level: Int) = (55f + level * 2.5f).coerceAtMost(170f)
        fun damageMult(tier: Int) = 1f + tier * 0.5f
    }

    // ===== 精英怪散射弹幕 =====
    object EliteConfig {
        const val SPAWN_INTERVAL_SEC = 120f  // 每 120 秒生成一只
        const val FIRE_INTERVAL = 2.0f        // 散射冷却（秒）
        const val FAN_COUNT = 4
        const val BULLET_SPEED = 280f
        const val BULLET_DAMAGE_MUL = 0.6f
        const val BULLET_RADIUS = 5f
    }

    // ===== 经验曲线 =====
    fun expToNextLevel(level: Int): Int {
        return (18f * Math.pow(level.toDouble(), 1.35) + 12).roundToInt()
    }

    // ===== 升级三选一选项 =====
    enum class UpgradeId { ATK, ATK_SPEED, BULLETS, MOVE_SPEED, MAX_HP, ARMOR, PICKUP_RANGE, LIFESTEAL, COOLDOWN, HEAL, PIERCE, EXPLOSIVE, CRIT, COIN_GAIN }

    data class UpgradeDef(val id: UpgradeId, val name: String, val desc: String)

    val UPGRADES: List<UpgradeDef> = listOf(
        UpgradeDef(UpgradeId.ATK, "利爪强化", "攻击力 +20%"),
        UpgradeDef(UpgradeId.ATK_SPEED, "疾风连击", "攻击速度 +15%"),
        UpgradeDef(UpgradeId.BULLETS, "多重弹幕", "同时发射弹幕 +1"),
        UpgradeDef(UpgradeId.MOVE_SPEED, "迅捷步伐", "移动速度 +12%"),
        UpgradeDef(UpgradeId.MAX_HP, "坚韧体魄", "最大生命 +25，并回复等量"),
        UpgradeDef(UpgradeId.ARMOR, "铁壁护甲", "受到的伤害 -10%"),
        UpgradeDef(UpgradeId.PICKUP_RANGE, "磁力拾取", "拾取范围 +40%"),
        UpgradeDef(UpgradeId.LIFESTEAL, "嗜血", "击杀回复 2 点生命"),
        UpgradeDef(UpgradeId.COOLDOWN, "灵能涌动", "主动技能冷却 -15%"),
        UpgradeDef(UpgradeId.HEAL, "急救", "立即回复 40% 生命"),
        UpgradeDef(UpgradeId.PIERCE, "穿甲弹", "子弹可穿透 1 个敌人"),
        UpgradeDef(UpgradeId.EXPLOSIVE, "爆裂弹", "子弹命中时小范围爆炸"),
        UpgradeDef(UpgradeId.CRIT, "致命一击", "暴击率 +15%（暴击 2 倍伤害）"),
        UpgradeDef(UpgradeId.COIN_GAIN, "聚宝盆", "金币获取 +25%"),
    )

    // ===== 主动技能 =====
    enum class SkillId { FIRE_BLAST, DASH, SUMMON }

    data class SkillDef(
        val id: SkillId,
        val name: String,
        val desc: String,
        val cooldown: Float,
        val color: Int,
    )

    val SKILLS: List<SkillDef> = listOf(
        SkillDef(SkillId.FIRE_BLAST, "狐火爆裂", "对全屏敌人造成大量伤害", 6f, 0xFFFF7043.toInt()),
        SkillDef(SkillId.DASH, "疾风冲刺", "向移动方向冲刺，冲刺期间无敌", 3f, 0xFF42A5F5.toInt()),
        SkillDef(SkillId.SUMMON, "灵狐召唤", "召唤环绕的灵狐自动攻击", 8f, 0xFFFFD54F.toInt()),
    )

    // ===== 局外角色 =====
    data class CharacterDef(
        val id: String,
        val name: String,
        val desc: String,
        val color: Int,
        val cost: Int,          // 金币解锁价，0 表示默认解锁
        val passiveAtk: Float,  // 被动攻击加成倍数
        val passiveHp: Float,   // 被动生命加成倍数
    )

    val CHARACTERS: List<CharacterDef> = listOf(
        CharacterDef("fox", "狐狸", "均衡的全能选手", 0xFFFF9E4A.toInt(), 0, 1.0f, 1.0f),
        CharacterDef("rabbit", "兔子", "移速 +15%", 0xFFE1BEE7.toInt(), 500, 1.0f, 0.9f),
        CharacterDef("panda", "熊猫", "生命 +40%", 0xFF424242.toInt(), 1200, 0.9f, 1.4f),
        CharacterDef("wolf", "灰狼", "攻击 +30%", 0xFF9E9E9E.toInt(), 2500, 1.3f, 0.85f),
    )

    // ===== 局外永久属性 =====
    enum class PermUpgradeId { ATK, HP, SPEED, EXP, COIN }

    data class PermUpgradeDef(
        val id: PermUpgradeId,
        val name: String,
        val baseCost: Int,
        val maxLevel: Int,
        val costGrowth: Float = 1.6f,   // 每级涨价倍率
        val perLevel: Float,            // 每级提升
    )

    val PERM_UPGRADES: List<PermUpgradeDef> = listOf(
        PermUpgradeDef(PermUpgradeId.ATK, "攻击力 +10%", 100, 20, 1.55f, 0.10f),
        PermUpgradeDef(PermUpgradeId.HP, "最大生命 +10%", 100, 20, 1.55f, 0.10f),
        PermUpgradeDef(PermUpgradeId.SPEED, "移动速度 +4%", 120, 15, 1.6f, 0.04f),
        PermUpgradeDef(PermUpgradeId.EXP, "经验获取 +10%", 150, 15, 1.65f, 0.10f),
        PermUpgradeDef(PermUpgradeId.COIN, "金币获取 +10%", 150, 15, 1.65f, 0.10f),
    )

    // 结算金币：基础 + 击杀数 * 系数
    const val COIN_PER_KILL = 0.05f
    const val COIN_BASE_REWARD = 20

    fun color(hex: Long): Int = hex.toInt()
}
