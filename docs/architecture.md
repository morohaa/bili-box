# 架构与状态归属

BiliBox 当前是单个 Android `:app` 模块，包边界承担职责划分。本文描述现有代码，新增功能沿用这些边界；无需为了目录对称提前拆成多个 Gradle 模块。

## 依赖方向

源码根包为 `io.github.bilibox`：

```text
MainActivity → app
app          → feature / data / core
feature      → data / core
data         → core
```

- `MainActivity` 负责 Activity 生命周期与 Compose 挂载；`app` 组合主题、导航、会话与跨页面状态。
- `feature/<name>` 放页面、组件与功能专用逻辑；可以调用 `data` 和 `core`，不能引用 `app` 或 `MainActivity`。
- `data` 放接口、解析、设备媒体访问与持久化，可以依赖 `core`，不能引用 `feature` 或应用组装层。
- `core` 放已跨功能共享的模型、播放协议、组件与工具，不依赖 `data`、`feature` 或应用组装层。它可使用 Android / Media3 API，并非纯 Kotlin 模块。
- 功能包可以按需单向组合，但不得成环。当前主要组合为 `home → search`、`profile → auth`、`online → player / listen`、`player → listen / danmaku`；子包属于同一个功能。
- `search` 不反向依赖 `home`，`listen` 不反向依赖 `player`。`library` 负责浏览与筛选，`player/details` 负责本地播放详情，两功能包不互相依赖；导航由 `app` 的回调连接。

出现双向需求时，先确认组件归属。只服务一个页面的组件留在所属功能；确实共享的状态、协议或 UI 按职责提到 `core`，通过参数和回调协作，不为消除 import 就把业务整体搬进共享层。

弹幕模型、解析、偏好持久化在 `data/danmaku`；轨道、速度、碰撞与显示时间排布仅服务显示，放在 `feature/danmaku/layout`。

搜索与收藏的视频卡片共用 `core/ui/component/VideoCards` 的封面、排版、元信息与明暗配色；收藏通过附加信息显示收藏时间，并为失效资源传入状态文案与禁用点击。

`RemoteCover` 通过 `core/util/RemoteImageCache` 复用按图片实际内存占用限制的 32 MiB LRU 缓存；创建列表项时同步读取缓存，避免滚动返回先显示占位。相同 URL 的并发加载共享任务，任务不会随单个列表项离开组合而取消；加载最多四路并发，解码最长边限制为 640px，「清除缓存」同时清空这份内存缓存。

## 播放器与听视频

- `LocalVideoScreen` 创建并管理 `LocalPlaybackQueue`，队列持有 ExoPlayer。同一文件夹会话复用播放器，队列直接切文件，使后台连播无需等待 Compose 重组；页面离开时释放队列。
- `OnlineVideoScreen` 创建并管理 ExoPlayer、分 P、清晰度和加载状态，播放地址请求及取消由 `OnlinePlaybackLoadCoordinator` 协调。
- 两个入口都组合 `VideoPlayerScreen`。它使用调用方传入的播放器，负责控制条、手势、弹层与听视频界面切换。
- `feature/listen` 通过 `ListenInfo`、`ListenControls` 和回调接收展示信息与队列操作。`ListenPlaybackEffect` 绑定同一个播放器并关闭视频轨道，不创建另一套音频播放进度。
- 后台媒体会话在 `core/playback`；`ListenPlaybackBridge` 将现有播放器接入 `ListenPlaybackService`，队列通过 `ListenQueue` 协议直接切换。倍速、定时关闭面板在 `core/ui/component/playback`，播放器与听视频共享定时状态。

## 状态归属与生命周期

| 状态 | 归属 | 生命周期 / 恢复方式 |
|---|---|---|
| 会话、顶层导航、搜索选项、选中的视频 | `app/BiliBoxAppState` | 组合各功能；会话从加密存储加载。Saver 只恢复标签索引、搜索词、排序 / 时长 / 时间筛选、列表布局、首页展开状态与「主题外观」是否打开 |
| 搜索结果、加载 / 失败、请求取消 | `feature/search/SearchStateHolder` | 由应用组装层持有；会话 Cookie 改变时重建，结果不经 Saver 恢复 |
| 收藏夹、收藏视频、搜索 / 排序、分页与请求取消 | `feature/favorites/FavoritesStateHolder` | 由应用组装层按登录 Cookie 创建，切换账号时取消旧请求并丢弃结果；进入播放页保留浏览结果与列表位置，收藏导航开关随应用 Saver 恢复 |
| 在线分 P、清晰度、加载、弹幕；本地队列与播放状态 | 对应 `feature/online`、`feature/player` 入口 | 跟随播放会话；选中的播放对象、本地播放列表和视频库临时筛选不经应用 Saver 恢复 |
| 手势、抽屉、弹层等局部 UI 状态 | 最近的页面或组件 | 按需使用 `remember` / `rememberSaveable`，无需提升到应用层 |
| Cookie、历史、播放进度、弹幕偏好、主题外观、壁纸 / 头图调节、自定义头图与首页背景 | 对应 `data/*` 存储 | 按存储实现持久化；主题外观由 `BiliBoxApp` 通过 `data/appearance/AppearanceStore` 读写 `appearance` SharedPreferences；自定义头图与头图调节由 `data/profile/ProfileBannerStore` 写入应用私有目录与 `profile_banner` SharedPreferences；自定义首页背景由 `data/appearance/HomeBackgroundImageStore` 写入应用私有目录，背景调节随主题外观一起保存 |

