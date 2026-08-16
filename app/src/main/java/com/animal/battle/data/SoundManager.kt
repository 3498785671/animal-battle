package com.animal.battle.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.animal.battle.R

/**
 * 音效管理：基于 SoundPool 播放短音效（线程安全，可在游戏线程调用）。
 * 射击音效做节流，避免高频触发过吵。
 */
class SoundManager(context: Context) {

    companion object {
        const val SHOOT = 0
        const val HIT = 1
        const val EXPLOSION = 2
        const val LEVELUP = 3
        const val COIN = 4
        const val HEAL = 5
        const val HURT = 6
        const val SKILL = 7
        const val GAMEOVER = 8
    }

    private val pool: SoundPool
    private val ids = IntArray(9) { 0 }
    private var shootLast = 0L

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()
        ids[SHOOT] = pool.load(context, R.raw.shoot, 1)
        ids[HIT] = pool.load(context, R.raw.hit, 1)
        ids[EXPLOSION] = pool.load(context, R.raw.explosion, 1)
        ids[LEVELUP] = pool.load(context, R.raw.levelup, 1)
        ids[COIN] = pool.load(context, R.raw.coin, 1)
        ids[HEAL] = pool.load(context, R.raw.heal, 1)
        ids[HURT] = pool.load(context, R.raw.hurt, 1)
        ids[SKILL] = pool.load(context, R.raw.skill, 1)
        ids[GAMEOVER] = pool.load(context, R.raw.gameover, 1)
    }

    fun play(id: Int, volume: Float = 1f) {
        val sid = ids.getOrElse(id) { 0 }
        if (sid == 0) return
        if (id == SHOOT) {
            val now = System.currentTimeMillis()
            if (now - shootLast < 45) return
            shootLast = now
        }
        pool.play(sid, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
