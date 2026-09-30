# Changelog

使用 Keep a Changelog 风格，应用版本遵循 Semantic Versioning。Room schema 独立管理。

## [Unreleased]

### Added

- README 新增 GitHub Issues 问题反馈入口。
- Windows 发行签名脚本和 Debug → 正式签名版备份迁移说明；仓库地址更正为 `maskkkkk1000/Moodiary`。

### Changed

### Fixed

- 设备测试：语言恢复清理避免等待已被重建的界面帧；大数据页面跳转前结束输入法切换。生产应用和数据库结构不变。

### Security

- 公开仓库的历史提交及标签使用 GitHub 隐私邮箱；原始历史保留在独立私有存档，应用源码、数据结构及签名 APK 不变。

## [0.1.0] - 2026-09-28

首个正式 Git 开发基线，非 1.0 正式发行。此前没有项目提交历史，不补造开发提交。

### Added

- Kotlin/Compose/Material 3、Hilt、Room、DataStore、WorkManager 本地优先应用。
- 日记增删改、多条/天、私有照片、时间线、日历、搜索与筛选。
- 情绪、活动、分组、模板、目标与自动联动、连续记录、提醒、重要日、成就和主题。
- 心情统计、活动与次日关联及非因果/样本量提示。
- 中文、英文和跟随系统语言切换。
- 本地备份、归档校验与恢复回滚、自动备份、JSON/CSV/PDF 导出。
- Google Drive 可选授权与备份集成；真实 OAuth 云端验收待用户配置。
- JVM、Room 集成、Compose UI、数据恢复和大数据集回归测试。
- Git 忽略规则、协作规范、版本路线图和发布检查单。

### Security

- PIN 盐化哈希与尝试限制、平台生物识别接入；无广告或行为追踪 SDK。
- 用户照片复制到私有存储；备份排除 PIN 和云凭据；恢复前验证归档。
- Room schema 1 导出归档，无 destructive migration fallback。

### Known limitations

- 备份与导出未加密，PIN 是界面锁而非数据库加密。
- 真正 Drive 往返尚未验收。已收到用户真机可用反馈；跨厂商后台策略、长期使用和完整 TalkBack 仍需扩大验证范围。
- 提醒可能受系统调度延迟；大照片/长文本数据集仍需内存压力测试。
- 2026-09-28 基线构建未签名；2026-09-29 为同一 v0.1.0 源码制作独立发行签名 APK，用户反馈除 OAuth 外真机功能可用。签名和迁移方式见 `docs/INSTALL_AND_SIGNING.md`。
