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

/** 召唤的小牛：跟随玩家环绕游走，发射能量球攻击敌人 */
class SummonCow {
    var x = 0f
    var y = 0f
    var angle = 0f       // 环绕玩家角度
    var fireTimer = 0f
    var life = 0f        // 存活时间（秒）
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
    val summonCows = ArrayList<SummonCow>(8)

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
    private var spawnInterval = 2.0f
    private var bossTimer = 0f
    private var eliteTimer = 0f
    private val maxEnemies = 240

    // 环绕武器
    var orbitalAngle = 0f

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
        summonCows.clear()

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
        spawnInterval = 2.0f
        bossTimer = 0f
        eliteTimer = 0f
        orbitalAngle = 0f
        dashTimer = 0f
        slowTimer = 0f
        shakeTime = 0f
        shakePower = 0f
    }

    // ================= 主更新 =================
    fun update(dt: Float) {
        survivalTime += dt
        updatePlayer(dt)
        updateOrbitalWeapon(dt)
        updateSpawner(dt)
        updateEnemies(dt)
        updateElites(dt)
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
    }

    /** 旋转大宝剑：绕玩家旋转，剑身碰到敌人造成伤害 */
    private fun updateOrbitalWeapon(dt: Float) {
        val p = player
        val count = GameConfig.SwordConfig.count(p.level)
        val swordLen = GameConfig.SwordConfig.swordLength(p.radius, p.level)
        val orbitR = GameConfig.SwordConfig.orbitRadius(p.level)
        val dmg = p.attack * GameConfig.SwordConfig.damageMult(p.level)
        val swordWidth = 18f
        orbitalAngle += dt * GameConfig.SwordConfig.rotationSpeed(p.level)

        val step = (Math.PI * 2.0 / count).toFloat()
        for (i in 0 until count) {
            val a = orbitalAngle + i * step
            val ca = cos(a.toDouble()).toFloat()
            val sa = sin(a.toDouble()).toFloat()
            val ax = p.x + ca * orbitR
            val ay = p.y + sa * orbitR
            val bx = p.x + ca * (orbitR + swordLen)
            val by = p.y + sa * (orbitR + swordLen)
            for (e in enemies) {
                if (!e.alive || e.weaponHitCooldown > 0f) continue
                val d = distToSegment(e.x, e.y, ax, ay, bx, by)
                if (d <= e.radius + swordWidth) {
                    var dmg2 = dmg
                    if (p.critChance > 0f && Math.random() < p.critChance) dmg2 *= p.critMult
                    e.takeDamage(dmg2)
                    e.weaponHitCooldown = 0.3f
                    sound?.play(SoundManager.HIT, 0.3f)
                    emit(e.x, e.y, 2, 0xFFFFFFFF.toInt(), 80f, 3f, 0.15f)
                    if (!e.alive) onEnemyDeath(e)
                }
            }
        }
    }

    /** 点到线段的最短距离 */
    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay
        val len2 = abx * abx + aby * aby
        val t = if (len2 == 0f) 0f else ((apx * abx + apy * aby) / len2).coerceIn(0f, 1f)
        val cx = ax + t * abx
        val cy = ay + t * aby
        val dx = px - cx
        val dy = py - cy
        return sqrt(dx * dx + dy * dy)
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
            // lion（熊）出现后的阶段（120 秒）降低生成频率：下限从 0.5 提到 1.2
            val minInterval = if (survivalTime > 120f) 1.2f else 0.5f
            spawnInterval = (2.0f * 0.978f.pow(survivalTime / 8f)).coerceAtLeast(minInterval)
            spawnWave()
        }
        // 精英怪每 2 分钟一只（散射弹幕）
        eliteTimer += dt
        if (eliteTimer >= GameConfig.EliteConfig.SPAWN_INTERVAL_SEC) {
            eliteTimer = 0f
            val (x, y) = randomEdgePos()
            spawnEnemy(GameConfig.EnemyType.ELITE, x, y, 1f + survivalTime / 80f, 1f)
        }
        // Boss 每 4 分钟一只
        bossTimer += dt
        if (bossTimer >= 240f) {
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
            if (e.weaponHitCooldown > 0f) e.weaponHitCooldown -= dt
        }
    }

    /** 精英怪散射弹幕（朝玩家扇形发射） */
    private fun updateElites(dt: Float) {
        val p = player
        for (e in enemies) {
            if (!e.alive || e.type != GameConfig.EnemyType.ELITE) continue
            e.eliteFireTimer -= dt
            if (e.eliteFireTimer > 0f) continue
            // lion 阶段（120 秒）后进一步降低弹幕攻击频率，避免满屏子弹
            e.eliteFireTimer = if (survivalTime > 120f) 4.5f else GameConfig.EliteConfig.FIRE_INTERVAL
            val ang = atan2(p.y - e.y, p.x - e.x)
            val count = GameConfig.EliteConfig.FAN_COUNT
            val spread = 0.35f
            val speed = GameConfig.EliteConfig.BULLET_SPEED
            val dmg = e.damage * GameConfig.EliteConfig.BULLET_DAMAGE_MUL
            for (i in 0 until count) {
                val offset = if (count == 1) 0f else (i - (count - 1) / 2f) * spread
                val a = ang + offset
                val b = bulletPool.obtain()
                b.spawn(e.x, e.y, cos(a) * speed, sin(a) * speed, dmg, 0,
                    friendly = false, color = 0xFFEF5350.toInt(),
                    radius = GameConfig.EliteConfig.BULLET_RADIUS, life = 4f)
                bullets.add(b)
            }
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

    // ================= 召唤物（小牛） =================
    private fun updateSummons(dt: Float) {
        val p = player
        var i = 0
        while (i < summonCows.size) {
            val s = summonCows[i]
            s.life -= dt
            if (s.life <= 0f) {
                summonCows.removeAt(i)
                continue
            }
            // 环绕玩家 + 更新位置
            s.angle += 1.6f * dt
            s.x = p.x + cos(s.angle) * 60f
            s.y = p.y + sin(s.angle) * 60f
            // 发射能量球攻击最近敌人
            s.fireTimer -= dt
            if (s.fireTimer <= 0f) {
                s.fireTimer = 0.9f
                val target = nearestEnemy(p.x, p.y)
                if (target != null) {
                    val a = atan2(target.y - s.y, target.x - s.x)
                    val b = bulletPool.obtain()
                    b.spawn(s.x, s.y, cos(a) * 620f, sin(a) * 620f, p.attack * 0.5f, 0)
                    bullets.add(b)
                }
            }
            i++
        }
    }

    // ================= 碰撞 =================
    private fun checkCollisions() {
        val p = player
        // 重建网格
        grid.clear()
        for (e in enemies) if (e.alive) grid.insert(e)

        // 子弹 vs 敌人（仅玩家/友方子弹）
        for (b in bullets) {
            if (!b.alive || !b.friendly) continue
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
                    val kd = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    e.x += dx / kd * 3f
                    e.y += dy / kd * 3f
                    if (p.explosive) explodeAt(e.x, e.y, 55f, b.damage * 0.6f)
                    if (!e.alive) onEnemyDeath(e)
                    if (b.pierce > 0) b.pierce-- else b.alive = false
                    if (!b.alive) break
                }
            }
        }

        // 敌人子弹 vs 玩家
        for (b in bullets) {
            if (!b.alive || b.friendly) continue
            val dx = p.x - b.x
            val dy = p.y - b.y
            val r = p.radius + b.radius
            if (dx * dx + dy * dy <= r * r) {
                if (p.takeDamage(b.damage)) {
                    emit(p.x, p.y, 4, 0xFFEF5350.toInt(), 80f, 3f, 0.2f)
                    sound?.play(SoundManager.HURT, 0.4f)
                }
                b.alive = false
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
        // 掉落经验球：大怪（Boss/精英/熊）掉多个金色经验球，普通怪掉绿色
        val isBig = e.type == GameConfig.EnemyType.BOSS ||
            e.type == GameConfig.EnemyType.ELITE ||
            e.type == GameConfig.EnemyType.BEAR
        if (isBig) {
            val count = if (e.type == GameConfig.EnemyType.BOSS) 6 else 3
            val per = (e.exp / count).coerceAtLeast(1)
            for (i in 0 until count) {
                val ox = e.x + (Math.random() * 36 - 18).toFloat()
                val oy = e.y + (Math.random() * 36 - 18).toFloat()
                spawnPickup(Pickup.Type.GOLD_EXP, ox, oy, per)
            }
        } else {
            spawnPickup(Pickup.Type.EXP, e.x, e.y, e.exp)
        }
        // 金币
        if (e.coin > 0 && Math.random() < 0.6) spawnPickup(Pickup.Type.COIN, e.x, e.y, e.coin)
        // 血包：5% 起，随时间递增到 12%，改善后期生存
        val heartChance = (0.05f + (survivalTime / 600f) * 0.07f).coerceAtMost(0.12f)
        if (Math.random() < heartChance) spawnPickup(Pickup.Type.HEART, e.x, e.y, 1)
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
                if (gained > 0) {
                    sound?.play(SoundManager.LEVELUP, 0.6f)
                    emit(player.x, player.y, 16, 0xFFFFFFFF.toInt(), 220f, 5f, 0.6f)
                    shakeTime = 0.12f
                    shakePower = 4f
                }
                emit(pk.x, pk.y, 3, 0xFF7CE38B.toInt(), 60f, 3f, 0.25f)
            }
            Pickup.Type.GOLD_EXP -> {
                val gained = player.gainExp(pk.value)
                if (gained > 0) {
                    sound?.play(SoundManager.LEVELUP, 0.6f)
                    emit(player.x, player.y, 16, 0xFFFFFFFF.toInt(), 220f, 5f, 0.6f)
                    shakeTime = 0.12f
                    shakePower = 4f
                }
                emit(pk.x, pk.y, 4, 0xFFFFD54F.toInt(), 70f, 3f, 0.3f)
            }
            Pickup.Type.COIN -> {
                coinsEarned += (pk.value * player.coinMult).toInt().coerceAtLeast(1)
                sound?.play(SoundManager.COIN, 0.4f)
            }
            Pickup.Type.HEART -> {
                player.heal(player.maxHp * 0.1f)
                sound?.play(SoundManager.HEAL, 0.5f)
                emit(pk.x, pk.y, 8, 0xFFFF5252.toInt(), 80f, 4f, 0.3f)
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
            GameConfig.SkillId.SHIELD -> {
                // 牛盾：5 秒无敌护盾
                player.invincibleTimer = 5f
                emit(player.x, player.y, 30, 0xFF4FC3F7.toInt(), 260f, 6f, 0.8f)
            }
            GameConfig.SkillId.SUMMON_COW -> {
                // 牛召：召唤小牛辅助战斗（持续 20 秒）
                if (summonCows.size < 3) {
                    val cow = SummonCow()
                    cow.angle = (Math.random() * Math.PI * 2).toFloat()
                    cow.life = 20f
                    cow.x = player.x
                    cow.y = player.y
                    summonCows.add(cow)
                }
                emit(player.x, player.y, 24, 0xFFFFD54F.toInt(), 260f, 5f, 0.5f)
            }
            GameConfig.SkillId.THUNDER -> {
                // 牛雷：全屏雷电秒杀（高伤害 AOE）
                val dmg = player.attack * 20f
                for (e in enemies) {
                    if (!e.alive) continue
                    e.takeDamage(dmg)
                    if (!e.alive) onEnemyDeath(e)
                }
                emit(player.x, player.y, 90, 0xFF7E57C2.toInt(), 640f, 8f, 0.8f)
                emit(player.x, player.y, 40, 0xFFFFEB3B.toInt(), 520f, 6f, 0.6f)
                shakeTime = 0.6f
                shakePower = 24f
            }
        }
        return true
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
