# 0.1.0 基线验证记录

日期：2026-09-28。应用 0.1.0 / versionCode 1，Room schema 1。此记录用于初始 Git 基线，并不代表 1.0 正式发行验收。

## 环境与结果

Windows、JDK 17、Gradle 8.13、Android SDK/target 35、Build Tools 35.0.0；专用 API 35 x86_64 模拟器 MoodiaryApi35。

最终运行命令：

```powershell
gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :domain:test :app:lintDebug :app:connectedDebugAndroidTest --continue --console=plain --no-daemon
```

实际使用本机同版本 Gradle 8.13 执行上述任务，结果 **BUILD SUCCESSFUL**。

| 检查 | 结果 |
| --- | --- |
| Debug build | 通过 |
| Release build | 通过，未签名；不是正式发行包 |
| domain JVM tests | 34 通过 |
| app JVM tests | 34 通过 |
| Android / Room / Compose tests | 15 通过 |
| 测试总计 | 83 通过，0 失败、0 错误、0 跳过 |
| lintDebug | 0 错误，44 警告；仍需逐步治理 |
| Room | schema 1 JSON 保留；真实 Room 重开/关联与不支持版本拒绝测试通过 |
| Backup / Restore | 实际 ZIP + 合成照片往返、损坏拒绝、回滚与自动备份测试通过 |
| 版本检查 | app 0.1.0、versionCode 1、Room 1 未更改 |

初始设备运行保留了先前手动验证的中文应用语言，英文文案定位的 UI 测试超时。修正专用测试环境为英文后重跑全部任务，通过 83 项测试。没有为绕过失败而删除测试或放宽断言。语言专项测试自行切换中英文并验证持久化与草稿；其余文案定位测试要求英文环境，见 README。这是当前测试套件的环境前提，后续可改为资源/稳定标签定位。

## Git 与敏感资料审查

- 项目原本没有独立 `.git`；上级用户目录存在空的 master 仓库，没有提交、远端或跟踪本项目的文件，保留未改动。
- 在本项目创建独立 main。用户指定远端 `https://github.com/maskkkkk1000/Moodeiary.git`，认证后只读核验为没有引用的空仓库。
- 首个逻辑提交为 `chore: establish v0.1.0 baseline`；验证成功后创建 annotated tag `v0.1.0`，不伪造开发历史。
- 审查源码、测试、schema、构建配置、Wrapper 和文档；仅包含合成测试输入，不包含真实用户日记、照片或 PIN。
- 路径扫描、常见私钥/GitHub/Google/AWS token 模式及凭据赋值/含密码 URL 扫描未发现敏感内容。模式扫描不等于对任意格式秘密的绝对保证，后续每次提交仍必须审查。
- 本机工具、缓存、构建产物、数据库、照片、归档、截图、日志和测试输出不提交。原始报告只保留在忽略的 build/verification 目录。
- GitHub 分支保护及云端 CI 不属于本次已验证配置；协作约定已经写入 AGENTS.md 与 VERSION_CONTROL.md。

## 发布边界

无历史生产 schema，所以没有虚构 0→1 迁移；后续发布 schema 变更必须显式迁移并测试。真实 Drive OAuth/云端往返、目标手机生物识别、厂商后台/长期提醒、完整 TalkBack 和正式签名发行仍待验收。详见 IMPLEMENTATION_STATUS.md 与 RELEASE_CHECKLIST.md。
