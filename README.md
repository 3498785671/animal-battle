# 🦊 动物大逃杀（Animal Battle）

一款**原生 Android（Kotlin）** 开发的单机割草类游戏。控制可爱的狐狸，在封闭地图中对抗成群刷新的狼、熊、野猪、毒蛇，自动攻击 + 华丽技能，体验满屏击杀的极致爽感。**完全离线运行，无需网络。**

<p align="center">
  <!-- 运行游戏后截图，替换下方占位 -->
  <img src="docs/screenshots/menu.png" width="200" />
  <img src="docs/screenshots/gameplay.png" width="200" />
  <img src="docs/screenshots/upgrade.png" width="200" />
</p>

## ✨ 特性

- 🎮 **爽快割草**：敌人成群刷新，自动攻击 + 满屏粒子特效 + 屏幕震动
- 🔊 **音效**：SoundPool 播放射击 / 爆炸 / 升级等 9 种合成音效
- 🦊 **狐狸主角**：虚拟摇杆移动，3 个主动技能（全屏爆发 / 无敌冲刺 / 召唤灵狐）
- 📈 **Roguelike 成长**：升级三选一强化，14 种被动 + 穿甲 / 爆裂 / 暴击 / 聚宝
- 💰 **局外养成**：金币购买永久属性，解锁兔子 / 熊猫 / 灰狼等角色
- 🏆 **无尽生存 + 高分榜**：挑战最高击杀与最长存活
- 📱 **中低端机优化**：对象池 + 空间网格 + 粒子上限，流畅 60 FPS
- 🎨 **纯代码绘制**：无需美术资源，可无缝替换为 PNG 素材

## 🎮 玩法说明

1. **移动**：左下角虚拟摇杆控制狐狸移动。
2. **攻击**：自动锁定最近敌人发射弹幕，无需操作。
3. **技能**：右下角 3 个技能按钮，冷却结束后点击释放。
4. **升级**：拾取绿色经验球升级，弹出三选一强化。
5. **存活**：血量归零即结束，击杀越多金币越多。

## 📥 下载

- **最新 APK**：前往 [Releases](https://github.com/你的用户名/animal-battle/releases) 下载 `animal-battle-v1.0.0.apk`
- 或自行构建：见下方「构建」章节。

## 🔧 构建

### 环境要求
- JDK 21
- Android SDK（compileSdk 36，build-tools 36.0.0）
- Gradle 8.11.1（项目自带 wrapper）

### 步骤

```bash
# 1. 配置 SDK 路径（local.properties）
echo "sdk.dir=D:/develop/AndroidSDK" > local.properties

# 2. 构建 debug APK
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 3. 构建 release 正式签名 APK（见 docs/打包与GitHub发布指南.md）
./gradlew assembleRelease
```

> 首次构建会自动下载 Gradle 与依赖，请保持网络畅通。

## 🗂 目录结构

```
app/src/main/java/com/animal/battle/
├── MainActivity.kt        # 入口
├── GameView.kt            # SurfaceView 游戏循环 + 状态机 + 触摸
├── GameState.kt           # 游戏世界核心模拟
├── engine/                # 对象池、空间网格
├── entity/                # 玩家/敌人/子弹/掉落物/粒子
├── render/                # Canvas 渲染
├── ui/                    # 摇杆、按钮
└── data/                  # 数值配置、存档
```

## 🛠 技术栈

- 语言：Kotlin
- 渲染：SurfaceView + Canvas（无第三方库、无游戏引擎）
- 架构：固定时间步长游戏循环 + 对象池 + 空间网格

## 📄 文档

- 游戏设计文档：[docs/GDD.md](docs/GDD.md)
- 打包与发布指南：[docs/打包与GitHub发布指南.md](docs/打包与GitHub发布指南.md)

## 📜 License

MIT License（占位，按需修改）
