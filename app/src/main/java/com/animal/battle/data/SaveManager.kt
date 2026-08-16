package com.animal.battle.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地存档：金币、永久属性等级、已解锁角色、最高分、当前角色。
 * 使用 SharedPreferences 存储，纯本地离线。
 */
class SaveManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("animal_battle_save", Context.MODE_PRIVATE)

    // ===== 金币 =====
    var coins: Int
        get() = prefs.getInt("coins", 0)
        set(v) = prefs.edit().putInt("coins", v).apply()

    fun addCoins(delta: Int) {
        coins = (coins + delta).coerceAtLeast(0)
    }

    // ===== 永久属性等级 =====
    fun permLevel(id: GameConfig.PermUpgradeId): Int =
        prefs.getInt("perm_${id.name}", 0)

    fun setPermLevel(id: GameConfig.PermUpgradeId, level: Int) =
        prefs.edit().putInt("perm_${id.name}", level.coerceIn(0, 99)).apply()

    /** 某永久属性下一级所需金币 */
    fun permNextCost(def: GameConfig.PermUpgradeDef): Int {
        val lv = permLevel(def.id)
        if (lv >= def.maxLevel) return -1
        return (def.baseCost * Math.pow(def.costGrowth.toDouble(), lv.toDouble())).toInt()
    }

    // ===== 角色解锁与选择 =====
    fun isUnlocked(charId: String): Boolean =
        prefs.getBoolean("char_unlock_$charId", charId == "fox")

    fun unlock(charId: String) = prefs.edit().putBoolean("char_unlock_$charId", true).apply()

    var currentCharacter: String
        get() = prefs.getString("current_char", "fox") ?: "fox"
        set(v) = prefs.edit().putString("current_char", v).apply()

    // ===== 最高分 =====
    var highScore: Int
        get() = prefs.getInt("high_score", 0)
        set(v) = prefs.edit().putInt("high_score", v).apply()

    var bestKills: Int
        get() = prefs.getInt("best_kills", 0)
        set(v) = prefs.edit().putInt("best_kills", v).apply()

    var bestTime: Float
        get() = prefs.getFloat("best_time", 0f)
        set(v) = prefs.edit().putFloat("best_time", v).apply()
}
