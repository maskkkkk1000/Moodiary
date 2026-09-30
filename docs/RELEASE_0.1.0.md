# v0.1.0 签名发行验收

最终检查日期：2026-09-30。应用 0.1.0 / versionCode 1 / Room schema 1。

发行 APK 对应 annotated tag `v0.1.0`、公开仓库源码提交 `76959d1014a38c9a24b5dc595d72bb1f7e3a60cc`。后续提交仅改进测试同步、签名工具和文档；生产源码、依赖配置和数据库结构与标签一致。2026-09-30 经仓库所有者授权，新公开仓库将历史邮箱替换为 GitHub 隐私邮箱，因此提交与标签对象哈希变化，逐提交文件树保持一致；原始历史和标签保留在私有存档。应用仍为 0.1.0，同一初始版本的签名分发不修改应用或 schema 版本。

## 发布检查结果

| 项目 | 结果 |
| --- | --- |
| Debug / Release / Android test APK 构建 | 全部通过 |
| JVM tests | domain 34 + app 34，全部通过 |
| Android / Room / Compose tests | 最终 15/15 通过，0 失败、0 错误、0 跳过 |
| lintDebug | 0 错误、44 警告 |
| Room schema / migration | schema 1 保留；无更旧生产版本；不支持版本安全拒绝测试通过，无 destructive fallback |
| backup → restore | ZIP、合成照片、损坏拒绝、自动备份及回滚回归覆盖通过 |
| 签名 / 对齐 | apksigner v2/v3 验证及 zipalign 验证通过，非 debuggable APK |
| 签名 APK 实際运行 | 安装、冷启动、草稿重启恢复、创建/保存、同签名覆盖安装、强制停止后冷启动记录保留均通过 |
| 敏感资料 | 变更扫描未发现凭据；密钥位于仓库外，不进入提交或附件 |
| 版本 / tag | app 0.1.0、versionCode 1、Room 1；公开仓库 annotated tag 对应匿名化后的相同源码 |
| 文档 | CHANGELOG、README、ROADMAP、安装迁移和签名维护说明更新 |

环境：Windows、JDK 17、Gradle 8.13、SDK / target 35、Build Tools 35.0.0；专用 API 35 模拟器，英文界面、解除系统锁屏、测试期间常亮。只使用合成日记，不操作用户真机数据。

原始报告保留在忽略的 verification 和模块 build 目录：`release-resume2-build.log`、`release-resume2-device.log`、`release-v0.1.0/smoke-result.txt` 及 XML 界面证据，不作为公共附件。

## 测试异常与处理

首次设备检查的大数据测试曾出现 Compose/Espresso 布局重入异常。后续复测在语言恢复清理时等待界面帧，线程转储显示主线程空闲；另确认中断后重启的模拟器处于系统锁屏，无法获取当前界面根节点。失败/停滞日志已保留，没有用旧报告替代。

测试修正：语言清理等待语言设置恢复，不等待正在重建的旧界面帧；大数据测试在页面跳转前收起输入法并等待布局稳定；环境保持常亮和解锁。之后完整 15 项设备测试通过，核心断言和用例均保留。仍需持续关注跨设备自动化稳定性。

## 安装包与密钥

- APK：`Moodiary-v0.1.0.apk`，15,240,550 字节。
- APK SHA-256：`45d231f0ffbb125c5e2d3a2610357924f68998bdea07c6b041ada9366aa4a66d`。
- 公开证书 SHA-256：`43564ca8b3530145db25929a9a3e2762103237338204e394e1c7f2bb4598be8e`。
- 安装、Debug 迁移和长期密钥备份见 [INSTALL_AND_SIGNING](INSTALL_AND_SIGNING.md)。签名脚本经实际执行验证，生成 APK 与发行附件哈希一致。

## 发布边界

用户已反馈除 OAuth 外真机功能正常。Google Drive 仍需用户自己的 Cloud/OAuth 配置及真实云端验收；跨厂商长期后台和完整 TalkBack 继续验证。备份/导出为明文，PIN 不加密数据库。

公开发布使用独立仓库，原始仓库继续保持私有。公开内容包括匿名化后的开发历史及相同签名 APK；不包含签名密钥、密码、真实日记或照片。此发行不表示上架应用商店。服务端分支保护和云端 CI 未配置，不能标记为已通过。

仓库：https://github.com/maskkkkk1000/Moodiary

发行页：https://github.com/maskkkkk1000/Moodiary/releases/tag/v0.1.0
