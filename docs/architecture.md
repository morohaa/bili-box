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

## 播放器与听视频

- `LocalVideoScreen` 创建并管理 `LocalPlaybackQueue`，队列持有 ExoPlayer。同一文件夹会话复用播放器，队列直接切文件，使后台连播无需等待 Compose 重组；页面离开时释放队列。
- `OnlineVideoScreen` 创建并管理 ExoPlayer、分 P、清晰度和加载状态，播放地址请求及取消由 `OnlinePlaybackLoadCoordinator` 协调。
- 两个入口都组合 `VideoPlayerScreen`。它使用调用方传入的播放器，负责控制条、手势、弹层与听视频界面切换。
- `feature/listen` 通过 `ListenInfo`、`ListenControls` 和回调接收展示信息与队列操作。`ListenPlaybackEffect` 绑定同一个播放器并关闭视频轨道，不创建另一套音频播放进度。
- 后台媒体会话在 `core/playback`；`ListenPlaybackBridge` 将现有播放器接入 `ListenPlaybackService`，队列通过 `ListenQueue` 协议直接切换。倍速、定时关闭面板在 `core/ui/component/playback`，播放器与听视频共享定时状态。

## 状态归属与生命周期

| 状态 | 归属 | 生命周期 / 恢复方式 |
|---|---|---|
| 会话、顶层导航、搜索选项、选中的视频 | `app/BiliBoxAppState` | 组合各功能；会话从加密存储加载。Saver 只恢复标签索引、搜索词、排序 / 时长 / 时间筛选、列表布局和首页展开状态 |
| 搜索结果、加载 / 失败、请求取消 | `feature/search/SearchStateHolder` | 由应用组装层持有；会话 Cookie 改变时重建，结果不经 Saver 恢复 |
| 在线分 P、清晰度、加载、弹幕；本地队列与播放状态 | 对应 `feature/online`、`feature/player` 入口 | 跟随播放会话；选中的播放对象、本地播放列表和视频库临时筛选不经应用 Saver 恢复 |
| 手势、抽屉、弹层等局部 UI 状态 | 最近的页面或组件 | 按需使用 `remember` / `rememberSaveable`，无需提升到应用层 |
| Cookie、历史、播放进度、弹幕偏好 | 对应 `data/*` 存储 | 按存储实现持久化；主题偏好目前由 `BiliBoxApp` 直接读写 `appearance` SharedPreferences |

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
