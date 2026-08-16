package com.animal.battle.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.animal.battle.GameState
import com.animal.battle.SummonFox
import com.animal.battle.data.GameConfig
import com.animal.battle.data.SaveManager
import com.animal.battle.entity.Bullet
import com.animal.battle.entity.Enemy
import com.animal.battle.entity.Particle
import com.animal.battle.entity.Pickup
import com.animal.battle.entity.Player
import com.animal.battle.ui.Joystick
import com.animal.battle.ui.UIButton
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全部 Canvas 绘制：游戏世界、实体、粒子特效、HUD、摇杆、技能按钮、
 * 以及主菜单/养成/升级/结算界面。逻辑坐标宽 720。
 */
class Renderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // 背景装饰点（固定种子，避免每帧分配）
    private val deco = ArrayList<FloatArray>(40)

    init {
        val rnd = java.util.Random(7)
        for (i in 0 until 40) {
            deco.add(floatArrayOf(rnd.nextFloat() * 720f, rnd.nextFloat() * 1600f, 8f + rnd.nextFloat() * 20f))
        }
    }

    // ================= 世界渲染（在 scale 内调用） =================
    fun renderWorld(c: Canvas, s: GameState) {
        drawBackground(c, s)
        for (pk in s.pickups) if (pk.alive) drawPickup(c, pk)
        for (e in s.enemies) if (e.alive) drawEnemy(c, e)
        for (f in s.summonFoxes) drawSummonFox(c, s.player, f)
        drawPlayer(c, s.player, s.character)
        for (b in s.bullets) if (b.alive) drawBullet(c, b)
        for (p in s.particles) if (p.alive) drawParticle(c, p)
    }

    private fun drawBackground(c: Canvas, s: GameState) {
        c.drawColor(0xFF8FCF5A.toInt())
        // 网格
        stroke.color = 0x33FFFFFF
        stroke.strokeWidth = 2f
        val step = 90f
        var x = step
        while (x < s.worldW) { c.drawLine(x, 0f, x, s.worldH, stroke); x += step }
        var y = step
        while (y < s.worldH) { c.drawLine(0f, y, s.worldW, y, stroke); y += step }
        // 装饰点
        for (d in deco) {
            if (d[1] > s.worldH) continue
            fill.color = 0x33FFFFFF
            c.drawCircle(d[0], d[1], d[2], fill)
        }
        // 边界墙
        stroke.color = 0xFF2E7D32.toInt()
        stroke.strokeWidth = 16f
        c.drawRect(0f, 0f, s.worldW, s.worldH, stroke)
    }

    // ================= 玩家 =================
    private fun drawPlayer(c: Canvas, p: Player, char: GameConfig.CharacterDef) {
        // 面向：优先最近敌人方向（简化用摇杆方向，无输入朝上）
        var angle = -Math.PI / 2
        if (p.inputX != 0f || p.inputY != 0f) angle = kotlin.math.atan2(p.inputY.toDouble(), p.inputX.toDouble())
        val flash = p.invincibleTimer > 0f && ((p.invincibleTimer * 20).toInt() % 2 == 0)

        val body = if (char.id == "panda") 0xFFF5F5F5.toInt() else char.color
        val ear = when (char.id) {
            "rabbit" -> 0xFFF48FB1.toInt()
            "panda" -> 0xFF333333.toInt()
            else -> char.color
        }

        c.save()
        c.translate(p.x, p.y)
        if (flash) fill.color = 0x66FFFFFF

        // 尾巴
        fill.color = body
        val tx = (-cos(angle).toFloat()) * p.radius * 0.9f
        val ty = (-sin(angle).toFloat()) * p.radius * 0.9f
        c.drawCircle(tx, ty, p.radius * 0.4f, fill)

        // 耳朵
        fill.color = ear
        when (char.id) {
            "rabbit" -> {
                c.save(); c.rotate(-30f)
                c.drawOval(RectF(-p.radius * 0.25f, -p.radius * 1.7f, p.radius * 0.25f, -p.radius * 0.4f), fill)
                c.restore()
                c.save(); c.rotate(30f)
                c.drawOval(RectF(-p.radius * 0.25f, -p.radius * 1.7f, p.radius * 0.25f, -p.radius * 0.4f), fill)
                c.restore()
            }
            "panda", "bear" -> {
                c.drawCircle(-p.radius * 0.55f, -p.radius * 0.7f, p.radius * 0.35f, fill)
                c.drawCircle(p.radius * 0.55f, -p.radius * 0.7f, p.radius * 0.35f, fill)
            }
            else -> {
                path.reset()
                path.moveTo(-p.radius * 0.7f, -p.radius * 0.4f)
                path.lineTo(-p.radius * 0.45f, -p.radius * 1.3f)
                path.lineTo(-p.radius * 0.1f, -p.radius * 0.6f)
                path.close()
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(p.radius * 0.7f, -p.radius * 0.4f)
                path.lineTo(p.radius * 0.45f, -p.radius * 1.3f)
                path.lineTo(p.radius * 0.1f, -p.radius * 0.6f)
                path.close()
                c.drawPath(path, fill)
            }
        }

        // 身体
        fill.color = body
        c.drawCircle(0f, 0f, p.radius, fill)
        // 肚皮
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(0f, p.radius * 0.3f, p.radius * 0.5f, fill)

        // 眼睛（朝 angle）
        val ex = cos(angle).toFloat() * p.radius * 0.22f
        val ey = sin(angle).toFloat() * p.radius * 0.22f
        if (char.id == "panda") {
            fill.color = 0xFF333333.toInt()
            c.drawCircle(-p.radius * 0.32f, -p.radius * 0.15f, p.radius * 0.3f, fill)
            c.drawCircle(p.radius * 0.32f, -p.radius * 0.15f, p.radius * 0.3f, fill)
        }
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(-p.radius * 0.32f + ex, -p.radius * 0.15f + ey, p.radius * 0.2f, fill)
        c.drawCircle(p.radius * 0.32f + ex, -p.radius * 0.15f + ey, p.radius * 0.2f, fill)
        fill.color = 0xFF263238.toInt()
        c.drawCircle(-p.radius * 0.32f + ex * 1.4f, -p.radius * 0.15f + ey * 1.4f, p.radius * 0.1f, fill)
        c.drawCircle(p.radius * 0.32f + ex * 1.4f, -p.radius * 0.15f + ey * 1.4f, p.radius * 0.1f, fill)

        c.restore()
    }

    // ================= 敌人 =================
    private fun drawEnemy(c: Canvas, e: Enemy) {
        val r = e.radius * (if (e.bornTimer > 0f) (1f - e.bornTimer * 4f).coerceAtLeast(0.2f) else 1f)
        val color = e.type.color
        c.save()
        c.translate(e.x, e.y)

        // 精英/Boss 光环
        if (e.type == GameConfig.EnemyType.ELITE || e.type == GameConfig.EnemyType.BOSS) {
            stroke.color = color
            stroke.strokeWidth = 4f
            c.drawCircle(0f, 0f, r + 8f, stroke)
        }

        if (e.type == GameConfig.EnemyType.SNAKE) {
            // 蛇：椭圆身体
            c.rotate((e.angle * 180 / Math.PI).toFloat())
            fill.color = color
            c.drawOval(RectF(-r * 1.6f, -r * 0.7f, r * 1.6f, r * 0.7f), fill)
            fill.color = 0xFFFFFFFF.toInt()
            c.drawCircle(r * 1.4f, -r * 0.15f, r * 0.18f, fill)
            c.drawCircle(r * 1.4f, r * 0.25f, r * 0.18f, fill)
            fill.color = 0xFF263238.toInt()
            c.drawCircle(r * 1.45f, -r * 0.15f, r * 0.09f, fill)
            c.drawCircle(r * 1.45f, r * 0.25f, r * 0.09f, fill)
        } else {
            // 圆身体类
            val earColor = color
            // 耳朵
            fill.color = earColor
            if (e.type == GameConfig.EnemyType.BEAR || e.type == GameConfig.EnemyType.BOSS) {
                c.drawCircle(-r * 0.55f, -r * 0.7f, r * 0.35f, fill)
                c.drawCircle(r * 0.55f, -r * 0.7f, r * 0.35f, fill)
            } else if (e.type == GameConfig.EnemyType.HEDGEHOG) {
                c.drawCircle(-r * 0.5f, -r * 0.7f, r * 0.22f, fill)
                c.drawCircle(r * 0.5f, -r * 0.7f, r * 0.22f, fill)
            } else if (e.type == GameConfig.EnemyType.BOAR) {
                c.drawCircle(-r * 0.5f, -r * 0.7f, r * 0.28f, fill)
                c.drawCircle(r * 0.5f, -r * 0.7f, r * 0.28f, fill)
            } else {
                path.reset()
                path.moveTo(-r * 0.7f, -r * 0.4f); path.lineTo(-r * 0.45f, -r * 1.3f); path.lineTo(-r * 0.1f, -r * 0.6f); path.close()
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(r * 0.7f, -r * 0.4f); path.lineTo(r * 0.45f, -r * 1.3f); path.lineTo(r * 0.1f, -r * 0.6f); path.close()
                c.drawPath(path, fill)
            }
            // 身体
            fill.color = color
            c.drawCircle(0f, 0f, r, fill)
            fill.color = 0x88FFFFFF.toInt()
            c.drawCircle(0f, r * 0.3f, r * 0.5f, fill)
            // 野猪獠牙
            if (e.type == GameConfig.EnemyType.BOAR) {
                fill.color = 0xFFFFFFFF.toInt()
                path.reset()
                path.moveTo(-r * 0.3f, r * 0.3f); path.lineTo(-r * 0.45f, r * 0.7f); path.lineTo(-r * 0.15f, r * 0.4f); path.close()
                c.drawPath(path, fill)
                path.reset()
                path.moveTo(r * 0.3f, r * 0.3f); path.lineTo(r * 0.45f, r * 0.7f); path.lineTo(r * 0.15f, r * 0.4f); path.close()
                c.drawPath(path, fill)
            }
            // 刺猬背刺
            if (e.type == GameConfig.EnemyType.HEDGEHOG) {
                fill.color = 0xFF37474F.toInt()
                for (k in 0 until 7) {
                    val ang = Math.PI + (k - 3) * 0.4
                    val bx = cos(ang).toFloat() * r * 0.9f
                    val by = sin(ang).toFloat() * r * 0.9f
                    val ox = cos(ang).toFloat() * r * 1.4f
                    val oy = sin(ang).toFloat() * r * 1.4f
                    val tx = -sin(ang).toFloat() * 5f
                    val ty = cos(ang).toFloat() * 5f
                    path.reset()
                    path.moveTo(bx - tx, by - ty)
                    path.lineTo(ox, oy)
                    path.lineTo(bx + tx, by + ty)
                    path.close()
                    c.drawPath(path, fill)
                }
            }
            // 蝙蝠翅膀
            if (e.type == GameConfig.EnemyType.BAT) {
                fill.color = color
                c.save(); c.rotate(-35f)
                c.drawOval(RectF(-r * 1.6f, -r * 0.15f, -r * 0.2f, r * 0.5f), fill)
                c.restore()
                c.save(); c.rotate(35f)
                c.drawOval(RectF(r * 0.2f, -r * 0.15f, r * 1.6f, r * 0.5f), fill)
                c.restore()
            }
            // 眼睛
            val ex = cos(e.angle.toDouble()).toFloat() * r * 0.2f
            val ey = sin(e.angle.toDouble()).toFloat() * r * 0.2f
            fill.color = 0xFFFFFFFF.toInt()
            c.drawCircle(-r * 0.32f + ex, -r * 0.15f + ey, r * 0.2f, fill)
            c.drawCircle(r * 0.32f + ex, -r * 0.15f + ey, r * 0.2f, fill)
            fill.color = 0xFF263238.toInt()
            c.drawCircle(-r * 0.32f + ex * 1.4f, -r * 0.15f + ey * 1.4f, r * 0.1f, fill)
            c.drawCircle(r * 0.32f + ex * 1.4f, -r * 0.15f + ey * 1.4f, r * 0.1f, fill)
        }

        // 受击白闪
        if (e.hitFlash > 0f) {
            fill.color = 0x88FFFFFF.toInt()
            c.drawCircle(0f, 0f, r, fill)
        }
        c.restore()
    }

    private fun drawSummonFox(c: Canvas, p: Player, f: SummonFox) {
        val x = p.x + cos(f.angle.toDouble()).toFloat() * 70f
        val y = p.y + sin(f.angle.toDouble()).toFloat() * 70f
        fill.color = 0xFFFFD54F.toInt()
        c.drawCircle(x, y, 13f, fill)
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x - 4f, y - 3f, 4f, fill)
        c.drawCircle(x + 4f, y - 3f, 4f, fill)
        fill.color = 0xFF263238.toInt()
        c.drawCircle(x - 4f, y - 3f, 2f, fill)
        c.drawCircle(x + 4f, y - 3f, 2f, fill)
    }

    // ================= 子弹 / 掉落 / 粒子 =================
    private fun drawBullet(c: Canvas, b: Bullet) {
        fill.color = 0x66FFFF00
        c.drawCircle(b.x, b.y, b.radius * 1.8f, fill)
        fill.color = b.color
        c.drawCircle(b.x, b.y, b.radius, fill)
        fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(b.x, b.y, b.radius * 0.5f, fill)
    }

    private fun drawPickup(c: Canvas, pk: Pickup) {
        val bob = sin((pk.bobPhase + System.currentTimeMillis() / 200.0)).toFloat() * 3f
        val y = pk.y + bob
        when (pk.type) {
            Pickup.Type.EXP -> {
                fill.color = 0xFF43A047.toInt()
                c.drawCircle(pk.x, y, 7f, fill)
                fill.color = 0xFFB9F6CA.toInt()
                c.drawCircle(pk.x - 2f, y - 2f, 2.5f, fill)
            }
            Pickup.Type.COIN -> {
                fill.color = 0xFFFFB300.toInt()
                c.drawCircle(pk.x, y, 8f, fill)
                fill.color = 0xFFFFE082.toInt()
                c.drawCircle(pk.x - 2f, y - 2f, 3f, fill)
            }
            Pickup.Type.HEART -> {
                fill.color = 0xFFE53935.toInt()
                c.drawCircle(pk.x, y, 9f, fill)
                fill.color = 0xFFFF8A80.toInt()
                c.drawCircle(pk.x - 3f, y - 3f, 3f, fill)
            }
        }
    }

    private fun drawParticle(c: Canvas, pt: Particle) {
        val t = (pt.life / pt.maxLife).coerceIn(0f, 1f)
        val size = if (pt.shrink) pt.size * t else pt.size
        val alpha = (t * 255).toInt()
        fill.color = (alpha shl 24) or (pt.color and 0x00FFFFFF)
        c.drawCircle(pt.x, pt.y, size, fill)
    }

    // ================= HUD（屏幕坐标） =================
    fun renderHUD(c: Canvas, s: GameState, sw: Float, sh: Float, scale: Float) {
        val p = s.player
        val pad = 16f * (sw / 720f)
        val barW = 200f * (sw / 720f)

        // 血条
        val hpRatio = (p.hp / p.maxHp).coerceIn(0f, 1f)
        fill.color = 0x55000000
        c.drawRoundRect(RectF(pad, pad, pad + barW, pad + 26f * (sw / 720f)), 12f, 12f, fill)
        fill.color = if (hpRatio > 0.3f) 0xFF4CAF50.toInt() else 0xFFE53935.toInt()
        c.drawRoundRect(RectF(pad + 2f, pad + 2f, pad + 2f + (barW - 4f) * hpRatio, pad + 24f * (sw / 720f)), 10f, 10f, fill)
        // 血量数字
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = 20f * (sw / 720f)
        text.textAlign = Paint.Align.LEFT
        c.drawText("${p.hp.toInt()}/${p.maxHp.toInt()}", pad + 8f, pad + 20f * (sw / 720f), text)

        // 等级 + 经验条
        val expY = pad + 38f * (sw / 720f)
        fill.color = 0x55000000
        c.drawRoundRect(RectF(pad, expY, pad + barW, expY + 14f * (sw / 720f)), 7f, 7f, fill)
        val expRatio = (p.exp.toFloat() / p.expToNext).coerceIn(0f, 1f)
        fill.color = 0xFF42A5F5.toInt()
        c.drawRoundRect(RectF(pad + 2f, expY + 2f, pad + 2f + (barW - 4f) * expRatio, expY + 12f * (sw / 720f)), 6f, 6f, fill)
        text.textSize = 18f * (sw / 720f)
        c.drawText("Lv ${p.level}", pad + 8f, expY - 6f * (sw / 720f), text)

        // 击杀 / 金币
        text.textSize = 24f * (sw / 720f)
        text.textAlign = Paint.Align.LEFT
        c.drawText("击杀 ${s.kills}", pad, expY + 40f * (sw / 720f), text)
        c.drawText("金币 ${s.coinsEarned}", pad, expY + 72f * (sw / 720f), text)

        // 计时（右上）
        text.textAlign = Paint.Align.RIGHT
        text.textSize = 28f * (sw / 720f)
        val mm = (s.survivalTime / 60).toInt()
        val ss = (s.survivalTime % 60).toInt()
        c.drawText("%02d:%02d".format(mm, ss), sw - pad, pad + 30f * (sw / 720f), text)

        // 敌人数量
        text.textSize = 20f * (sw / 720f)
        c.drawText("敌人 ${s.enemies.size}", sw - pad, pad + 58f * (sw / 720f), text)
    }

    fun renderJoystick(c: Canvas, joy: Joystick) {
        stroke.color = 0x55FFFFFF
        stroke.strokeWidth = 4f
        c.drawCircle(joy.baseX, joy.baseY, joy.baseRadius, stroke)
        fill.color = 0x33FFFFFF
        c.drawCircle(joy.baseX, joy.baseY, joy.baseRadius, fill)
        fill.color = if (joy.active) 0xBBFFFFFF.toInt() else 0x88FFFFFF.toInt()
        c.drawCircle(joy.knobX, joy.knobY, joy.knobRadius, fill)
    }

    fun renderSkillButtons(c: Canvas, s: GameState, buttons: List<UIButton>) {
        val p = s.player
        for ((i, b) in buttons.withIndex()) {
            val skill = p.skills[i] ?: continue
            val cx = b.rect.centerX()
            val cy = b.rect.centerY()
            val r = b.rect.width() / 2f
            // 底
            fill.color = if (skill.isReady) 0xAAFFFFFF.toInt() else 0x66555555.toInt()
            c.drawCircle(cx, cy, r, fill)
            stroke.color = 0xFFFFFFFF.toInt()
            stroke.strokeWidth = 3f
            c.drawCircle(cx, cy, r, stroke)
            // 冷却遮罩
            if (!skill.isReady) {
                val ratio = (skill.currentCooldown / skill.def.cooldown).coerceIn(0f, 1f)
                fill.color = 0x88000000.toInt()
                c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * ratio, true, fill)
            }
            // 图标色
            fill.color = skill.def.color
            c.drawCircle(cx, cy, r * 0.55f, fill)
            // 名称首字
            text.color = 0xFFFFFFFF.toInt()
            text.textSize = r * 0.6f
            text.textAlign = Paint.Align.CENTER
            c.drawText(skill.def.name.substring(0, 1), cx, cy + text.textSize * 0.35f, text)
        }
    }

    // ================= 升级三选一 =================
    fun renderUpgradeOverlay(c: Canvas, s: GameState, sw: Float, sh: Float, buttons: List<UIButton>) {
        c.drawColor(0xAA000000.toInt())
        val pad = sw * 0.08f
        text.textAlign = Paint.Align.CENTER
        text.textSize = sw * 0.08f
        text.color = 0xFFFFD54F.toInt()
        c.drawText("升 级 ！", sw / 2f, sh * 0.16f, text)
        text.textSize = sw * 0.045f
        text.color = 0xFFFFFFFF.toInt()
        c.drawText("选择一项强化", sw / 2f, sh * 0.22f, text)

        for (b in buttons) {
            fill.color = if (b.enabled) 0xFF3E2723.toInt() else 0xFF555555.toInt()
            c.drawRoundRect(b.rect, 20f, 20f, fill)
            stroke.color = 0xFFFFB300.toInt()
            stroke.strokeWidth = 3f
            c.drawRoundRect(b.rect, 20f, 20f, stroke)
            text.textSize = sw * 0.055f
            text.color = 0xFFFFFFFF.toInt()
            c.drawText(b.text, b.rect.centerX(), b.rect.centerY() - sw * 0.01f, text)
            text.textSize = sw * 0.038f
            text.color = 0xFFBDBDBD.toInt()
            c.drawText(b.subtext, b.rect.centerX(), b.rect.centerY() + sw * 0.045f, text)
        }
    }

    // ================= 结算 =================
    fun renderGameOver(c: Canvas, s: GameState, save: SaveManager, sw: Float, sh: Float, buttons: List<UIButton>) {
        c.drawColor(0xBB000000.toInt())
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFFF5252.toInt()
        text.textSize = sw * 0.1f
        c.drawText("游戏结束", sw / 2f, sh * 0.14f, text)

        val mm = (s.survivalTime / 60).toInt()
        val ss = (s.survivalTime % 60).toInt()
        val lines = arrayOf(
            "存活时间  %02d:%02d".format(mm, ss),
            "击杀数量  ${s.kills}",
            "获得金币  ${s.coinsEarned}",
            "历史最高  ${save.highScore}",
        )
        text.textSize = sw * 0.05f
        text.color = 0xFFFFFFFF.toInt()
        for ((i, l) in lines.withIndex()) {
            c.drawText(l, sw / 2f, sh * (0.24f + i * 0.06f), text)
        }

        for (b in buttons) {
            fill.color = b.color
            c.drawRoundRect(b.rect, 20f, 20f, fill)
            text.color = 0xFFFFFFFF.toInt()
            text.textSize = sw * 0.05f
            c.drawText(b.text, b.rect.centerX(), b.rect.centerY() + sw * 0.018f, text)
        }
    }

    // ================= 主菜单 =================
    fun renderMenu(c: Canvas, save: SaveManager, sw: Float, sh: Float, buttons: List<UIButton>) {
        c.drawColor(0xFF8FCF5A.toInt())
        // 装饰
        fill.color = 0x33FFFFFF
        for (d in deco) c.drawCircle(d[0] * (sw / 720f), d[1] * (sw / 720f), d[2] * (sw / 720f), fill)

        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = sw * 0.11f
        c.drawText("动物大逃杀", sw / 2f, sh * 0.22f, text)
        text.textSize = sw * 0.045f
        c.drawText("爽快割草 · 无尽生存", sw / 2f, sh * 0.28f, text)
        text.color = 0xFFFFE082.toInt()
        c.drawText("最高分 ${save.highScore} · 金币 ${save.coins}", sw / 2f, sh * 0.33f, text)

        for (b in buttons) {
            fill.color = b.color
            c.drawRoundRect(b.rect, 24f, 24f, fill)
            text.color = 0xFFFFFFFF.toInt()
            text.textSize = sw * 0.055f
            c.drawText(b.text, b.rect.centerX(), b.rect.centerY() + sw * 0.02f, text)
        }
    }

    // ================= 养成页 =================
    fun renderShop(c: Canvas, save: SaveManager, sw: Float, sh: Float, buttons: List<UIButton>) {
        c.drawColor(0xFF5D4037.toInt())
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFFFD54F.toInt()
        text.textSize = sw * 0.08f
        c.drawText("养 成", sw / 2f, sh * 0.08f, text)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = sw * 0.045f
        c.drawText("金币 ${save.coins}", sw / 2f, sh * 0.13f, text)

        for (b in buttons) {
            fill.color = if (b.enabled) b.color else 0xFF444444.toInt()
            c.drawRoundRect(b.rect, 14f, 14f, fill)
            text.textAlign = Paint.Align.LEFT
            text.color = 0xFFFFFFFF.toInt()
            text.textSize = sw * 0.042f
            c.drawText(b.text, b.rect.left + sw * 0.04f, b.rect.centerY() + sw * 0.014f, text)
            text.textAlign = Paint.Align.RIGHT
            text.color = if (b.enabled) 0xFFFFE082.toInt() else 0xFF888888.toInt()
            c.drawText(b.subtext, b.rect.right - sw * 0.04f, b.rect.centerY() + sw * 0.014f, text)
        }
    }
}