## 主题外观

- `core/ui/theme/Appearance.kt` 保存明暗模式、首页背景（渐变 / 纯色 / 自定义）、自定义背景的暗化 / 模糊与 12 套主题色板；`AppearanceBackdrop` 负责把渐变光晕或用户上传的图片按要求铺满页面，叠上与明暗模式匹配的可读性蒙层，再按设置追加暗化与模糊。
- `data/appearance/AppearanceStore` 负责 `appearance` SharedPreferences 的读写、旧版 `dark_mode` / `home_background=HOME` 的迁移，以及壁纸调节值。
- `data/appearance/HomeBackgroundImageStore` 把「自定义首页背景」缩放到 2160px 以内并编码为 JPEG 写入应用私有目录，启动时由 `BiliBoxApp` 异步读回。
- `BiliBoxApp` 解析「浅色 / 深色 / 自动」得到实际明暗，创建带强调色的 `BiliBoxTheme`，并通过 `LocalAccentColor` 下发给首页、底部导航与个人页头图。
- `feature/appearance` 只负责页面和交互，选中的主题、模式、背景与调节值通过回调交给应用组装层持久化；页面不直接读写 SharedPreferences，也不依赖 `app`。
- 首页背景弹层不写标题，只保留右上角「完成」，三项依次是渐变、纯色、自定义；点「自定义」先选图，再进入整屏壁纸预览，点「应用」才写盘并生效。
- 壁纸预览的顶栏、可展开面板、暗化 / 模糊滑杆与底部「应用 + 更多」按钮在 `core/ui/component/WallpaperEditorChrome`，首页壁纸与头图预览共用；预览层自己负责截图导出，浮层不进入导出的图片。
- 「立即应用」不改变已经即时生效的预览，只做一次确认提示；退出页面不会回滚已保存的设置。

## 个人页头图

- 「我的」页头图点击后进入沉浸式全屏预览，开合状态放在 `app/BiliBoxAppState`，由应用组装层统一处理底部导航隐藏、系统栏与返回键。
- 默认头图仍由主题强调色与头像生成；自定义头图经 `data/profile/ProfileBannerStore` 按 16:9 居中裁切为 JPEG 后写入应用私有目录，不依赖相册 URI 的临时权限。暗化 / 模糊与图片分开存在 `profile_banner` SharedPreferences，叠加在个人页头图与预览画面上。
- 预览页复用 `WallpaperEditorChrome`：顶栏关闭 / 换图，面板提供暗化、模糊、保存到相册、全屏预览与恢复默认，底部「应用」提交调节值；保存与更换的界面状态留在 `feature/profile`。
- 16:9 画面始终居中展示；面板展开时通过 `onPanelOpenChange` 回调把画面向上让位，避免半透明面板压住头图。
- 更换头图先进入 16:9 裁剪页：拖动 / 双指缩放的状态留在 `feature/profile`，`bannerCropRect` 把视口映射回源图坐标，`ProfileBannerStore.saveCrop` 只负责裁切与落盘；确认后才替换预览中的头图。

共享模型放在 `core/model`，不夹带接口客户端或页面状态。UI 文案与图标由 UI 层映射，接口参数和响应转换放在数据层。

三种“缓存”职责不同：`feature/online/EpisodePlayCache` 是播放会话内的相邻分 P 地址预取与短期内存复用；`feature/online/OnlineCacheController` 维护缓存面板的两路并发队列、视频/音频双轨状态、取消与重试；`data/online/OnlineVideoCacheStore` 负责真实下载（视频 MP4 + DASH 音频）、进度回调、取消清理与 MediaStore 发布，`OnlineCacheIndex` 持久化每个分 P 的双轨文件。前两者随会话重建，后者产出持久文件，不应因为同名 Cache 合并。

## 自动边界检查

```bash
./gradlew checkArchitecture
# 无需 Android SDK，只运行检查器及其回归测试：
python3 -B -m unittest discover -s scripts/tests
python3 -B scripts/check_architecture.py
```

`checkArchitecture` 先运行检查器回归测试，再检查主源码；`:app:check` 和 GitHub Actions 均已接入。Python 工具与 Windows 前提见 [README](../README.md#环境要求)。

检查范围是 `app/src/main/java/**/*.kt` 中实际使用的项目内显式 import 和全限定引用，按 `feature/<name>` 合并子包，拒绝上述反向依赖与功能包环，并输出文件、行号和依赖路径。注释、KDoc 与字符串字面量不形成边，字符串模板内的表达式仍参与检查；仅为 KDoc 引用引入的 import 不计入依赖。项目内部不使用通配符 import，外部库不受此限制。

这是轻量词法检查，按符号文本匹配 import 的使用，不是 Kotlin 编译器的符号解析或运行时依赖分析；运算符等隐式调用、同名符号遮蔽、反射和生成代码等仍需代码评审与编译验证。检查器规则及回归用例位于 `scripts/check_architecture.py`、`scripts/tests/`，调整边界时同步修改本文和测试，不能用忽略列表绕过循环。
