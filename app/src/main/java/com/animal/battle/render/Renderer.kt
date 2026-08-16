package com.animal.battle.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.animal.battle.R
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
 * 全部 Canvas 绘制：游戏世界（像素贴图）、实体、特效、HUD、摇杆、技能按钮、
 * 以及主菜单/养成/结算界面。逻辑坐标宽 720。
 */
class Renderer(private val ctx: android.content.Context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    // 像素贴图（每个动物 2 帧）
    private val foxFrames = arrayOfNulls<Bitmap>(2)
    private val wolfFrames = arrayOfNulls<Bitmap>(2)
    private val bearFrames = arrayOfNulls<Bitmap>(2)
    private val boarFrames = arrayOfNulls<Bitmap>(2)
    private val snakeFrames = arrayOfNulls<Bitmap>(2)
    private val hedgehogFrames = arrayOfNulls<Bitmap>(2)
    private val batFrames = arrayOfNulls<Bitmap>(2)
    private val eliteFrames = arrayOfNulls<Bitmap>(2)
    private val bossFrames = arrayOfNulls<Bitmap>(2)
    private var expBitmap: Bitmap? = null
    private var orbWhite: Bitmap? = null
    private var orbGreen: Bitmap? = null
    private var orbBlue: Bitmap? = null
    private var orbPurple: Bitmap? = null
    private var orbRed: Bitmap? = null

    // 背景装饰点
    private val deco = ArrayList<FloatArray>(40)

    init {
        val rnd = java.util.Random(7)
        for (i in 0 until 40) {
            deco.add(floatArrayOf(rnd.nextFloat() * 720f, rnd.nextFloat() * 1600f, 8f + rnd.nextFloat() * 20f))
        }
        loadBitmaps()
    }

    private fun loadBitmaps() {
        fun loadFrames(prefix: String, frames: Array<Bitmap?>): Array<Bitmap?> {
            val res = ctx.resources
            return arrayOf(
                BitmapFactory.decodeResource(res, res.getIdentifier("${prefix}_0", "drawable", ctx.packageName)),
                BitmapFactory.decodeResource(res, res.getIdentifier("${prefix}_1", "drawable", ctx.packageName)),
            )
        }
        System.arraycopy(loadFrames("fox", foxFrames), 0, foxFrames, 0, 2)
        System.arraycopy(loadFrames("wolf", wolfFrames), 0, wolfFrames, 0, 2)
        System.arraycopy(loadFrames("bear", bearFrames), 0, bearFrames, 0, 2)
        System.arraycopy(loadFrames("boar", boarFrames), 0, boarFrames, 0, 2)
        System.arraycopy(loadFrames("snake", snakeFrames), 0, snakeFrames, 0, 2)
        System.arraycopy(loadFrames("hedgehog", hedgehogFrames), 0, hedgehogFrames, 0, 2)
        System.arraycopy(loadFrames("bat", batFrames), 0, batFrames, 0, 2)
        System.arraycopy(loadFrames("elite", eliteFrames), 0, eliteFrames, 0, 2)
        System.arraycopy(loadFrames("boss", bossFrames), 0, bossFrames, 0, 2)
        val res = ctx.resources
        expBitmap = BitmapFactory.decodeResource(res, R.drawable.exp)
        orbWhite = BitmapFactory.decodeResource(res, R.drawable.orb_white)
        orbGreen = BitmapFactory.decodeResource(res, R.drawable.orb_green)
        orbBlue = BitmapFactory.decodeResource(res, R.drawable.orb_blue)
        orbPurple = BitmapFactory.decodeResource(res, R.drawable.orb_purple)
        orbRed = BitmapFactory.decodeResource(res, R.drawable.orb_red)
    }

    private fun framesForType(type: GameConfig.EnemyType): Array<Bitmap?> = when (type) {
        GameConfig.EnemyType.WOLF -> wolfFrames
        GameConfig.EnemyType.BEAR -> bearFrames
        GameConfig.EnemyType.BOAR -> boarFrames
        GameConfig.EnemyType.SNAKE -> snakeFrames
        GameConfig.EnemyType.HEDGEHOG -> hedgehogFrames
        GameConfig.EnemyType.BAT -> batFrames
        GameConfig.EnemyType.ELITE -> eliteFrames
        GameConfig.EnemyType.BOSS -> bossFrames
    }

    private fun orbForTier(tier: Int): Bitmap? = when (tier) {
        1 -> orbGreen; 2 -> orbBlue; 3 -> orbPurple; 4 -> orbRed; else -> orbWhite
    }

    // ================= 世界渲染（在 scale 内调用） =================
    fun renderWorld(c: Canvas, s: GameState) {
        drawBackground(c, s)
        // 环绕武器（底层，在角色下）
        drawOrbs(c, s)
        for (pk in s.pickups) if (pk.alive) drawPickup(c, pk)
        for (e in s.enemies) if (e.alive) drawEnemy(c, e, s.survivalTime)
        for (f in s.summonFoxes) drawSummonFox(c, s.player, f)
        drawPlayer(c, s.player)
        for (b in s.bullets) if (b.alive) drawBullet(c, b)
        for (p in s.particles) if (p.alive) drawParticle(c, p)
    }

    private val orbRect = RectF()
    private val enemyRect = RectF()
    private val playerRect = RectF()
    private val pickupRect = RectF()

    /** 绘制玩家周围的环绕能量体（白绿蓝紫红五阶） */
    private fun drawOrbs(c: Canvas, s: GameState) {
        val p = s.player
        val tier = GameConfig.WeaponTiers.tierFor(p.level)
        val count = GameConfig.WeaponTiers.count(tier)
        val orbR = GameConfig.WeaponTiers.orbRadius(tier)
        val orbitR = GameConfig.WeaponTiers.orbitRadius(p.level)
        val bmp = orbForTier(tier) ?: return
        // 与 updateOrbitalWeapon 同步的角速度
        val baseAngle = s.orbitalAngle // private 但同文件可见（实际不行，下面补救）
        // 公共访问：用 s.survivalTime * 2.6f 估算（GameState.orbitalAngle private，从 GameState 暴露）

        val step = (Math.PI * 2.0 / count).toFloat()
        for (i in 0 until count) {
            val a = baseAngle + i * step
            val ox = p.x + cos(a.toDouble()).toFloat() * orbitR
            val oy = p.y + sin(a.toDouble()).toFloat() * orbitR
            orbRect.set(ox - orbR, oy - orbR, ox + orbR, oy + orbR)
            c.drawBitmap(bmp, null, orbRect, bitmapPaint)
        }
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
    private fun drawPlayer(c: Canvas, p: Player) {
        var angle = -Math.PI / 2
        if (p.inputX != 0f || p.inputY != 0f) angle = kotlin.math.atan2(p.inputY.toDouble(), p.inputX.toDouble())
        val flash = p.invincibleTimer > 0f && ((p.invincibleTimer * 20).toInt() % 2 == 0)
        val f = ((System.currentTimeMillis() / 200).toInt()) and 1
        val bmp = foxFrames[f] ?: return
        val size = p.radius * 3.0f
        c.save()
        c.translate(p.x, p.y)
        c.rotate((angle * 180 / Math.PI + 90).toFloat())
        bitmapPaint.alpha = if (flash) 90 else 255
        playerRect.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
        c.drawBitmap(bmp, null, playerRect, bitmapPaint)
        bitmapPaint.alpha = 255
        c.restore()
    }

    // ================= 敌人 =================
    private fun drawEnemy(c: Canvas, e: Enemy, time: Float) {
        val frames = framesForType(e.type)
        val f = ((time * 6).toInt()) and 1
        val bmp = frames[f] ?: return
        val bornScale = if (e.bornTimer > 0f) (1f - e.bornTimer * 4f).coerceAtLeast(0.3f) else 1f
        val size = e.radius * 2.5f * bornScale
        enemyRect.set(e.x - size / 2f, e.y - size / 2f, e.x + size / 2f, e.y + size / 2f)
        c.drawBitmap(bmp, null, enemyRect, bitmapPaint)
        // 受击白闪
        if (e.hitFlash > 0f) {
            fill.color = 0x88FFFFFF.toInt()
            c.drawCircle(e.x, e.y, e.radius, fill)
        }
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
        val bmp = expBitmap ?: return
        val bob = sin((pk.bobPhase + System.currentTimeMillis() / 200.0)).toFloat() * 3f
        val y = pk.y + bob
        val size = when (pk.type) {
            Pickup.Type.EXP -> 18f
            Pickup.Type.COIN -> 18f
            Pickup.Type.HEART -> 18f
        }
        pickupRect.set(pk.x - size / 2f, y - size / 2f, pk.x + size / 2f, y + size / 2f)
        c.drawBitmap(bmp, null, pickupRect, bitmapPaint)
        // 叠加颜色环以区分类型
        fill.color = when (pk.type) {
            Pickup.Type.COIN -> 0x88FFB300.toInt()
            Pickup.Type.HEART -> 0x88E53935.toInt()
            Pickup.Type.EXP -> 0x00000000
        }
        if (fill.color != 0) c.drawCircle(pk.x, y, 12f, fill)
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
