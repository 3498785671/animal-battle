package com.animal.battle

import com.animal.battle.data.GameConfig
import com.animal.battle.data.SoundManager
import com.animal.battle.engine.ObjectPool
import com.animal.battle.engine.SpatialGrid
import com.animal.battle.entity.Bullet
import com.animal.battle.entity.Enemy
import com.animal.battle.entity.Particle
import com.animal.battle.entity.Pickup
import com.animal.battle.entity.Player
import com.animal.battle.entity.Skill
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** 召唤的灵狐：环绕玩家旋转并自动射击 */
class SummonFox {
    var angle = 0f
    var fireTimer = 0f
}

/**
 * 游戏世界核心模拟：持有所有实体、对象池，并驱动一帧更新。
 * 逻辑坐标：世界宽 720，高由屏幕比例决定。
 */
class GameState {

    var worldW = 720f
    var worldH = 1280f

    val player = Player()
    val enemies = ArrayList<Enemy>(300)
    val bullets = ArrayList<Bullet>(160)
    val pickups = ArrayList<Pickup>(200)
    val particles = ArrayList<Particle>(600)
    val summonFoxes = ArrayList<SummonFox>(8)

    private lateinit var enemyPool: ObjectPool<Enemy>
    private lateinit var bulletPool: ObjectPool<Bullet>
    private lateinit var pickupPool: ObjectPool<Pickup>
    private lateinit var particlePool: ObjectPool<Particle>

    val grid = SpatialGrid(96f)
    private val queryBuf = ArrayList<Enemy>(64)

    // 计时 / 分数
    var survivalTime = 0f
    var kills = 0
    var coinsEarned = 0

    // 生成
    private var spawnTimer = 0f
    private var spawnInterval = 1.1f
    private var bossTimer = 0f
    private val maxEnemies = 240

    // 升级弹窗
    var upgradeActive = false
    private var pendingUpgradeCount = 0
    val upgradeChoices = ArrayList<GameConfig.UpgradeDef>(3)

    // 冲刺
    private var dashTimer = 0f
    private var dashVx = 0f
    private var dashVy = 0f

    // 减速
    private var slowTimer = 0f

    // 屏幕震动
    var shakeTime = 0f
    var shakePower = 0f

    // 角色与永久加成
    var character = GameConfig.CHARACTERS[0]
    var permAtk = 0f
    var permHp = 0f
    var permSpeed = 0f
    var permExp = 0f
    var permCoin = 0f

    // 音效
    var sound: SoundManager? = null

    // 粒子上限（中低端机优化）
    private val maxParticles = 380

    init {
        enemyPool = ObjectPool(320, { Enemy() }, { it.alive = false })
        bulletPool = ObjectPool(200, { Bullet() }, { it.alive = false })
        pickupPool = ObjectPool(220, { Pickup() }, { it.alive = false })
        particlePool = ObjectPool(500, { Particle() }, { it.alive = false })
    }

