# 📦 打包 APK 与上传 GitHub 完整指南

本指南基于你电脑的现有环境，逐步完成「构建 APK → 正式签名 → 上传 GitHub」。

## 0. 环境速查（已安装）

| 工具 | 路径 |
|---|---|
| JDK 21 | `D:\develop\jdk-21.0.12+8` |
| Android SDK | `D:\develop\AndroidSDK` |
| Gradle 8.11.1 | `D:\develop\Gradle\gradle-8.11.1` |
| Android Studio | `D:\develop\AndroidStudio\bin\studio64.exe` |

> 提示：本指南的构建命令用**已安装的 Gradle** 直接执行。项目也已准备好 `gradlew`（wrapper），
> 用 Android Studio 打开时它会自动识别 SDK，无需手动配环境变量。

---

## 1. 构建 Debug APK（可立即安装测试）

```bash
# 打开 Git Bash，设置环境变量
export JAVA_HOME="D:/develop/jdk-21.0.12+8"
export ANDROID_HOME="D:/develop/AndroidSDK"
cd /d/develop/AndroidProjects/动物大逃杀

# 构建
"/d/develop/Gradle/gradle-8.11.1/bin/gradle.bat" assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 安装到手机测试
```bash
# 手机开启「开发者选项 → USB 调试」，连接电脑
"D:/develop/AndroidSDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 2. 生成 Release 正式签名

### 2.1 生成密钥库（只做一次）
```bash
"D:/develop/jdk-21.0.12+8/bin/keytool.exe" -genkeypair -v \
  -keystore D:/develop/animal-battle.keystore \
  -alias animalbattle \
  -keyalg RSA -keysize 2048 -validity 10000
```
按提示设置密码（请牢记，后续签名都要用）。

### 2.2 配置签名（两种方式任选）

**方式 A：`keystore.properties`（推荐，密钥不入库）**

在项目根目录新建 `keystore.properties`（已被 `.gitignore` 忽略）：
```properties
storeFile=D:/develop/animal-battle.keystore
storePassword=你的密码
keyAlias=animalbattle
keyPassword=你的密码
```

然后修改 `app/build.gradle.kts`，在 `android {}` 内加入签名配置（见文件内注释示例）。

**方式 B：直接在 `app/build.gradle.kts` 写死**（简单但不安全，仅供个人项目）。

### 2.3 构建 Release APK
```bash
"/d/develop/Gradle/gradle-8.11.1/bin/gradle.bat" assembleRelease
```
产物：`app/build/outputs/apk/release/app-release.apk`

---

## 3. 游戏截图（用于 README）

用模拟器或真机运行游戏，在关键界面截图：

```bash
# 方式一：adb 截图（真机/模拟器）
"D:/develop/AndroidSDK/platform-tools/adb.exe" shell screencap -p /sdcard/menu.png
"D:/develop/AndroidSDK/platform-tools/adb.exe" pull /sdcard/menu.png docs/screenshots/menu.png
```

建议截 3 张：主菜单、战斗画面、升级三选一，命名为
`docs/screenshots/menu.png`、`gameplay.png`、`upgrade.png`（README 已引用）。

---

## 4. 上传 GitHub

### 4.1 初始化仓库并提交
```bash
cd /d/develop/AndroidProjects/动物大逃杀
git init
git add .
git commit -m "feat: 动物大逃杀 单机割草游戏 v1.0.0"
```

### 4.2 创建远程仓库
- 方式一（网页）：打开 https://github.com/new ，仓库名填 `animal-battle`，创建后复制地址。
- 方式二（gh CLI，若已安装并登录）：
  ```bash
  gh repo create animal-battle --public --source=. --push
  ```

### 4.3 关联并推送（网页方式）
```bash
git branch -M main
git remote add origin https://github.com/你的用户名/animal-battle.git
git push -u origin main
```

### 4.4 上传 APK 到 Releases
1. 打开仓库页 → **Releases** → **Create a new release**。
2. Tag 填 `v1.0.0`，标题「动物大逃杀 v1.0.0」。
3. 把 `app-release.apk` 拖入「Attach binaries」。
4. 发布后，README 中的下载链接即可指向该 APK。

---

## 5. README 编写要点（已内置，可微调）

仓库根目录的 `README.md` 已包含：项目简介、特性列表、玩法说明、下载链接、构建步骤、目录结构。
发布前只需：
1. 替换 `下载链接` 中的「你的用户名」。
2. 补上第 3 步生成的三张截图。
3. 按需修改 License 与仓库描述。

---

## 6. 常见问题

| 问题 | 解决 |
|---|---|
| 构建报 `SDK location not found` | 检查 `local.properties` 的 `sdk.dir` 是否正确 |
| `gradle.bat` 提示找不到 Java | 先 `export JAVA_HOME="D:/develop/jdk-21.0.12+8"` |
| 依赖下载慢/失败 | 已在 `settings.gradle.kts` 用 google/mavenCentral，可换阿里云镜像 |
| 手机无法安装 | 检查手机是否开启「允许安装未知来源应用」 |
| 忘记 keystore 密码 | 无法找回，需重新生成密钥并重新签名 |
