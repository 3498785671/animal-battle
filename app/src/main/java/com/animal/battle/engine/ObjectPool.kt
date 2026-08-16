package com.animal.battle.engine

/**
 * 通用对象池。obtain() 取出或新建，recycle() 归还（触发 reset）。
 * 用于敌人/子弹/掉落物/粒子等高频创建销毁的对象。
 */
class ObjectPool<T>(
    private val capacity: Int,
    private val factory: () -> T,
    private val reset: (T) -> Unit,
) {
    private val free = ArrayList<T>(capacity)
    var created = 0; private set

    fun obtain(): T {
        return if (free.isNotEmpty()) free.removeAt(free.size - 1) else {
            created++
            factory()
        }
    }

    fun recycle(obj: T) {
        reset(obj)
        if (free.size < capacity) free.add(obj)
    }

    fun clear() = free.clear()
}
