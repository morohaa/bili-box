# BiliBox

BiliBox 是一个用 Kotlin + Jetpack Compose 编写的原生 Android 哔哩哔哩客户端。项目直接调用 B 站 Web 端公开接口，提供视频搜索、在线播放、弹幕、本地视频管理、后台听视频与账号登录等常用能力，不依赖 WebView 承载主要界面。

> 本项目是非官方项目，仅用于 Android 开发和接口学习。使用前请阅读并遵守哔哩哔哩的用户协议与相关法律法规，不要用于商业用途或高频请求。BiliBox 与哔哩哔哩官方无关联。

## 当前版本

`1.0.0-beta.1` 为首个公开测试候选版，支持 Android 9 及以上系统。功能和限制见 [更新日志](CHANGELOG.md)，实际测试范围见 [发布验证记录](docs/releases/1.0.0-beta.1-validation.md)。

## 功能

- **首页 / 搜索**：品牌搜索首页、搜索历史、排序与时长 / 发布时间筛选、单双列切换、分页加载。
- **在线视频**：视频详情、多 P 选集、自动连播、清晰度切换、CDN 容灾回退、离线缓存、截屏。
- **弹幕**：按分 P 拉取弹幕、滚动 / 顶部 / 底部弹幕绘制、进度同步、显示开关、不透明度 / 字号 / 显示区域 / 速度设置；登录后可从播放器直接发送弹幕。
- **本地视频**：主题色压暗的渐变头部 + 顶部搜索，叠层文件夹封面与轻量分隔列表、快捷筛选、10 种排序、列表 / 网格布局；显示设置弹窗可分别开关视频字段与文件夹字段，支持播放进度续播与后台连播队列。
- **听视频**：关闭画面只保留音频、通知栏媒体控制、定时关闭与队列切换。
- **我的**：扫码 / 密码 / 短信登录、加密保存会话、等级与经验展示；头图全屏预览，支持恢复默认、保存到相册与从相册更换；预览可调暗化 / 模糊并保存，选图后可拖动 / 缩放裁剪 16:9 画面。
- **我的收藏**：读取当前 B 站账号创建的收藏夹（含私密收藏夹）及收藏视频；支持分页、夹内搜索、按收藏时间 / 发布时间 / 播放量排序、下拉刷新与点击播放，返回时保留浏览位置；失效视频显示原因。
- **主题外观**：12 套品牌色、浅色 / 深色 / 自动模式、首页背景样式；自定义背景全屏预览支持暗化遮罩与背景模糊，主题色同步到首页、导航与个人页头图。
- **播放器**：左滑后退 / 右滑快进、手势调亮度 / 音量、长按 2 倍速、倍速面板、锁屏、画质抽屉、媒体通知。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.2 |
| UI | Jetpack Compose + Material 3 |
| 播放 | AndroidX Media3 / ExoPlayer |
| 网络 | OkHttp + kotlinx.serialization |
| 登录 | B 站 Passport 接口、扫码、极验 WebView 桥 |
| 包名 | `io.github.bilibox` |
| 构建 | Gradle 9.6 + AGP 9.5，minSdk 28 / targetSdk 37 |

## 目录结构

源码采用 feature-first 的单模块结构；源码根包下按职责分为应用组装层 `app`、共享层 `core`、数据层 `data` 和功能层 `feature`。

