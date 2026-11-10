# 发布与签名

## 版本

当前候选版本为 `1.0.0-beta.1`，`versionCode = 1`。每次公开发布新安装包都必须递增 `versionCode`；同一版本的验证应使用同一份 APK。

Release 开启代码和资源优化，不包含 Debug 的 UI 调试工具及测试宿主。

## 初始化签名（仅首次）

没有既有发布签名时运行：

```bash
python3 scripts/setup_release_signing.py --backup-dir /absolute/path/outside/repository/bilibox-signing-backup
```

如命令行无法找到 JDK，请先将 `JAVA_HOME` 指向 JDK 25。脚本生成 RSA 4096 位 PKCS12 密钥和随机密码，保存到 `.release-signing/`、`signing.properties`，并在仓库外备份。所有私有文件仅允许当前用户读取，脚本验证备份密钥的内容和证书与原件一致。发现已有密钥、配置或备份时会拒绝覆盖。

这些私有文件均被 Git 忽略。备份目录包含密钥、密码配置和证书，请另存一份到自己的离线介质或安全备份中；本机副本无法防止整机或磁盘损坏。后续版本必须继续使用同一密钥，不能重新初始化。

也可自行创建 `signing.properties`，字段如下：

```properties
storeFile=/absolute/path/to/existing-release.p12
storePassword=your-local-store-password
keyAlias=your-key-alias
keyPassword=your-local-key-password
```

相对密钥路径以该配置文件所在目录为基准。配置缺项或密钥文件不存在时构建会报错，不会退回 Debug 签名。

## 构建与核验

```bash
./gradlew ktlintCheck checkArchitecture checkKotlinFileSize testDebugUnitTest assembleDebug --max-workers=1
./gradlew assembleRelease --max-workers=1
```

有本地签名配置时，发布产物为 `app/build/outputs/apk/release/app-release.apk`。无配置时（例如公开 CI）只会生成 `app-release-unsigned.apk`，用于验证压缩优化，不能作为安装包发布。

使用 SDK Build-Tools 中的 `apksigner` 验证发布签名和证书：

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

核对输出的证书 SHA-256 与 `.release-signing/certificate.sha256` 一致，再为同一份 APK 生成 SHA-256 校验值。

## 从备份恢复

备份中的 `signing.properties` 使用相邻的密钥文件，可以直接构建：

```bash
BILIBOX_SIGNING_PROPERTIES=/absolute/path/to/backup/signing.properties ./gradlew assembleRelease
```

恢复时保留私有文件权限并核对原发布证书指纹。切勿将备份、密码或密钥提交到仓库。

## 回归与发布

1. 确认当前提交的 CI 通过，使用正式签名构建 Release，核验版本、证书和 APK 校验值。
2. 在真机验证启动、登录、在线取流和播放、本地权限和播放、后台听视频、通知控制、横竖屏切换。登录成功需要测试账号本人完成身份验证。
3. 记录实际设备、APK 校验值、通过项目和待验证项目，区分模拟器与真机结果，不能以 Debug 测试代替 Release 回归。
4. 更新 `CHANGELOG.md`、README 和对应发布验证记录。Beta 版本在 GitHub Releases 标记为 Pre-release，附上正式签名 APK、校验值及已知限制。

开发期 Debug 包与正式签名包无法互相覆盖安装。验证真机时先确认设备上是否已有 Debug 包；需要卸载时先保留所需数据并取得设备使用者确认。
