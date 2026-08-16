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
import com.animal.battle.SummonCow
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
    private val clipPath = Path()
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
    private var bloodbagBitmap: Bitmap? = null
    private var bgBitmap: Bitmap? = null
    private var back2Bitmap: Bitmap? = null
    private var back3Bitmap: Bitmap? = null
    private var armsBitmap: Bitmap? = null
    private var petcowBitmap: Bitmap? = null
    private var cowuogradeBitmap: Bitmap? = null
    private val skillIcons = arrayOfNulls<Bitmap>(3)

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
        val res = ctx.resources
        // 带 inSampleSize 的加载，避免超大图导致 OOM（闪退修复）
        fun load(name: String, maxSize: Int = 1024): Bitmap? {
            val id = res.getIdentifier(name, "drawable", ctx.packageName)
            if (id == 0) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(res, id, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeResource(res, id, opts)
        }

        // 主角：cow
        val cow = load("cow")
        foxFrames[0] = cow
        foxFrames[1] = cow
        // 怪物：rabbit / lion / panda / elephant
        val rabbit = load("rabbit")
        val lion = load("lion")
        val panda = load("panda")
        val elephant = load("elephant")
        wolfFrames[0] = rabbit; wolfFrames[1] = rabbit      // 狼 → 兔子
        bearFrames[0] = lion; bearFrames[1] = lion          // 熊 → 狮子
        boarFrames[0] = panda; boarFrames[1] = panda        // 野猪 → 熊猫
        snakeFrames[0] = elephant; snakeFrames[1] = elephant // 蛇 → 大象
        hedgehogFrames[0] = panda; hedgehogFrames[1] = panda // 刺猬 → 熊猫
        batFrames[0] = rabbit; batFrames[1] = rabbit         // 蝙蝠 → 兔子
        eliteFrames[0] = lion; eliteFrames[1] = lion         // 精英 → 狮子
        bossFrames[0] = elephant; bossFrames[1] = elephant   // Boss → 大象

        expBitmap = load("exp")
        bloodbagBitmap = load("bloodbag")
        bgBitmap = load("back")
        back2Bitmap = load("back2")
        back3Bitmap = load("back3")
        armsBitmap = load("arms")
        petcowBitmap = load("petcow")
        cowuogradeBitmap = load("cowuograde", 640)  // 养成页大图降采样
        skillIcons[0] = load("skill1")
        skillIcons[1] = load("skill2")
        skillIcons[2] = load("skillmax")
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

    // ================= 世界渲染（在 scale 内调用） =================
    fun renderWorld(c: Canvas, s: GameState) {
        drawBackground(c, s)
        for (pk in s.pickups) if (pk.alive) drawPickup(c, pk)
        for (e in s.enemies) if (e.alive) drawEnemy(c, e, s.survivalTime)
        for (f in s.summonCows) drawSummonCow(c, f)
        drawPlayer(c, s.player)
        // 剑在主角之上绘制（避免主角遮挡剑，看起来剑尖插在牛身上）
        drawSwords(c, s)
        for (b in s.bullets) if (b.alive) drawBullet(c, b)
        for (p in s.particles) if (p.alive) drawParticle(c, p)
    }

    private val swordRect = RectF()
    private val skillRect = RectF()
    private val enemyRect = RectF()
    private val playerRect = RectF()
    private val pickupRect = RectF()

    /** 绘制旋转大宝剑 */
    private val swordDstRect = RectF()

    /** 旋转大宝剑：剑从主角中心向外辐射，剑尖朝外，无残留特效 */
    private fun drawSwords(c: Canvas, s: GameState) {
        val p = s.player
        val bmp = armsBitmap ?: return
        val count = GameConfig.SwordConfig.count(p.level)
        val swordLen = GameConfig.SwordConfig.swordLength(p.radius, p.level)
        val orbitR = GameConfig.SwordConfig.orbitRadius(p.level)
        val baseAngle = s.orbitalAngle
        val step = (Math.PI * 2.0 / count).toFloat()

        for (i in 0 until count) {
            val a = baseAngle + i * step
            val cosA = cos(a.toDouble()).toFloat()
            val sinA = sin(a.toDouble()).toFloat()
            // 剑柄在主角外缘（p.radius），剑尖在外侧（p.radius + swordLen），剑身整体在外层不覆盖主角
            val handleX = p.x + cosA * p.radius
            val handleY = p.y + sinA * p.radius
            val tipX = p.x + cosA * (p.radius + swordLen)
            val tipY = p.y + sinA * (p.radius + swordLen)
            // 画图中心 = 剑身中点 = (柄 + 尖)/2
            val mx = (handleX + tipX) / 2f
            val my = (handleY + tipY) / 2f

            c.save()
            c.translate(mx, my)
            // arms.png 剑尖朝上，rotate +90° 让剑尖始终指向 a 方向（外侧）
            c.rotate((a * 180 / Math.PI).toFloat() + 90f)
            // arms.png 剑身长 347/384≈0.904，size = swordLen/0.904 让剑身 ≈ swordLen
            val size = swordLen / 0.904f
            swordDstRect.set(-size / 2f, -size / 2f, size / 2f, size / 2f)
            c.drawBitmap(bmp, null, swordDstRect, bitmapPaint)
            c.restore()
        }
    }

    private fun drawBackground(c: Canvas, s: GameState) {
        val bmp = bgBitmap
        if (bmp != null) {
            // 草原场景背景，拉伸铺满世界
            c.drawBitmap(bmp, null, RectF(0f, 0f, s.worldW, s.worldH), bitmapPaint)
        } else {
            c.drawColor(0xFF8FCF5A.toInt())
        }
        // 边界墙
        stroke.color = 0x552E7D32.toInt()
        stroke.strokeWidth = 12f
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
        // 怪物血条（头顶横条，受伤后显示）
        if (e.hp < e.maxHp) {
            val bw = e.radius * 2f
            val bh = 4f
            val bx = e.x - bw / 2f
            val by = e.y - e.radius - 10f
            fill.color = 0x88000000.toInt()
            c.drawRect(bx, by, bx + bw, by + bh, fill)
            val ratio = (e.hp / e.maxHp).coerceIn(0f, 1f)
            fill.color = 0xFFE53935.toInt()
            c.drawRect(bx, by, bx + bw * ratio, by + bh, fill)
        }
    }

    private fun drawSummonCow(c: Canvas, f: SummonCow) {
        val bmp = petcowBitmap ?: return
        val size = 44f
        pickupRect.set(f.x - size / 2f, f.y - size / 2f, f.x + size / 2f, f.y + size / 2f)
        c.drawBitmap(bmp, null, pickupRect, bitmapPaint)
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
                val bmp = expBitmap ?: return
                // 高级经验球（value>=10，如熊/lion 掉落）更大
                val size = if (pk.value >= 10) 28f else 18f
                pickupRect.set(pk.x - size / 2f, y - size / 2f, pk.x + size / 2f, y + size / 2f)
                c.drawBitmap(bmp, null, pickupRect, bitmapPaint)
            }
            Pickup.Type.GOLD_EXP -> {
                // 金色经验球（大怪掉落）
                fill.color = 0xFFFFD54F.toInt()
                c.drawCircle(pk.x, y, 14f, fill)
                fill.color = 0xFFFFF59D.toInt()
                c.drawCircle(pk.x - 3f, y - 3f, 4f, fill)
            }
            Pickup.Type.COIN -> {
                fill.color = 0xFFFFB300.toInt()
                c.drawCircle(pk.x, y, 8f, fill)
                fill.color = 0xFFFFE082.toInt()
                c.drawCircle(pk.x - 2f, y - 2f, 3f, fill)
            }
            Pickup.Type.HEART -> {
                val bmp = bloodbagBitmap
                if (bmp != null) {
                    val size = 26f
                    pickupRect.set(pk.x - size / 2f, y - size / 2f, pk.x + size / 2f, y + size / 2f)
                    c.drawBitmap(bmp, null, pickupRect, bitmapPaint)
                } else {
                    fill.color = 0xFFE53935.toInt()
                    c.drawCircle(pk.x, y, 9f, fill)
                }
            }
            Pickup.Type.MAGNET -> {
                // 吸铁石（U 型磁铁，蓝红两色）
                fill.color = 0xFF42A5F5.toInt()
                c.drawRect(pk.x - 12f, y - 10f, pk.x - 5f, y + 10f, fill)
                fill.color = 0xFFEF5350.toInt()
                c.drawRect(pk.x + 5f, y - 10f, pk.x + 12f, y + 10f, fill)
                fill.color = 0xFFB0BEC5.toInt()
                c.drawRect(pk.x - 12f, y - 12f, pk.x + 12f, y - 7f, fill)
                c.drawRect(pk.x - 12f, y + 7f, pk.x + 12f, y + 12f, fill)
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
        val k = sw / 720f

        // 主角头顶环形血条（红）+ 经验条（蓝）
        val px = p.x * scale
        val py = p.y * scale
        val ringR = 30f * k
        val ringCy = py - (p.radius * scale + 26f * k)
        stroke.style = Paint.Style.STROKE
        stroke.color = 0x88000000.toInt()
        stroke.strokeWidth = 7f * k
        c.drawCircle(px, ringCy, ringR, stroke)
        val hpRatio = (p.hp / p.maxHp).coerceIn(0f, 1f)
        stroke.color = 0xFFE53935.toInt()
        stroke.strokeWidth = 5f * k
        c.drawArc(RectF(px - ringR, ringCy - ringR, px + ringR, ringCy + ringR), -90f, 360f * hpRatio, false, stroke)
        val expRatio = (p.exp.toFloat() / p.expToNext).coerceIn(0f, 1f)
        stroke.color = 0xFF42A5F5.toInt()
        stroke.strokeWidth = 5f * k
        val r2 = ringR - 9f * k
        c.drawArc(RectF(px - r2, ringCy - r2, px + r2, ringCy + r2), -90f, 360f * expRatio, false, stroke)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = 18f * k
        text.textAlign = Paint.Align.CENTER
        c.drawText("Lv${p.level}", px, ringCy + 6f * k, text)
        stroke.style = Paint.Style.FILL

        // 击杀 / 金币（左上角）
        text.textSize = 24f * k
        text.textAlign = Paint.Align.LEFT
        c.drawText("击杀 ${s.kills}", pad, pad + 30f * k, text)
        c.drawText("金币 ${s.coinsEarned}", pad, pad + 62f * k, text)

        // 计时（右上）
        text.textAlign = Paint.Align.RIGHT
        text.textSize = 28f * k
        val mm = (s.survivalTime / 60).toInt()
        val ss = (s.survivalTime % 60).toInt()
        c.drawText("%02d:%02d".format(mm, ss), sw - pad, pad + 30f * k, text)
        text.textSize = 20f * k
        c.drawText("敌人 ${s.enemies.size}", sw - pad, pad + 58f * k, text)
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
            // 半透明圆底（深灰底座）
            fill.color = if (skill.isReady) 0xCC444444.toInt() else 0x88333333.toInt()
            c.drawCircle(cx, cy, r, fill)
            // 描边
            stroke.color = 0xFFFFFFFF.toInt()
            stroke.strokeWidth = 3f
            c.drawCircle(cx, cy, r, stroke)
            // 冷却遮罩
            if (!skill.isReady) {
                val ratio = (skill.currentCooldown / skill.def.cooldown).coerceIn(0f, 1f)
                fill.color = 0x88000000.toInt()
                c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * ratio, true, fill)
            }
            // 技能图标（圆形裁剪融入底座）
            val icon = skillIcons.getOrNull(i)
            if (icon != null) {
                val ir = r * 0.80f
                c.save()
                clipPath.reset()
                clipPath.addCircle(cx, cy, ir, Path.Direction.CW)
                c.clipPath(clipPath)
                skillRect.set(cx - ir, cy - ir, cx + ir, cy + ir)
                c.drawBitmap(icon, null, skillRect, bitmapPaint)
                c.restore()
                // 图标内圈描边（增强"按钮"感）
                stroke.color = 0x66FFFFFF.toInt()
                stroke.strokeWidth = 2f
                c.drawCircle(cx, cy, ir, stroke)
            } else {
                fill.color = skill.def.color
                c.drawCircle(cx, cy, r * 0.55f, fill)
            }
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
        val bg = back2Bitmap
        if (bg != null) c.drawBitmap(bg, null, RectF(0f, 0f, sw, sh), bitmapPaint)
        else c.drawColor(0xFF8FCF5A.toInt())

        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = sw * 0.11f
        c.drawText("牛牛大逃杀", sw / 2f, sh * 0.20f, text)
        text.textSize = sw * 0.045f
        c.drawText("爽快割草 · 无尽生存", sw / 2f, sh * 0.26f, text)
        text.color = 0xFFFFE082.toInt()
        c.drawText("最高分 ${save.highScore} · 金币 ${save.coins}", sw / 2f, sh * 0.31f, text)

        for (b in buttons) {
            fill.color = b.color
            c.drawRoundRect(b.rect, 24f, 24f, fill)
            text.color = 0xFFFFFFFF.toInt()
            text.textSize = sw * 0.055f
            c.drawText(b.text, b.rect.centerX(), b.rect.centerY() + sw * 0.02f, text)
        }
    }

    // ================= 养成页 =================
    private val glowColors = intArrayOf(
        0xFFFFEB3B.toInt(), 0xFF4FC3F7.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt(), 0xFFEF5350.toInt(),
    )

    fun renderShop(c: Canvas, save: SaveManager, sw: Float, sh: Float, buttons: List<UIButton>) {
        val bg = back3Bitmap
        if (bg != null) c.drawBitmap(bg, null, RectF(0f, 0f, sw, sh), bitmapPaint)
        else c.drawColor(0xFF5D4037.toInt())

        // 上方 cowuograde 升级图
        val cow = cowuogradeBitmap
        val ch = sh * 0.42f
        val cw = if (cow != null) ch * (cow.width.toFloat() / cow.height.toFloat()) else 0f
        val cowL = sw / 2f - cw / 2f
        val cowT = sh * 0.01f
        if (cow != null) {
            c.drawBitmap(cow, null, RectF(cowL, cowT, cowL + cw, cowT + ch), bitmapPaint)
        }
        // 升级彩色光芒
        if (System.currentTimeMillis() < shopGlowUntil && cow != null) {
            for (k in 0 until 16) {
                val a = (k * Math.PI * 2 / 16).toFloat() + System.currentTimeMillis() / 300f
                val gx = sw / 2f + cos(a.toDouble()).toFloat() * cw * 0.55f
                val gy = cowT + ch / 2f + sin(a.toDouble()).toFloat() * ch * 0.5f
                fill.color = glowColors[k % glowColors.size]
                c.drawCircle(gx, gy, 7f + (k % 3) * 3f, fill)
            }
        }
        // 金币
        text.textAlign = Paint.Align.CENTER
        text.color = 0xFFFFD54F.toInt()
        text.textSize = sw * 0.055f
        c.drawText("金币 ${save.coins}", sw / 2f, sh * 0.47f, text)

        // 下方属性按钮
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

    var shopGlowUntil: Long = 0L

    // ================= 合并按钮 / 游戏菜单 =================
    fun renderMenuBtn(c: Canvas, btn: RectF) {
        // 右上角合并按钮（⏸ 两条竖线）
        fill.color = 0x88000000.toInt()
        c.drawRoundRect(btn, 8f, 8f, fill)
        fill.color = 0xFFFFFFFF.toInt()
        val cx = btn.centerX()
        val cy = btn.centerY()
        val bh = btn.height() * 0.26f
        val bw = bh * 0.38f
        c.drawRect(cx - bw * 1.5f, cy - bh, cx - bw * 0.5f, cy + bh, fill)
        c.drawRect(cx + bw * 0.5f, cy - bh, cx + bw * 1.5f, cy + bh, fill)
    }

    fun renderGameMenu(c: Canvas, sw: Float, sh: Float, contBtn: RectF, homeBtn: RectF) {
        c.drawColor(0x88000000)
        fill.color = 0xFF37474F.toInt()
        c.drawRoundRect(RectF(sw * 0.12f, sh * 0.38f, sw * 0.88f, sh * 0.70f), 20f, 20f, fill)
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = sw * 0.055f
        text.textAlign = Paint.Align.CENTER
        c.drawText("游戏暂停", sw / 2f, sh * 0.44f, text)
        // 继续游戏
        fill.color = 0xFF5C9E31.toInt()
        c.drawRoundRect(contBtn, 14f, 14f, fill)
        text.textSize = sw * 0.045f
        c.drawText("继续游戏", contBtn.centerX(), contBtn.centerY() + text.textSize * 0.35f, text)
        // 回到主页
        fill.color = 0xFF7E57C2.toInt()
        c.drawRoundRect(homeBtn, 14f, 14f, fill)
        c.drawText("回到主页", homeBtn.centerX(), homeBtn.centerY() + text.textSize * 0.35f, text)
    }
}
