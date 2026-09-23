# Android 使用与维护说明

## 用户使用

应用名称为“7.0指令生成器”，包名为 `com.grasscutter.m`。首次使用远程执行时，在“远程执行”中填写 OpenCommand 地址：

- 同一台 Android 设备上的服务：`http://127.0.0.1:8091`
- 电脑局域网服务：`http://192.168.1.100:8091`（替换为电脑实际 IP）
- 公网服务：填写可访问的域名或 IP，可使用 `http://` 或 `https://`

点击“检测服务器”确认连通，再发送验证码并完成验证。已有 Token 可以直接填写后保存。应用已统一允许可配置的 HTTP 地址，同时保留 HTTPS 支持；`127.0.0.1` 指 Android 设备自身，不是开发电脑。

资源搜索支持名称和 ID。生成的命令可以复制、分享、保存到历史记录，或发送到已连接的 OpenCommand 服务器。GOOD 存档、JSON 配置和应用设置均可通过文件选择器导入或导出。

## 发布版本更新

同步原仓库资源后，Android 构建会自动执行 `syncUpstreamResources`，一般不需要手动复制资源，也不要覆盖 Android 专用代码。发布新版本时：

1. 将 `Android/app/build.gradle.kts` 中的 `versionCode` 增加一个整数，并将 `versionName` 改为目标版本，例如 `7.1.0`。
2. 保持 `applicationId` 和 `namespace` 为 `com.grasscutter.m`，否则系统会把它识别成另一个应用，无法覆盖安装。
3. 使用与旧版相同的正式 Android 签名密钥构建 release APK。没有相同签名时，用户需要先卸载旧版，卸载会删除应用设置和命令历史。
4. 在 GitHub Actions 的 Android 工作流中构建 `assembleRelease`，从成功运行的 Artifacts 下载 APK，并在真机上验证安装、资源搜索和远程连接。

工作流支持正式签名密钥。配置仓库 Secrets `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS` 和 `ANDROID_KEY_PASSWORD` 后，release APK 会自动签名；未配置时只生成用于测试的未签名包。正式发布必须使用同一密钥，否则无法覆盖安装旧版本。

仅更新 `Source/GrasscutterTools/Resources` 时，通常只需同步原仓库并重新构建；如果上游改变命令参数、OpenCommand API、GOOD 格式或资源文件格式，则需要同步修改 Kotlin 适配代码。

This Android project deliberately lives beside the upstream WinForms source. `Source/GrasscutterTools/Resources` remains the data authority.

After an upstream update, run:

```sh
git pull --rebase upstream main
cd Android
./tools/sync-upstream-resources.sh
./gradlew assembleRelease
```

`syncUpstreamResources` also runs automatically before every Gradle build, so a CI APK includes the resources from the exact Git revision being built. Keep Android-specific command templates in Kotlin; do not copy or edit upstream resource files here.

The original project is licensed under AGPL-3.0-or-later. Any distributed derivative must preserve the corresponding license obligations.

The GitHub Actions build uses the Android SDK preinstalled on the hosted Ubuntu runner.
