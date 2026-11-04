# BiliBox

BiliBox 是一个用 Kotlin + Jetpack Compose 编写的原生 Android 哔哩哔哩客户端。项目直接调用 B 站 Web 端公开接口，提供视频搜索、在线播放、弹幕、本地视频管理、后台听视频与账号登录等常用能力，不依赖 WebView 承载主要界面。

> 本项目仅用于 Android 开发和接口学习。使用前请阅读并遵守哔哩哔哩的用户协议与相关法律法规，不要用于商业用途或高频请求。

## 功能

- **动态 / 搜索**：品牌搜索首页、搜索历史、排序与时长 / 发布时间筛选、单双列切换、分页加载。
- **在线视频**：视频详情、多 P 选集、自动连播、清晰度切换、CDN 容灾回退、离线缓存、截屏。
- **弹幕**：按分 P 拉取弹幕、滚动 / 顶部 / 底部弹幕绘制、进度同步、显示开关、不透明度 / 字号 / 显示区域 / 速度设置；登录后可从播放器直接发送弹幕。
- **本地视频**：扫描设备视频、文件夹浏览、缩略图、搜索、排序、播放进度续播、后台连播队列。
- **听视频**：关闭画面只保留音频、通知栏媒体控制、定时关闭与队列切换。
- **我的**：扫码 / 密码 / 短信登录、加密保存会话、等级与经验展示、日间 / 夜间主题。
- **播放器**：手势调亮度 / 音量、长按 2 倍速、倍速面板、锁屏、画质抽屉、媒体通知。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| 播放 | AndroidX Media3 / ExoPlayer |
| 网络 | OkHttp + kotlinx.serialization |
| 登录 | B 站 Passport 接口、扫码、极验 WebView 桥 |
| 构建 | Gradle 9.6 + AGP 9.5，minSdk 28 / targetSdk 37 |

## 目录结构

```text
.
├── app/
│   ├── src/main/java/com/example/bilibox/
│   │   ├── MainActivity.kt           # 应用入口、底部导航与页面切换
│   │   ├── HomeScreen.kt             # 顶栏、底部玻璃导航等共享 UI
│   │   ├── DynamicPage.kt            # 动态页：搜索首页、历史与结果
│   │   ├── SearchScreen.kt           # 搜索结果列表与视频卡片
│   │   ├── SearchControls.kt         # 排序 / 筛选 / 布局控制条
│   │   ├── OnlineVideoScreen.kt      # 在线视频详情、选集与播放状态
│   │   ├── PlayerScreen.kt           # 本地 / 在线共用的播放器界面
│   │   ├── LibraryScreen.kt          # 本地视频库
│   │   ├── LocalVideoDetails.kt      # 本地视频详情与播放顺序
│   │   ├── ListenMode.kt             # 听视频界面与后台播放
│   │   ├── MyPage.kt                 # 我的页、登录入口与会话存储
│   │   ├── danmaku/                  # 弹幕解析、轨道排布、绘制与设置
│   │   └── bili/                     # B 站接口客户端、模型、WBI 签名与播放源
│   ├── src/test/                     # JVM 单元测试（解析、排布、搜索等）
│   └── src/androidTest/              # Compose / 设备相关测试
├── design/
│   ├── icon/                         # 应用图标矢量源与生成脚本
│   └── prototypes/                   # 页面 HTML 设计稿（不参与构建）
├── gradle/                           # 版本目录与 Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

`design/` 里的 HTML 仅作为视觉参考，不会被编译进 APK；应用实际界面全部由 Compose 实现。

## 环境要求

- macOS / Linux / Windows，已安装 Android Studio（推荐当前稳定版）。
- JDK 25（`gradle/gradle-daemon-jvm.properties` 指定；Android Studio 自带 JBR 即可）。
- 仓库自带 Gradle Wrapper，无需单独安装 Gradle。
- Android SDK Platform 37、Build-Tools 及对应 Android SDK Platform-Tools。
- 真机或 API 28 以上的模拟器。

如果命令行没有配置 `JAVA_HOME`，可以临时指定 Android Studio 自带的 JDK：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 配置

1. 用 Android Studio 打开项目，或在项目根目录创建 `local.properties`（该文件已被 Git 忽略）：

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

2. 无需配置任何第三方 API Key。应用会直接访问 B 站公开 Web 接口。
3. 登录时在「我的」页选择扫码、账号密码或短信验证码。登录 Cookie 通过 Android Keystore 加密后保存在应用私有目录，不会写入日志或仓库。
4. Android 13 以下缓存视频或保存截屏时需要存储权限；Android 13 及以上按系统媒体权限申请。
5. 如需构建 release 包，请在本地配置签名信息，不要把 keystore、`.jks` 或密码提交到仓库。

## 构建与运行

```bash
# 编译 Debug APK（输出 app/build/outputs/apk/debug/app-debug.apk）
./gradlew assembleDebug

# 安装到已连接的设备 / 模拟器
./gradlew installDebug

# 运行 JVM 单元测试
./gradlew testDebugUnitTest

# 运行 Compose / 设备测试（需要已启动的模拟器或真机）
./gradlew connectedDebugAndroidTest
```

产物和依赖目录（`build/`、`.gradle/`、`.gradle-home/`、`.idea/`、`local.properties` 等）都已加入 `.gitignore`，不会进入版本库。

## 测试说明

- `app/src/test` 覆盖 WBI 签名、搜索历史、播放缓存、弹幕 XML / 压缩解码与轨道排布等纯逻辑。
- `app/src/androidTest` 覆盖播放器控件、手势、本地视频详情、听视频和弹幕设置弹层等 Compose 交互。
- 首次运行 Gradle 需要联网下载依赖；国内网络可自行配置 Maven 镜像，但不要把镜像的私有凭据提交到仓库。

## 已知限制

- B 站接口和风控策略可能变化，部分视频的清晰度或播放地址受账号、地区与版权限制。
- 暂未接入「我的收藏」「稍后再看」等需要账号数据接口的模块。
- 在线评论暂不展示；播放器内的额外能力以实际可用接口为准。

## License

本仓库未附带开源许可证。代码仅用于学习交流，请勿直接用于商业分发。