    fun reset(worldW: Float, worldH: Float) {
        this.worldW = worldW
        this.worldH = worldH
        grid.resize(worldW, worldH)

        // 回收所有实体
        recycleAll(enemies, enemyPool)
        recycleAll(bullets, bulletPool)
        recycleAll(pickups, pickupPool)
        recycleAll(particles, particlePool)
        summonFoxes.clear()

        // 初始化玩家
        val p = player
        p.x = worldW / 2
        p.y = worldH / 2
        p.inputX = 0f
        p.inputY = 0f
        p.baseAttack = GameConfig.PLAYER_BASE_ATTACK * character.passiveAtk * (1f + permAtk)
        p.attackMult = 1f
        p.baseAttackSpeed = GameConfig.PLAYER_BASE_ATTACK_SPEED
        p.attackSpeedMult = 1f
        p.baseBullets = GameConfig.PLAYER_BASE_BULLETS
        p.bonusBullets = 0
        p.baseMoveSpeed = GameConfig.PLAYER_BASE_MOVE_SPEED * (1f + permSpeed)
        if (character.id == "rabbit") p.baseMoveSpeed *= 1.15f
        p.moveSpeedMult = 1f
        p.maxHp = GameConfig.PLAYER_BASE_HP * character.passiveHp * (1f + permHp)
        p.hp = p.maxHp
        p.armor = 0f
        p.pickupRange = GameConfig.PLAYER_BASE_PICKUP_RANGE
        p.lifesteal = 0f
        p.expMult = 1f + permExp
        p.coinMult = 1f + permCoin
        p.cooldownMult = 1f
        p.pierce = 0
        p.critChance = 0f
        p.explosive = false
        p.level = 1
        p.exp = 0
        p.expToNext = GameConfig.expToNextLevel(1)
        p.attackTimer = 0f
        p.invincibleTimer = 0f
        p.skills[0] = Skill(GameConfig.SKILLS[0])
        p.skills[1] = Skill(GameConfig.SKILLS[1])
        p.skills[2] = Skill(GameConfig.SKILLS[2])

        survivalTime = 0f
        kills = 0
        coinsEarned = 0
        spawnTimer = 0.5f
        spawnInterval = 1.1f
        bossTimer = 0f
        pendingUpgradeCount = 0
        upgradeActive = false
        dashTimer = 0f
        slowTimer = 0f
        shakeTime = 0f
        shakePower = 0f
    }

    // ================= 主更新 =================
    fun update(dt: Float) {
        survivalTime += dt
        updatePlayer(dt)
        updateSpawner(dt)
        updateEnemies(dt)
        updateBullets(dt)
        updateSummons(dt)
        checkCollisions()
        updatePickups(dt)
        updateParticles(dt)
        updateSkills(dt)

        // 清理死亡实体
        compactEnemies()
        compactBullets()
        compactPickups()
        compactParticles()

        if (shakeTime > 0f) shakeTime -= dt

        // 弹出升级三选一
        if (pendingUpgradeCount > 0 && !upgradeActive) {
            openUpgradeChoice()
            pendingUpgradeCount--
        }
    }

    // ================= 玩家 =================
    private fun updatePlayer(dt: Float) {
        val p = player
        if (dashTimer > 0f) {
            dashTimer -= dt
            p.x += dashVx * dt
            p.y += dashVy * dt
            p.invincibleTimer = 0.1f
        } else {
            var spd = p.moveSpeed
            if (slowTimer > 0f) spd *= 0.6f
            p.x += p.inputX * spd * dt
            p.y += p.inputY * spd * dt
        }
        // 边界限制
        p.x = p.x.coerceIn(p.radius, worldW - p.radius)
        p.y = p.y.coerceIn(p.radius, worldH - p.radius)

        if (p.invincibleTimer > 0f) p.invincibleTimer -= dt
        if (slowTimer > 0f) slowTimer -= dt

        // 自动攻击
        p.attackTimer += dt
        val interval = 1f / p.attackSpeed.coerceAtLeast(0.1f)
        while (p.attackTimer >= interval) {
            p.attackTimer -= interval
            fireBullets()
        }
    }

    private fun fireBullets() {
        val p = player
        val target = nearestEnemy(p.x, p.y)
        if (target == null) return
        sound?.play(SoundManager.SHOOT, 0.4f)
        val baseAngle = atan2(target.y - p.y, target.x - p.x)
        val count = p.bullets
        val spread = 0.22f
        val speed = 640f
        for (i in 0 until count) {
            val offset = if (count == 1) 0f else (i - (count - 1) / 2f) * spread
            val a = baseAngle + offset
            val b = bulletPool.obtain()
            b.spawn(p.x, p.y, cos(a) * speed, sin(a) * speed, p.attack, p.pierce)
            bullets.add(b)
        }
    }

