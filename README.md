# Moodiary

本地优先的 Android 心情日记应用。项目规范：[`Moodiary_Codex_Master_Plan.md`](Moodiary_Codex_Master_Plan.md)，第一轮迭代规范：[`moodiary_plan2.md`](moodiary_plan2.md)。当前版本为 `0.2.0`，已发布发行签名 APK：[下载安装包](https://github.com/maskkkkk1000/Moodiary/releases/download/v0.2.0/Moodiary-v0.2.0.apk)。

## 构建与测试

当前源码：0.2.0（versionCode 2），Room schema 2，备份格式 2（兼容读取格式 1）。本轮验收记录见 [ITERATION_1](docs/ITERATION_1.md)；[0.2.0 发行页](https://github.com/maskkkkk1000/Moodiary/releases/tag/v0.2.0)提供签名 APK、SHA-256 校验文件与发行说明。参见[安装迁移/签名说明](docs/INSTALL_AND_SIGNING.md)、[ROADMAP](docs/ROADMAP.md)、[CHANGELOG](CHANGELOG.md)和[版本管理约定](docs/VERSION_CONTROL.md)。

环境：JDK 17、Android SDK 35、Build Tools 35.0.0、Gradle 8.13。当前工作目录已安装可移植工具，不需要修改系统环境变量。

全新克隆不会包含 `.tooling`。请自行安装上述 JDK/SDK，设置 `JAVA_HOME` 和 `ANDROID_HOME`（或在不提交的 `local.properties` 中设置 `sdk.dir`），然后直接运行 Gradle Wrapper；首次构建需要联网下载依赖。下面的 `use-local-toolchain.ps1` 仅适用于已有本机可移植工具的目录。macOS/Linux 使用 `./gradlew`。

```powershell
. .\scripts\use-local-toolchain.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :domain:test :app:lintDebug
.\gradlew.bat :app:assembleRelease
# 需要运行中的测试设备或模拟器：
.\gradlew.bat :app:connectedDebugAndroidTest
```

部分 Compose 路径断言使用英文文案，运行整套设备测试前请把专用设备系统及 Moodiary 的语言设为英文。Android 13+ 可在安装调试 APK 后执行 `adb shell cmd locale set-app-locales app.moodiary --user 0 --locales en`；语言专项测试会自行切换中英文并恢复原设置。不要在保存真实日记的设备上运行测试。

设备测试期间保持屏幕唤醒并解除系统锁屏；专用模拟器可使用 `adb shell svc power stayon true`。屏幕休眠可能使 Compose 等待无法绘制的界面帧，造成测试停滞。测试结束后可用 `adb shell svc power stayon false` 恢复。

调试安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。请在专用测试设备上运行仪器测试；测试会创建日记、目标和临时报告。发行版需要用户自己的签名密钥，项目不包含私钥。

在手机启用开发者选项和 USB 调试，授权连接后执行：

```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

多设备时使用 `adb -s <设备序列号> install -r ...`。已有安装若签名不同，不要直接卸载丢失日记，先在旧应用导出备份。未签名 Release APK 在 `app/build/outputs/apk/release/app-release-unsigned.apk`。

## 核心功能与技术栈

日记增删改与多条/天、照片、时间线、日历、搜索筛选；可定制情绪/活动/分组、模板、目标联动与连续记录；统计和次日关联；提醒、重要日、成就、主题与语言切换；PIN/生物识别；本地备份恢复、自动备份、可选 Drive、JSON/CSV/PDF 导出。

0.2.0 增加柔和卡片与心情选择交互、Emoji/内置图标选择器、统一统计筛选和独立每日小目标。正常启动直接记录或恢复合法草稿；通知保留指定入口。统计可选择日期、心情、活动及模块，设置会保存，多个活动按 OR 筛选。小目标可记录成功、未做到或未记录，完成率只以已记录结果为分母；支持历史补录、归档恢复和周/月趋势。

使用 Kotlin、Jetpack Compose、Material 3、Navigation Compose、Hilt、Room、DataStore、WorkManager、Coroutines/Flow，采用 MVVM 和 repository 架构。

工具下载、校验和、SDK 许可记录及设备设置见 [`docs/TOOLCHAIN.md`](docs/TOOLCHAIN.md)。设计、日期、统计与恢复协议见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。基线验收见 [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md)，新增行为、测试和限制见 [`docs/ITERATION_1.md`](docs/ITERATION_1.md)。

## 应用结构

- `domain/`：可在普通 JVM 测试的统计、目标、连续记录、搜索、归档验证和导出逻辑。
- `app/`：Room、Hilt、Compose、Navigation、DataStore、WorkManager、照片、PIN／生物识别、备份恢复与 PDF。
- `app/schemas/`：Room 导出的数据库结构。没有破坏性迁移回退。

所有日记默认保留在本机，无广告、追踪 SDK、强制服务器账户。照片由系统选择器导入后复制到应用私有目录。云端功能完全可选。

## 界面语言

打开 **更多（More）→ Language / 语言**，可选择 **跟随系统、简体中文、English**。选择后立即刷新界面，重启应用后仍保留。Android 13 以上也可在系统的应用语言设置中选择，两处设置保持同步。

界面、日期/星期、统计说明、常见提示及 PDF 报告标题支持中英文。日记、自定义情绪/活动名称和已保存提醒文案保留原文；语言属于本机设置，不随日记备份恢复而改变。实现使用 Android [应用语言 API](https://developer.android.com/guide/topics/resources/app-languages) 与 AppCompat 的旧版系统兼容存储。

## Google Drive 配置

本地日记、备份及导出不需要 Google 配置。要验证 Drive：

1. 在用户自己的 Google Cloud 项目中启用 Drive API，配置 OAuth 授权页面及测试用户。
2. 创建 Android OAuth 客户端，包名 `app.moodiary`，登记当前签名证书 SHA-1（调试与发行证书分别登记）。可用 `gradlew :app:signingReport` 查看开发证书指纹。
3. 在有 Google Play 服务的 Android 设备上，从“Backup & restore”主动连接 Google Drive。
4. 实测上传、列出、下载恢复、撤销授权后的错误处理、自动备份与重试。

授权使用 Google `AuthorizationClient`，只请求 `drive.appdata`；应用不存储访问令牌，不需要后端账户。参考 [Google 授权文档](https://developer.android.com/identity/authorization) 与 [Drive 应用私有数据目录](https://developers.google.com/workspace/drive/api/guides/appdata)。本次环境没有用户的 Cloud 项目、OAuth 客户端和授权测试账户，不能宣称真实云端往返已经验证。

## 数据安全约定

- 备份 ZIP 包含明文日记和照片，请保存到可信位置。PIN 和云端凭据不进入归档。
- 替换恢复必须明确确认。归档完整验证后才修改数据，并保留中断恢复所需的回滚记录。
- Room 1 → 2 使用显式增量迁移，保留旧日记、关联、设置与照片；历史 schema JSON 均保留。禁止破坏性回退和直接降级。
- 统计展示观察性关联和样本量，不宣称因果关系。
- 系统省电策略可能延迟提醒和自动备份；提醒不承诺精确到分钟。

## Backup / Restore

在“更多 → 备份与恢复”使用系统文件选择器保存完整 ZIP。归档包含结构化日记、小目标及结果、设置和应用管理的照片；语言、统计筛选、PIN 和云凭据不随归档迁移。自动备份需选择并授权目标目录，系统后台限制可能影响执行时间。0.2.0 可恢复 0.1.0 旧备份，缺失小目标时按空列表处理；0.1.0 无法读取新版格式 2 备份。

恢复会替换当前日记，先备份现有数据，再选择归档并明确确认。应用先校验版本、内容和媒体路径，再执行替换；失败或中断时使用回滚记录保护原数据。JSON/CSV/PDF 是导出格式，不应当作完整恢复归档。

## 问题反馈

使用者如果发现任何问题，欢迎给我留言

请通过 [GitHub Issues](https://github.com/maskkkkk1000/Moodiary/issues) 留言，描述复现步骤和手机型号。请勿上传真实日记、私人照片、备份文件或密码。

## 当前限制

- Google Drive 接入已实现，但没有真实 OAuth 项目/账户验收，云端功能不能视为已正式验证。
- 已收到用户“除 OAuth 外真机功能正常”的反馈；跨厂商后台策略、长期使用及完整 TalkBack 仍需扩大验收范围。
- PIN 是界面访问控制，不会加密数据库；备份与导出文件是明文。
- 已测试 50,000 条合成短日记；大量高分辨率照片/长文本仍需压力测试，界面数据快照尚未全面分页化。
- 小目标每日提醒和独立 CSV 留待后续；本轮已接入完整 ZIP、JSON 与 PDF 摘要。习惯目标和小目标独立于日记，遵循日期/目标筛选，心情/活动筛选只影响日记统计。
- 部分底层技术诊断仍可能为英文，用户自定义内容和已保存默认名称不自动翻译。
- Gradle 默认输出未签名 APK；发行包另用仓库外的长期密钥签名。Debug 用户需先备份再迁移，见 [安装说明](docs/INSTALL_AND_SIGNING.md)。完整发布前执行 [发布检查单](docs/RELEASE_CHECKLIST.md)。
