# 应用字体

「我的 → 主题外观 → 应用字体」提供默认字体、思源宋体和霞鹜文楷。点选立即应用于 Compose 界面、输入框和弹幕，选择随外观设置保存；「恢复默认」同时恢复系统字体。

字体字族由 `BiliBoxTheme` 下发，所有 Material 文字样式保留原有字号、字重、行高和字间距。独立的 `TextStyle`、`BasicTextField` 和 Canvas 文字读取 `LocalAppFontFamily`。首页的品牌字标保留原有造型，网页登录页面使用网页自身的字体。

两款预设均随 APK 内置，不依赖网络下载或设备安装字体。使用原始 Regular 字体文件，其他字重由 Android 合成，未裁剪字库；缺少的字符由 Android 系统字体回退。字体资源合计约 24.4 MiB，APK 压缩后的增量以实际构建为准。

## 来源与许可

两款字体都采用 SIL Open Font License 1.1，完整许可随 APK 放在 `assets/licenses/`，字体文件保留原始版权和字体名称。

| 选项 | 文件 | 上游版本及来源 | SHA-256 |
| --- | --- | --- | --- |
| 思源宋体 | `app/src/main/res/font/noto_serif_sc_regular.otf` | Noto Serif SC 2.003，[noto-cjk](https://github.com/notofonts/noto-cjk/blob/f8d157532fbfaeda587e826d4cd5b21a49186f7c/Serif/SubsetOTF/SC/NotoSerifSC-Regular.otf)，与思源宋体共用设计 | `e8f396decc1f0963a016a989c3d8852e863d1350996f573860a80767c83a1cd3` |
| 霞鹜文楷 | `app/src/main/res/font/lxgw_wenkai_lite_regular.ttf` | LXGW WenKai Lite 1.530，[LxgwWenKai-Lite](https://github.com/lxgw/LxgwWenKai-Lite/blob/67384bd5fb67cf7b3a4f8907c1ec7342fad651ab/fonts/TTF/LXGWWenKaiLite-Regular.ttf) | `f799a538a3355877e597a0ae04050e897932c9ae7689eb9227c2953c5dd549ea` |

Noto Serif SC：© 2017–2024 Adobe (http://www.adobe.com/)。许可见 `app/src/main/assets/licenses/noto_serif_sc_OFL.txt`。

LXGW WenKai Lite：Copyright 2021–2026 LXGW；Copyright 2020 The Klee Project Authors。许可及保留字体名称条款见 `app/src/main/assets/licenses/lxgw_wenkai_lite_OFL.txt`。