```text
.
├── app/src/main/java/io/github/bilibox/
│   ├── MainActivity.kt                # Activity 入口与 Compose 挂载
│   ├── app/
│   │   ├── BiliBoxApp.kt              # 根布局、主题与系统栏外观
│   │   ├── AppNavigation.kt           # 顶层标签、页面切换与播放覆盖层
│   │   └── BiliBoxAppState.kt         # 会话、导航与搜索状态组装及恢复
│   ├── core/
│   │   ├── playback/                  # 队列协议、播放模式、倍速规则、定时状态与后台媒体会话
│   │   ├── model/                     # 跨功能共享的视频摘要与搜索业务选项
│   │   ├── ui/component/              # 顶栏、封面、图标与均衡器等共享 UI
│   │   │   └── playback/              # 播放器与听视频共用的倍速、定时关闭面板
│   │   ├── ui/theme/                  # 颜色、主题、字体、动态色板与主题色板
│   │   └── util/                      # 格式化、截帧等无业务工具
│   ├── data/
│   │   ├── appearance/                # 主题外观偏好持久化
│   │   ├── auth/                      # B 站通行证请求、会话加密存储
│   │   ├── bili/                      # B 站接口、模型、播放源与 WBI 签名
│   │   ├── danmaku/                   # 弹幕模型、解析与显示偏好持久化
│   │   ├── favorites/                 # 账号收藏夹与收藏视频读取
│   │   ├── localvideo/                # 本地视频扫描、缩略图、播放队列与进度
│   │   ├── online/                    # 在线媒体下载与 MediaStore 持久化
│   │   ├── profile/                   # 自定义头图裁切与私有目录持久化
│   │   └── search/                    # 搜索历史存储与视频摘要转换
│   └── feature/
│       ├── appearance/                # 主题外观页、主题网格与背景弹层
│       ├── auth/                      # 登录、扫码与极验 UI
│       ├── danmaku/                   # 弹幕层与播放器弹幕控制
│       │   └── layout/                # 弹幕轨道、速度、碰撞与显示时间排布
│       ├── favorites/                # 收藏夹浏览、搜索、排序和播放
│       ├── home/                      # 首页容器与搜索入口
│       ├── library/                   # 本地视频库、文件夹与筛选
│       ├── listen/                    # 听视频页面与播放状态绑定
│       ├── online/                    # 在线视频详情、页面播放状态、选集与缓存
│       ├── player/                    # 播放器页面、控制条、手势与定时播放绑定
│       │   └── details/               # 本地播放简介、媒体信息、同文件夹队列与播放顺序
│       ├── profile/                   # 我的页、头图预览与自定义头图
│       └── search/                    # 搜索页面状态、结果与筛选控件及文案
├── app/src/test/                      # JVM 单元测试（与主代码包镜像）
├── app/src/androidTest/               # Compose / 设备测试（与主代码包镜像）
├── design/
│   ├── icon/                          # 应用图标矢量源与生成脚本
│   └── prototypes/                    # 页面 HTML 设计稿（不参与构建）
├── docs/architecture.md               # 依赖方向、播放关系与状态归属
├── scripts/
│   ├── ktlint.sh                      # 固定版本的 Kotlin 格式检查与修复
│   ├── check_architecture.py          # 依赖边界与功能包循环检查
│   ├── setup_release_signing.py       # 发布签名初始化与本机备份校验
│   └── tests/                         # 架构检查器回归测试
├── .github/
│   ├── ISSUE_TEMPLATE/                # 缺陷与功能建议模板
│   ├── PULL_REQUEST_TEMPLATE.md        # 改动说明与验证清单
│   └── workflows/android.yml          # CI：格式、架构、行数、测试与 Debug 构建
├── gradle/                            # 版本目录与 Gradle Wrapper
├── build.gradle.kts
├── settings.gradle.kts
├── CHANGELOG.md                     # 版本说明与已知限制
├── LICENSE
├── CONTRIBUTING.md
└── README.md
```

`design/` 里的 HTML 仅作为视觉参考，不会被编译进 APK；应用实际界面全部由 Compose 实现。单模块的包边界优先于多模块拆分，后续只有在出现独立复用需求时再拆 `:core` / `:feature` 模块。

页面入口统一使用 `*Screen`，例如 `HomeScreen`、`LibraryScreen`、`ProfileScreen`；局部组件文件按职责命名，例如 `SearchResults.kt`、`SearchControls.kt`。`home` 组合搜索首页，`search` 提供结果、筛选与请求状态；跨功能导航和状态组装集中在 `app`，功能包不反向依赖应用组装层。

依赖边界、播放器与听视频关系、状态生命周期见 [架构说明](docs/architecture.md)；参与协作前请阅读 [贡献指南](CONTRIBUTING.md)。

## 环境要求

