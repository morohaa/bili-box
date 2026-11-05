# Contributing to BiliBox

感谢参与 BiliBox。提交改动前请先确认下面几项。

## 分支与提交

- 从 `main` / `master` 创建功能分支，例如 `feat/search-filter`、`fix/player-crash`。
- 提交信息沿用仓库现有风格：

  ```text
  feat<scope>: 简短描述
  - 变更点
  - 验证方式
  ```

  常用 scope：`app`、`home`、`danmaku`、`player`、`online`、`library`、`profile`、`search`、`auth`、`repo`。

## 代码组织

- 新代码优先放入已有模块：
  - 应用根布局、顶层导航、会话和跨功能状态组装 → `app/`
  - 共享模型与业务筛选选项 → `core/model`，按职责命名文件
  - 播放队列协议、播放模式、倍速规则、定时状态与后台媒体会话 → `core/playback`
  - 共享 UI / 主题 / 工具 → `core/ui`、`core/util`
  - 接口与存储 → `data/*`
  - 页面与页面组件 → `feature/*`
- `MainActivity` 只负责 Activity 生命周期和 Compose 挂载；`feature/`、`core/`、`data/` 不反向依赖应用组装包 `io.github.bilibox.app`。
- 完整页面入口统一命名为 `*Screen`，承载入口的文件优先同名；局部组件按职责命名，例如 `SearchResults`、`SearchControls`、`*Sheet`、`*Pane`，不要使用没有对应页面入口的 `*Screen` 文件名。
- `feature/home` 是首页容器，负责组合搜索入口；`feature/search` 提供搜索结果、筛选和请求状态，不反向依赖 `home`，也不承担应用导航。
- `data/` 不依赖 `feature/`；播放模式的文案和图标映射留在 UI 层，缩略图读取和缓存放在 `data/localvideo`。
- 已跨功能复用的封面、图标等 UI 放在 `core/ui/component`；共享播放面板放在其 `playback/` 子包，功能专用组件继续留在所属 `feature/`。
- `core/` 不依赖 `feature/`。播放器组合听视频界面，听视频界面通过共享状态、组件和回调协作，避免反向引用播放器页面。
- `feature/library` 负责视频库浏览、文件夹与筛选；本地播放简介、媒体信息、同文件夹队列和播放顺序归 `feature/player/details`，两包不互相依赖。共享格式化放在 `core/util`，共享封面 UI 放在 `core/ui/component`；缩略图缓存状态与加载回调由调用方传入，`core` 不依赖 `data`。
- `core/model` 不依赖数据层或接口客户端。搜索和播放等页面状态放在对应 `feature/`；筛选文案由 UI 层映射，接口参数和响应转换放在数据层。
- 不要把数据存储或网络请求直接写进 Composable；能独立测试的逻辑放到 `data/` 或 `core/`。
- 新增页面请在 `feature/<feature>/` 下建文件，测试目录保持同样的包结构。
- 单个 Kotlin 源文件建议不超过 500 行；按职责和可读性拆分，而不是只按行数机械切分。
- `app/src/main/java` 下的 Kotlin 文件以 1000 行为构建检查硬上限，超过时 `checkKotlinFileSize` 会失败。

## 验证

提交前至少运行：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

涉及 Compose 交互时再运行：

```bash
./gradlew connectedDebugAndroidTest
```

## 安全与隐私

- 不要提交 `local.properties`、keystore、`.jks`、Cookie、账号密码或 `.env` 文件。
- 新增日志时不要打印登录 Cookie、SESSDATA、bili_jct 等敏感信息。
- 接口失败提示应可读，不要把服务端原始 HTML 或堆栈直接展示给用户。
