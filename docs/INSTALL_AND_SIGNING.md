# 正式 APK 安装、迁移与签名

发行页：[Moodiary v0.2.0](https://github.com/maskkkkk1000/Moodiary/releases/tag/v0.2.0)；[下载签名 APK](https://github.com/maskkkkk1000/Moodiary/releases/download/v0.2.0/Moodiary-v0.2.0.apk)。

应用版本 0.2.0 / versionCode 2 / Room schema 2 / 备份格式 2（读取 1/2）。签名 APK 使用已验证的 v0.2.0 标签对应源码，沿用 0.1.0 的发行密钥，不移动已有标签，也不称为 V1.0。此版本提供 GitHub APK 分发，不代表已上架应用商店。

## 0.2.0 升级

当前源码为 0.2.0 / versionCode 2 / Room schema 2。升级包继续使用原发行密钥；已安装同一发行签名的 0.1.0 时可覆盖安装，无需卸载。更新前先保存一份完整备份。首次打开执行显式 1 → 2 迁移，原有日记和照片保留。0.2.0 可读取旧备份；新版格式 2 备份不能交给 0.1.0 恢复，数据库也不支持直接降级。

0.2.0 签名 APK 已于 2026-10-04（北京时间）上传，公开下载文件的 SHA-256 已核对；发行页附有 `SHA256SUMS.txt`。本轮实现和验证边界见 [ITERATION_1](ITERATION_1.md)。下文为 Debug 迁移步骤。

## 从 Debug 版迁移

新的发行签名通常不能直接覆盖开发用 Debug 签名。请按顺序操作：

1. 保留旧应用，在“更多 → 备份与恢复”导出**完整 ZIP 备份**到 Downloads、电脑或可信外部目录，不能只留在应用私有目录。
2. 确认备份文件可读取且大小合理，另存一份。重要日记建议再导出 JSON 留作核对；JSON/CSV/PDF 不能替代完整恢复 ZIP。照片多时等待备份真正完成。
3. 只有确认备份已保存在应用外后，才卸载旧版。卸载会删除旧应用的本地数据。
4. 从发行页下载 `Moodiary-v0.2.0.apk`，安装并打开。
5. 在“备份与恢复”选择 ZIP，阅读替换提示并确认恢复，核对日记数量、日期、照片和目标。
6. 重新设置 PIN、生物识别、通知权限及自动备份目录；这些设备安全设置/授权不随备份迁移。重新确认语言和提醒设置。

若没有可靠备份，先继续使用旧版，不要为了安装正式 APK 直接卸载。后续同一发行密钥签署、versionCode 更高的更新可正常覆盖安装；更新前仍建议备份。

Google Drive 需要开发者配置 OAuth，本版本未完成真实云端验收。其余本地功能不依赖 Google 登录。ZIP 和导出文件未加密，应保存于可信位置。

## 发行密钥维护（仅项目所有者）

密钥位于仓库外 `%USERPROFILE%\.moodiary-signing\moodiary-release.jks`，alias 为 `moodiary-release`。随机密码以 Windows DPAPI 加密保存为同目录 `release-password.dpapi.xml`，目录仅当前用户和 SYSTEM 可访问。不要提交或上传这两个文件，也不要将它们附在 GitHub Release 中。

**必须备份密钥，并将密码另存可信密码管理器。** DPAPI 文件通常只能在当前 Windows 用户/机器上解密，仅复制该 XML 到新电脑不足以恢复签名能力。可在自己的本机 PowerShell 私密窗口读取密码后存入密码管理器：

```powershell
$signingCredential = Import-Clixml "$env:USERPROFILE\.moodiary-signing\release-password.dpapi.xml"
$signingCredential.GetNetworkCredential().Password
```

不要将输出贴到聊天、Git、截图或公共日志。此步骤由密钥所有者自行执行，自动发布脚本不输出密码。丢失密钥或其密码，将无法为当前安装签署兼容更新。

公开证书 SHA-256：`43564ca8b3530145db25929a9a3e2762103237338204e394e1c7f2bb4598be8e`。

构建后在 Windows 使用已有密钥签名：

```powershell
. .\scripts\use-local-toolchain.ps1
.\gradlew.bat :app:assembleRelease
.\scripts\sign-release.ps1
```

新版本传入对应的 `-OutputApk` 文件名，并先按版本约定更新应用 versionName/versionCode；脚本不自动升级版本、不自动生成密钥、不覆盖现有输出。