- macOS / Linux / Windows，已安装 Android Studio（推荐当前稳定版）。
- JDK 25（`gradle/gradle-daemon-jvm.properties` 指定；Android Studio 自带 JBR 即可）。
- 仓库自带 Gradle Wrapper，无需单独安装 Gradle。
- Android SDK Platform 37.0（`platforms;android-37.0`）、Build-Tools 及对应 Android SDK Platform-Tools。
- 真机或 API 28 以上的模拟器。
- 架构检查需要 Python 3.9+（仅标准库，无需安装 pip 依赖）。macOS / Linux 默认调用 `python3`，Windows 默认调用 `python`；可用 `-PpythonExecutable=/path/to/python` 指定解释器。
- `ktlintCheck` / `ktlintFormat` 需要 Bash，以及 PATH 中的 `curl`、`find`、`sort`、`mkdir`、`mv`、`dirname` 和 `sha256sum` 或 `shasum`。首次运行需要联网下载并校验固定版本的 ktlint CLI，之后使用 `build/tools/` 缓存。

Windows 请安装 Git for Windows，并使用 Git Bash 执行下方 Bash 示例，确认这些工具位于 PATH 中。若使用 PowerShell，可用 `.\gradlew.bat` 替代 `./gradlew`，但格式任务仍会调用 Bash，需要将 Git 的 `bin`、`usr/bin` 工具目录加入该终端的 PATH，避免误用 Windows 自带的 `find` / `sort`。JDK、SDK 路径请按本机安装位置配置。

macOS 如果命令行没有配置 `JAVA_HOME`，可以临时指定 Android Studio 自带的 JDK：

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
5. Release 签名初始化、备份、恢复和构建见 [发布与签名](docs/releasing.md)。密钥和密码配置保存在本地，不能提交到仓库。

## 构建与运行

```bash
# 编译 Debug APK（输出 app/build/outputs/apk/debug/app-debug.apk）
./gradlew assembleDebug

# 安装到已连接的设备 / 模拟器
./gradlew installDebug

# 代码风格检查（首次运行会下载固定版本的 ktlint CLI）
./gradlew ktlintCheck

# 自动格式化 Kotlin 代码
./gradlew ktlintFormat

# 检查单文件行数上限
./gradlew checkKotlinFileSize

# 检查依赖方向与功能包循环（同时运行检查器回归测试）
./gradlew checkArchitecture

# 运行 JVM 单元测试
./gradlew testDebugUnitTest

# 运行 Compose / 设备测试（需要已启动的模拟器或真机）
./gradlew connectedDebugAndroidTest
```

产物和依赖目录（`build/`、`.gradle/`、`.gradle-home/`、`.idea/`、`local.properties` 等）都已加入 `.gitignore`，不会进入版本库。

## 测试说明

- `app/src/test` 覆盖应用状态恢复、WBI 签名、搜索历史、播放缓存、弹幕 XML / 压缩解码与轨道排布等逻辑。
- `app/src/androidTest` 覆盖播放器控件、手势、本地视频详情、听视频和弹幕设置弹层等 Compose 交互。
- `scripts/tests` 覆盖反向依赖、功能包循环、别名 / 全限定引用、KDoc 与字符串模板等检查器行为；`checkArchitecture` 会自动运行这些测试。
- 首次运行 Gradle 需要联网下载依赖；国内网络可自行配置 Maven 镜像，但不要把镜像的私有凭据提交到仓库。
- GitHub Actions 会在 push / pull request 时执行 ktlint、架构与文件行数检查、单元测试、Debug 与未签名 Release 构建，见 `.github/workflows/android.yml`。设备测试需本地另行运行。

## 已知限制

- B 站接口和风控策略可能变化，部分视频的清晰度或播放地址受账号、地区与版权限制。
- 视频页的关注、点赞、投币和添加收藏操作尚未接入；点击会提示暂不支持，不会改变计数或伪装操作成功。
- 「我的收藏」已支持读取和播放现有收藏；暂不支持通过视频页修改收藏。
- 暂不支持稍后再看、小窗播放和应用内自动更新；后续安装包需手动下载并覆盖安装。
- 在线评论暂不展示；播放器内的额外能力以实际可用接口为准。

## License

代码以 [MIT License](LICENSE) 开源。BiliBox 是非官方项目，仓库中的哔哩哔哩名称、图标参考素材及相关商标归其权利人所有；MIT License 不构成对这些素材的授权，发布或再分发前请自行确认素材使用权限。
