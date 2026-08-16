# 🦊 动物大逃杀（Animal Battle）

一款**原生 Android（Kotlin）** 开发的单机割草类游戏。控制可爱的狐狸，在封闭地图中对抗成群刷新的狼、熊、野猪、毒蛇，自动攻击 + 华丽技能，体验满屏击杀的极致爽感。**完全离线运行，无需网络。**

<p align="center">
  <!-- 运行游戏后截图，替换下方占位 -->
  <img src="docs/screenshots/menu.png" width="200" />
  <img src="docs/screenshots/gameplay.png" width="200" />
  <img src="docs/screenshots/upgrade.png" width="200" />
</p>

## ✨ 特性

- 🎮 **爽快割草**：敌人成群刷新，**环绕旋转的能量体武器**（触碰即伤），满屏粒子 + 屏幕震动 + 升级闪光
- 🔊 **音效 + BGM**：SoundPool 短音效 + MediaPlayer 循环播放原创 8-bit BGM
- 🎨 **像素风美术**：用 Python 自动生成 24 张像素贴图（狐狸/狼/熊/野猪/蛇/刺猬/蝙蝠/精英/Boss + 五阶能量体），卡通可爱
- 🦊 **狐狸主角**：虚拟摇杆移动，3 个主动技能（全屏爆发 / 无敌冲刺 / 召唤灵狐）
- 📈 **自动升级**：等级自动强化环绕武器（更大、更多、伤害更高），按 **白→绿→蓝→紫→红** 五阶递进
- 💰 **局外养成**：金币购买永久属性，解锁兔子 / 熊猫 / 灰狼等角色
- 🏆 **无尽生存 + 高分榜**：挑战最高击杀与最长存活
- 📱 **中低端机优化**：对象池 + 空间网格 + 粒子上限，流畅 60 FPS
- ⚔️ **精英怪**：每 2 分钟生成一只精英怪，扇形散射弹幕威胁全场

## 🎮 玩法说明

1. **移动**：左下角虚拟摇杆控制狐狸移动。
2. **攻击**：自动环绕 2~6 个能量体旋转，敌人触碰即受伤（暴击可造成 2 倍伤害）。
3. **技能**：右下角 3 个技能按钮，冷却结束后点击释放大招。
4. **升级**：拾取绿色经验球自动升级，环绕能量体更大更多（白→绿→蓝→紫→红 5 阶）。
5. **存活**：血量归零即结束。每 2 分钟刷一只精英怪（散射弹幕），注意躲避。

## 📥 下载

- **最新 APK**：前往 [Releases](https://github.com/3498785671/animal-battle/releases) 下载 `动物大逃杀-v1.1.0-release.apk`
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