    private fun nearestEnemy(x: Float, y: Float): Enemy? {
        var best: Enemy? = null
        var bestD = Float.MAX_VALUE
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - x
            val dy = e.y - y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD) {
                bestD = d2
                best = e
            }
        }
        return best
    }

    // ================= 敌人生成 =================
    private fun updateSpawner(dt: Float) {
        spawnTimer -= dt
        if (spawnTimer <= 0f) {
            spawnTimer = spawnInterval
            spawnInterval = (1.1f * 0.985f.pow(survivalTime / 5f)).coerceAtLeast(0.28f)
            spawnWave()
        }
        // Boss 每 90 秒一只
        bossTimer += dt
        if (bossTimer >= 90f) {
            bossTimer = 0f
            val (x, y) = randomEdgePos()
            spawnEnemy(GameConfig.EnemyType.BOSS, x, y, 1f + survivalTime / 90f, 1f)
        }
    }

    private fun spawnWave() {
        val count = (3 + survivalTime / 25f).toInt().coerceAtMost(26)
        val hpScale = 1f + survivalTime / 110f
        val speedScale = 1f + survivalTime / 320f
        for (i in 0 until count) {
            if (enemies.size >= maxEnemies) return
            val type = pickEnemyType()
            val (x, y) = randomEdgePos()
            spawnEnemy(type, x, y, hpScale, speedScale)
        }
    }

    private fun pickEnemyType(): GameConfig.EnemyType {
        val unlocked = GameConfig.ENEMY_UNLOCK_TIME.filter { it.value <= survivalTime }.keys.toList()
        val list = if (unlocked.isEmpty()) listOf(GameConfig.EnemyType.WOLF) else unlocked
        // 精英出现概率随时间上升
        return if (survivalTime > 180f && Math.random() < 0.04) GameConfig.EnemyType.ELITE
        else list[(Math.random() * list.size).toInt()]
    }

    private fun randomEdgePos(): Pair<Float, Float> {
        val side = (Math.random() * 4).toInt()
        val margin = 30f
        return when (side) {
            0 -> Pair((Math.random() * worldW).toFloat(), -margin)
            1 -> Pair((Math.random() * worldW).toFloat(), worldH + margin)
            2 -> Pair(-margin, (Math.random() * worldH).toFloat())
            else -> Pair(worldW + margin, (Math.random() * worldH).toFloat())
        }
    }

    private fun spawnEnemy(type: GameConfig.EnemyType, x: Float, y: Float, hpScale: Float, speedScale: Float) {
        val e = enemyPool.obtain()
        e.spawn(type, x, y, hpScale, speedScale)
        enemies.add(e)
    }

    // ================= 敌人 AI =================
    private fun updateEnemies(dt: Float) {
        val p = player
        for (e in enemies) {
            if (!e.alive) continue
            val dx = p.x - e.x
            val dy = p.y - e.y
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            e.angle = atan2(dy, dx)
            val sp = if (e.isCharging) e.speed * 2.6f else e.speed
            e.vx = dx / dist * sp
            e.vy = dy / dist * sp

            // 野猪冲锋
            if (e.type == GameConfig.EnemyType.BOAR) {
                if (e.chargeTimer > 0f) {
                    e.chargeTimer -= dt
                    if (e.chargeTimer <= 0f) e.isCharging = false
                } else if (Math.random() < dt * 0.6f && dist < 520f) {
                    e.isCharging = true
                    e.chargeTimer = 1.0f
                }
            }

            // 蝙蝠：沿朝向垂直方向正弦摆动
            if (e.type == GameConfig.EnemyType.BAT) {
                val wobble = sin(e.wobblePhase + survivalTime * 7f)
                e.x += (-dy / dist) * wobble * 55f * dt
                e.y += (dx / dist) * wobble * 55f * dt
            }

            e.x += e.vx * dt
            e.y += e.vy * dt
            e.x = e.x.coerceIn(e.radius, worldW - e.radius)
            e.y = e.y.coerceIn(e.radius, worldH - e.radius)

            if (e.hitFlash > 0f) e.hitFlash -= dt
            if (e.bornTimer > 0f) e.bornTimer -= dt
        }
    }

    // ================= 子弹 =================
    private fun updateBullets(dt: Float) {
        for (b in bullets) {
            if (!b.alive) continue
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.life -= dt
            if (b.life <= 0f || b.x < -40 || b.x > worldW + 40 || b.y < -40 || b.y > worldH + 40) {
                b.alive = false
            }
        }
    }

    // ================= 召唤物 =================
    private fun updateSummons(dt: Float) {
        val p = player
        for (s in summonFoxes) {
            s.angle += 1.9f * dt
            s.fireTimer -= dt
            if (s.fireTimer <= 0f) {
                s.fireTimer = 0.7f
                val target = nearestEnemy(p.x, p.y)
                if (target != null) {
                    val sx = p.x + cos(s.angle) * 70f
                    val sy = p.y + sin(s.angle) * 70f
                    val a = atan2(target.y - sy, target.x - sx)
                    val b = bulletPool.obtain()
                    b.spawn(sx, sy, cos(a) * 600f, sin(a) * 600f, p.attack * 0.6f, 0)
                    bullets.add(b)
                }
            }
        }
    }

    // ================= 碰撞 =================
    private fun checkCollisions() {
        val p = player
        // 重建网格
        grid.clear()
        for (e in enemies) if (e.alive) grid.insert(e)

        // 子弹 vs 敌人
        for (b in bullets) {
            if (!b.alive) continue
            grid.query(b.x, b.y, b.radius + 64f, queryBuf)
            for (e in queryBuf) {
                if (!e.alive) continue
                val dx = e.x - b.x
                val dy = e.y - b.y
                val r = e.radius + b.radius
                if (dx * dx + dy * dy <= r * r) {
                    // 暴击判定
                    var dmg = b.damage
                    if (p.critChance > 0f && Math.random() < p.critChance) dmg *= p.critMult
                    e.takeDamage(dmg)
                    sound?.play(SoundManager.HIT, 0.35f)
                    // 轻微击退
                    val kd = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    e.x += dx / kd * 3f
                    e.y += dy / kd * 3f
                    // 爆裂弹：命中时范围伤害
                    if (p.explosive) {
                        explodeAt(e.x, e.y, 55f, b.damage * 0.6f)
                    }
                    if (!e.alive) onEnemyDeath(e)
                    if (b.pierce > 0) b.pierce-- else b.alive = false
                    if (!b.alive) break
                }
            }
        }

        // 敌人 vs 玩家
        for (e in enemies) {
            if (!e.alive) continue
            val dx = p.x - e.x
            val dy = p.y - e.y
            val r = e.radius + p.radius
            if (dx * dx + dy * dy <= r * r) {
                if (p.takeDamage(e.damage)) {
                    emit(p.x, p.y, 8, 0xFFFF5252.toInt(), 140f, 4f, 0.3f)
                    sound?.play(SoundManager.HURT, 0.5f)
                    if (e.type == GameConfig.EnemyType.SNAKE) slowTimer = 1.5f
                }
            }
        }
    }

    private fun onEnemyDeath(e: Enemy) {
        kills++
        sound?.play(SoundManager.EXPLOSION, 0.5f)
        // 掉落经验球
        spawnPickup(Pickup.Type.EXP, e.x, e.y, e.exp)
        // 金币
        if (e.coin > 0 && Math.random() < 0.6) spawnPickup(Pickup.Type.COIN, e.x, e.y, e.coin)
        // 血包
        if (Math.random() < 0.025) spawnPickup(Pickup.Type.HEART, e.x, e.y, 1)
        // 爆炸粒子
        val count = if (e.type == GameConfig.EnemyType.BOSS) 40 else if (e.type == GameConfig.EnemyType.ELITE) 24 else 12
        emit(e.x, e.y, count, e.type.color, 200f, 5f, 0.5f)
        // 吸血
        if (player.lifesteal > 0f) player.heal(player.lifesteal)
        // 精英/Boss 屏幕震动
        if (e.type == GameConfig.EnemyType.ELITE || e.type == GameConfig.EnemyType.BOSS) {
            shakeTime = 0.3f
            shakePower = 12f
        }
    }

    /** 爆炸范围伤害（爆裂弹） */
    private fun explodeAt(x: Float, y: Float, radius: Float, damage: Float) {
        val r2 = radius * radius
        for (e in enemies) {
            if (!e.alive) continue
            val dx = e.x - x
            val dy = e.y - y
            if (dx * dx + dy * dy <= r2) {
                e.takeDamage(damage)
                if (!e.alive) onEnemyDeath(e)
            }
        }
        emit(x, y, 10, 0xFFFFB300.toInt(), 160f, 4f, 0.3f)
    }

    // ================= 掉落物 =================
    private fun spawnPickup(type: Pickup.Type, x: Float, y: Float, value: Int) {
        val pk = pickupPool.obtain()
        pk.spawn(type, x, y, value)
        pickups.add(pk)
    }

    private fun updatePickups(dt: Float) {
        val p = player
        for (pk in pickups) {
            if (!pk.alive) continue
            pk.life -= dt
            if (pk.life <= 0f) { pk.alive = false; continue }
            val dx = p.x - pk.x
            val dy = p.y - pk.y
            val d2 = dx * dx + dy * dy
            val range = p.pickupRange
            if (d2 <= range * range) pk.attracted = true
            if (pk.attracted) {
                val d = sqrt(d2).coerceAtLeast(1f)
                val sp = 520f
                pk.x += dx / d * sp * dt
                pk.y += dy / d * sp * dt
                if (d < p.radius + 10f) {
                    collectPickup(pk)
                    pk.alive = false
                }
            }
        }
    }

    private fun collectPickup(pk: Pickup) {
        when (pk.type) {
            Pickup.Type.EXP -> {
                val gained = player.gainExp(pk.value)
                pendingUpgradeCount += gained
                if (gained > 0) sound?.play(SoundManager.LEVELUP, 0.5f)
                emit(pk.x, pk.y, 3, 0xFF7CE38B.toInt(), 60f, 3f, 0.25f)
            }
            Pickup.Type.COIN -> {
                coinsEarned += (pk.value * player.coinMult).toInt().coerceAtLeast(1)
                sound?.play(SoundManager.COIN, 0.4f)
            }
            Pickup.Type.HEART -> {
                player.heal(player.maxHp * 0.2f)
                sound?.play(SoundManager.HEAL, 0.5f)
                emit(pk.x, pk.y, 8, 0xFFFF6B6B.toInt(), 80f, 4f, 0.3f)
            }
        }
    }

    // ================= 粒子 =================
    private fun emit(x: Float, y: Float, count: Int, color: Int, speed: Float, size: Float, life: Float) {
        var n = count
        if (particles.size + n > maxParticles) n = (maxParticles - particles.size).coerceAtLeast(0)
        for (i in 0 until n) {
            val a = (Math.random() * Math.PI * 2).toFloat()
            val sp = (Math.random() * speed).toFloat()
            val pt = particlePool.obtain()
            pt.spawn(x, y, cos(a) * sp, sin(a) * sp, life * (0.6f + Math.random().toFloat() * 0.6f), size, color)
            particles.add(pt)
        }
    }

    private fun updateParticles(dt: Float) {
        for (pt in particles) {
            if (pt.alive) pt.update(dt)
        }
    }

    // ================= 技能 =================
    private fun updateSkills(dt: Float) {
        for (s in player.skills) {
            s?.let { if (it.currentCooldown > 0f) it.currentCooldown -= dt }
        }
    }

    fun useSkill(index: Int): Boolean {
        val skill = player.skills[index] ?: return false
        if (!skill.isReady) return false
        skill.startCooldown(player.cooldownMult)
        sound?.play(SoundManager.SKILL, 0.5f)
        when (skill.def.id) {
            GameConfig.SkillId.FIRE_BLAST -> {
                val dmg = player.attack * 8f
                for (e in enemies) {
                    if (!e.alive) continue
                    e.takeDamage(dmg)
                    if (!e.alive) onEnemyDeath(e)
                }
                emit(player.x, player.y, 60, 0xFFFF7043.toInt(), 480f, 7f, 0.7f)
                shakeTime = 0.4f
                shakePower = 16f
            }
            GameConfig.SkillId.DASH -> {
                var dx = player.inputX
                var dy = player.inputY
                if (dx == 0f && dy == 0f) dy = -1f
                val len = sqrt(dx * dx + dy * dy)
                dx /= len; dy /= len
                dashVx = dx * 1600f
                dashVy = dy * 1600f
                dashTimer = 0.18f
                // 路径伤害
                for (e in enemies) {
                    if (!e.alive) continue
                    val ex = e.x - player.x
                    val ey = e.y - player.y
                    val d2 = ex * ex + ey * ey
                    if (d2 <= 140f * 140f) {
                        e.takeDamage(player.attack * 2f)
                        if (!e.alive) onEnemyDeath(e)
                    }
                }
                emit(player.x, player.y, 20, 0xFF42A5F5.toInt(), 300f, 5f, 0.4f)
            }
            GameConfig.SkillId.SUMMON -> {
                if (summonFoxes.size < 4) {
                    summonFoxes.add(SummonFox().apply { angle = (Math.random() * Math.PI * 2).toFloat() })
                }
                emit(player.x, player.y, 24, 0xFFFFD54F.toInt(), 260f, 5f, 0.5f)
            }
        }
        return true
    }

    // ================= 升级三选一 =================
    private fun openUpgradeChoice() {
        upgradeChoices.clear()
        val pool = GameConfig.UPGRADES.toMutableList()
        for (i in 0 until 3) {
            if (pool.isEmpty()) break
            val idx = (Math.random() * pool.size).toInt()
            upgradeChoices.add(pool.removeAt(idx))
        }
        upgradeActive = true
    }

    fun chooseUpgrade(def: GameConfig.UpgradeDef) {
        applyUpgrade(def.id)
        upgradeActive = false
    }

    private fun applyUpgrade(id: GameConfig.UpgradeId) {
        val p = player
        when (id) {
            GameConfig.UpgradeId.ATK -> p.attackMult += 0.20f
            GameConfig.UpgradeId.ATK_SPEED -> p.attackSpeedMult += 0.15f
            GameConfig.UpgradeId.BULLETS -> p.bonusBullets += 1
            GameConfig.UpgradeId.MOVE_SPEED -> p.moveSpeedMult += 0.12f
            GameConfig.UpgradeId.MAX_HP -> {
                p.maxHp += 25f
                p.hp += 25f
            }
            GameConfig.UpgradeId.ARMOR -> p.armor = (p.armor + 0.10f).coerceAtMost(0.7f)
            GameConfig.UpgradeId.PICKUP_RANGE -> p.pickupRange *= 1.4f
            GameConfig.UpgradeId.LIFESTEAL -> p.lifesteal += 2f
            GameConfig.UpgradeId.COOLDOWN -> p.cooldownMult *= 0.85f
            GameConfig.UpgradeId.HEAL -> p.heal(p.maxHp * 0.4f)
            GameConfig.UpgradeId.PIERCE -> p.pierce += 1
            GameConfig.UpgradeId.EXPLOSIVE -> p.explosive = true
            GameConfig.UpgradeId.CRIT -> p.critChance += 0.15f
            GameConfig.UpgradeId.COIN_GAIN -> p.coinMult += 0.25f
        }
    }

    // ================= 实体清理（对象池回收） =================
    private fun compactEnemies() {
        var w = 0
        for (i in enemies.indices) {
            val e = enemies[i]
            if (e.alive) enemies[w++] = e else enemyPool.recycle(e)
        }
        while (enemies.size > w) enemies.removeAt(enemies.size - 1)
    }

    private fun compactBullets() {
        var w = 0
        for (i in bullets.indices) {
            val b = bullets[i]
            if (b.alive) bullets[w++] = b else bulletPool.recycle(b)
        }
        while (bullets.size > w) bullets.removeAt(bullets.size - 1)
    }

    private fun compactPickups() {
        var w = 0
        for (i in pickups.indices) {
            val pk = pickups[i]
            if (pk.alive) pickups[w++] = pk else pickupPool.recycle(pk)
        }
        while (pickups.size > w) pickups.removeAt(pickups.size - 1)
    }

    private fun compactParticles() {
        var w = 0
        for (i in particles.indices) {
            val pt = particles[i]
            if (pt.alive) particles[w++] = pt else particlePool.recycle(pt)
        }
        while (particles.size > w) particles.removeAt(particles.size - 1)
    }

    private fun <T> recycleAll(list: ArrayList<T>, pool: ObjectPool<T>) {
        for (item in list) pool.recycle(item)
        list.clear()
    }
}
