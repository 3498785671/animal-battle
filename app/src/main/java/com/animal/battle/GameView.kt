package com.animal.battle

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.media.MediaPlayer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.animal.battle.data.GameConfig
import com.animal.battle.data.SaveManager
import com.animal.battle.data.SoundManager
import com.animal.battle.render.Renderer
import com.animal.battle.ui.Joystick
import com.animal.battle.ui.UIButton

/**
 * 游戏主视图：SurfaceView + 独立渲染线程（固定时间步长），
 * 管理菜单/养成/战斗/结算的状态机与全部触摸交互。
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private val renderer = Renderer(context)
    val save = SaveManager(context)
    private val state = GameState()
    private val sound = SoundManager(context)
    private var bgm: MediaPlayer? = null

    @Volatile
    private var mode = GameMode.MENU

    private enum class GameMode { MENU, SHOP, PLAYING, GAME_OVER }

    // 屏幕尺寸
    private var screenW = 0f
    private var screenH = 0f
    private var scale = 1f
    private var worldH = 1280f

    // UI
    private val joystick = Joystick()
    private val skillButtons = ArrayList<UIButton>()
    private val menuButtons = ArrayList<UIButton>()
    private val shopButtons = ArrayList<UIButton>()
    private val gameOverButtons = ArrayList<UIButton>()
    private val pauseBtn = RectF()
    private val homeBtn = RectF()
    private val confirmYesBtn = RectF()
    private val confirmNoBtn = RectF()

    // 状态
    @Volatile
    private var paused = false
    @Volatile
    private var homeConfirmActive = false

    // 线程
    @Volatile
    private var running = false
    private var thread: Thread? = null

    init {
        holder.addCallback(this)
        isFocusable = true
        state.sound = sound
    }

    // ================= 生命周期 =================
    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        if (thread == null || !thread!!.isAlive) {
            thread = Thread(GameThread(), "GameThread")
            thread!!.start()
        }
        // 回到前台时若仍在游戏中，恢复 BGM
        if (mode == GameMode.PLAYING && !paused) resumeBgm()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        screenW = width.toFloat()
        screenH = height.toFloat()
        scale = screenW / GameConfig.WORLD_W
        worldH = screenH / scale
        layoutAll()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        // 切到后台时暂停 BGM（不释放）
        pauseBgm()
        thread?.join(600)
        thread = null
    }

    // ================= 游戏线程 =================
    private inner class GameThread : Runnable {
        override fun run() {
            var last = System.nanoTime()
            var acc = 0f
            val fixedDt = 1f / 60f
            while (running) {
                val now = System.nanoTime()
                val frame = ((now - last) / 1e9f).coerceAtMost(0.25f)
                last = now
                acc += frame
                while (acc >= fixedDt) {
                    if (mode == GameMode.PLAYING && !paused && !state.player.isDead) {
                        synchronized(state) {
                            state.player.inputX = joystick.inputX
                            state.player.inputY = joystick.inputY
                            state.update(fixedDt)
                        }
                        if (state.player.isDead) onGameOver()
                    }
                    acc -= fixedDt
                }
                render()
            }
        }
    }

    private fun render() {
        if (screenW <= 0f) return
        val canvas: Canvas = holder.lockCanvas() ?: return
        try {
            when (mode) {
                GameMode.MENU -> renderer.renderMenu(canvas, save, screenW, screenH, menuButtons)
                GameMode.SHOP -> renderer.renderShop(canvas, save, screenW, screenH, shopButtons)
                GameMode.PLAYING, GameMode.GAME_OVER -> {
                    var sx = 0f
                    var sy = 0f
                    if (state.shakeTime > 0f) {
                        sx = (Math.random() * 2 - 1).toFloat() * state.shakePower
                        sy = (Math.random() * 2 - 1).toFloat() * state.shakePower
                    }
                    canvas.save()
                    canvas.translate(sx, sy)
                    canvas.scale(scale, scale)
                    renderer.renderWorld(canvas, state)
                    canvas.restore()

                    renderer.renderHUD(canvas, state, screenW, screenH, scale)
                    renderer.renderJoystick(canvas, joystick)
                    renderer.renderSkillButtons(canvas, state, skillButtons)
                    if (mode == GameMode.PLAYING) renderer.renderPauseHome(canvas, pauseBtn, homeBtn)

                    if (homeConfirmActive) {
                        renderer.renderHomeConfirm(canvas, screenW, screenH, confirmYesBtn, confirmNoBtn)
                    } else if (paused && mode == GameMode.PLAYING) {
                        renderer.renderPauseOverlay(canvas, screenW, screenH)
                    }

                    if (mode == GameMode.GAME_OVER) {
                        renderer.renderGameOver(canvas, state, save, screenW, screenH, gameOverButtons)
                    }
                }
            }
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    // ================= 触摸 =================
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (mode) {
            GameMode.MENU -> handleTapButtons(event, menuButtons) { onMenuAction(it) }
            GameMode.SHOP -> handleTapButtons(event, shopButtons) { onShopAction(it) }
            GameMode.PLAYING -> handlePlayingTouch(event)
            GameMode.GAME_OVER -> handleTapButtons(event, gameOverButtons) { onGameOverAction(it) }
        }
        return true
    }

    private var pressedButtonId: String? = null

    private fun handleTapButtons(event: MotionEvent, buttons: List<UIButton>, onAction: (String) -> Unit) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                for (b in buttons) if (b.hit(x, y)) { pressedButtonId = b.id; break }
            }
            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y
                val id = pressedButtonId
                pressedButtonId = null
                if (id != null) {
                    val b = buttons.firstOrNull { it.id == id }
                    if (b != null && b.hit(x, y)) onAction(id)
                }
            }
        }
    }

    private fun handlePlayingTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)
                // 主页确认弹层
                if (homeConfirmActive) {
                    if (confirmYesBtn.contains(x, y)) { backToMenu(); return }
                    if (confirmNoBtn.contains(x, y)) { homeConfirmActive = false; return }
                    return
                }
                // 暂停键
                if (pauseBtn.contains(x, y)) {
                    paused = !paused
                    if (paused) pauseBgm() else resumeBgm()
                    return
                }
                // 主页键（弹确认）
                if (homeBtn.contains(x, y)) {
                    paused = true
                    pauseBgm()
                    homeConfirmActive = true
                    return
                }
                if (paused) return
                // 技能按钮
                for ((i, b) in skillButtons.withIndex()) {
                    if (b.hit(x, y)) {
                        synchronized(state) { state.useSkill(i) }
                        return
                    }
                }
                joystick.begin(x, y, id)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (event.getPointerId(i) == joystick.pointerId) {
                        joystick.update(event.getX(i), event.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                joystick.end(event.getPointerId(event.actionIndex))
            }
        }
    }

    // ================= 布局 =================
    private fun layoutAll() {
        val w = screenW
        val h = screenH

        joystick.setBase(w * 0.16f, h * 0.80f)
        joystick.baseRadius = w * 0.13f
        joystick.knobRadius = w * 0.06f

        skillButtons.clear()
        val r = w * 0.082f
        val cy = h * 0.83f
        for (i in 0 until 3) {
            val cx = w - r - i * (r * 2.35f)
            skillButtons.add(UIButton("skill_$i", RectF(cx - r, cy - r, cx + r, cy + r), "", ""))
        }

        menuButtons.clear()
        menuButtons.add(UIButton("start", RectF(w * 0.18f, h * 0.40f, w * 0.82f, h * 0.50f), "开始游戏", "", true, 0xFF5C9E31.toInt()))
        menuButtons.add(UIButton("shop", RectF(w * 0.18f, h * 0.54f, w * 0.82f, h * 0.64f), "养 成", "", true, 0xFF7E57C2.toInt()))

        gameOverButtons.clear()
        gameOverButtons.add(UIButton("retry", RectF(w * 0.2f, h * 0.60f, w * 0.8f, h * 0.68f), "再来一局", "", true, 0xFF5C9E31.toInt()))
        gameOverButtons.add(UIButton("menu", RectF(w * 0.2f, h * 0.72f, w * 0.8f, h * 0.80f), "返回菜单", "", true, 0xFF7E57C2.toInt()))

        // 暂停 / 主页键（右上角，计时下方）
        pauseBtn.set(w - w * 0.16f, h * 0.10f, w - w * 0.02f, h * 0.17f)
        homeBtn.set(w - w * 0.16f, h * 0.19f, w - w * 0.02f, h * 0.26f)
        // 主页确认弹层按钮
        confirmYesBtn.set(w * 0.22f, h * 0.50f, w * 0.48f, h * 0.58f)
        confirmNoBtn.set(w * 0.52f, h * 0.50f, w * 0.78f, h * 0.58f)
    }

    private fun buildShopButtons() {
        shopButtons.clear()
        val w = screenW
        val h = screenH
        shopButtons.add(UIButton("back", RectF(w * 0.06f, h * 0.035f, w * 0.28f, h * 0.085f), "返回", "", true, 0xFF7E57C2.toInt()))

        var y = h * 0.52f
        val itemH = h * 0.065f
        for (def in GameConfig.PERM_UPGRADES) {
            val lv = save.permLevel(def.id)
            val cost = save.permNextCost(def)
            val sub = if (cost < 0) "已满级" else "Lv $lv · $cost 金币"
            shopButtons.add(UIButton("perm_${def.id.name}", RectF(w * 0.05f, y, w * 0.95f, y + itemH), def.name, sub, cost >= 0 && save.coins >= cost, 0xFF3E6B2E.toInt()))
            y += itemH + h * 0.012f
        }
    }

    // ================= 状态动作 =================
    private fun onMenuAction(id: String) {
        when (id) {
            "start" -> startGame()
            "shop" -> {
                mode = GameMode.SHOP
                buildShopButtons()
            }
        }
    }

    private fun onShopAction(id: String) {
        when {
            id == "back" -> mode = GameMode.MENU
            id.startsWith("perm_") -> {
                val pid = GameConfig.PermUpgradeId.valueOf(id.removePrefix("perm_"))
                val def = GameConfig.PERM_UPGRADES.first { it.id == pid }
                val cost = save.permNextCost(def)
                if (cost >= 0 && save.coins >= cost) {
                    save.addCoins(-cost)
                    save.setPermLevel(pid, save.permLevel(pid) + 1)
                    renderer.shopGlowUntil = System.currentTimeMillis() + 700  // 升级光芒
                    buildShopButtons()
                }
            }
        }
    }

    private fun onGameOverAction(id: String) {
        when (id) {
            "retry" -> startGame()
            "menu" -> mode = GameMode.MENU
        }
    }

    private fun backToMenu() {
        paused = false
        homeConfirmActive = false
        stopBgm()
        mode = GameMode.MENU
    }

    private fun startGame() {
        val char = GameConfig.CHARACTERS.firstOrNull { it.id == save.currentCharacter } ?: GameConfig.CHARACTERS[0]
        state.character = char
        paused = false
        homeConfirmActive = false
        state.permAtk = save.permLevel(GameConfig.PermUpgradeId.ATK) * 0.10f
        state.permHp = save.permLevel(GameConfig.PermUpgradeId.HP) * 0.10f
        state.permSpeed = save.permLevel(GameConfig.PermUpgradeId.SPEED) * 0.04f
        state.permExp = save.permLevel(GameConfig.PermUpgradeId.EXP) * 0.10f
        state.permCoin = save.permLevel(GameConfig.PermUpgradeId.COIN) * 0.10f
        state.reset(GameConfig.WORLD_W, worldH)
        startBgm()
        mode = GameMode.PLAYING
    }

    private fun startBgm() {
        if (bgm != null) return
        bgm = MediaPlayer.create(context, com.animal.battle.R.raw.bgm)?.apply {
            isLooping = true
            setVolume(0.35f, 0.35f)
            start()
        }
    }

    private fun stopBgm() {
        bgm?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        bgm = null
    }

    private fun pauseBgm() {
        bgm?.let { if (it.isPlaying) try { it.pause() } catch (_: Exception) {} }
    }

    private fun resumeBgm() {
        bgm?.let { if (!it.isPlaying) try { it.start() } catch (_: Exception) {} }
    }

    private fun onGameOver() {
        save.highScore = maxOf(save.highScore, state.kills)
        save.bestKills = maxOf(save.bestKills, state.kills)
        save.bestTime = maxOf(save.bestTime, state.survivalTime)
        save.addCoins(state.coinsEarned)
        sound.play(SoundManager.GAMEOVER, 0.6f)
        stopBgm()
        mode = GameMode.GAME_OVER
    }
}
