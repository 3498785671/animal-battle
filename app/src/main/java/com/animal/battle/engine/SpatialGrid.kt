package com.animal.battle.engine

import com.animal.battle.entity.Enemy
import kotlin.math.ceil

/**
 * 空间网格：把敌人按位置分桶，用于子弹/敌人近邻查询，
 * 避免 O(子弹数 × 敌人数) 的全量遍历。每帧 clear + 重建。
 */
class SpatialGrid(private val cellSize: Float) {
    private var cols = 1
    private var rows = 1
    private var cells = Array(cols * rows) { ArrayList<Enemy>(4) }

    fun resize(worldW: Float, worldH: Float) {
        cols = ceil(worldW / cellSize).toInt().coerceAtLeast(1)
        rows = ceil(worldH / cellSize).toInt().coerceAtLeast(1)
        cells = Array(cols * rows) { ArrayList<Enemy>(4) }
    }

    fun clear() {
        for (c in cells) c.clear()
    }

    fun insert(e: Enemy) {
        val cx = (e.x / cellSize).toInt().coerceIn(0, cols - 1)
        val cy = (e.y / cellSize).toInt().coerceIn(0, rows - 1)
        cells[cy * cols + cx].add(e)
    }

    fun query(x: Float, y: Float, radius: Float, out: MutableList<Enemy>) {
        out.clear()
        val minX = ((x - radius) / cellSize).toInt().coerceIn(0, cols - 1)
        val maxX = ((x + radius) / cellSize).toInt().coerceIn(0, cols - 1)
        val minY = ((y - radius) / cellSize).toInt().coerceIn(0, rows - 1)
        val maxY = ((y + radius) / cellSize).toInt().coerceIn(0, rows - 1)
        for (cy in minY..maxY) {
            for (cx in minX..maxX) {
                out.addAll(cells[cy * cols + cx])
            }
        }
    }
}
