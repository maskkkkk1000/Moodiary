# 版本管理约定

## 版本与提交

使用 Semantic Versioning `MAJOR.MINOR.PATCH`：PATCH 为修复及小型兼容调整；MINOR 为新功能、明显增强和向后兼容的数据升级；MAJOR 为重大不兼容的数据格式或核心行为变化。即使在 0.x 阶段，也必须明确记录不兼容变化及安全升级路径。不能仅因功能多而标记 1.0。

Android `versionCode` 是安装升级序号，每次分发新版本单调增加；`versionName` 是应用版本。Room schema 和备份格式分别独立管理。当前为 app 0.1.0 / versionCode 1 / Room 1。

初始已有实现作为一次真实基线提交 `chore: establish v0.1.0 baseline`，不补造历史。以后一个提交对应一个经过验证的逻辑阶段，使用 Conventional Commits：`feat:`、`fix:`、`perf:`、`test:`、`refactor:`、`docs:`、`chore:`；不混入无关文件。

## 分支与合并

- `main`：可构建、已验证的稳定状态。
- `feature/<topic>`：新功能；`fix/<topic>`：修复；`release/<version>`：发布准备。
- 大型改动在独立分支完成，检查差异、构建、相关测试后通过 PR 合并。保留公共历史，不随意 rebase 已推送分支。
- 禁止向 main force push、删除已有历史或移动已公开的版本标签。
- GitHub 配置后建议为 main 开启分支保护：禁止强推和删除、PR 合并、要求验证通过。尚未配置远端时不能声称服务端保护已启用。

## 每次提交前

2026-09-30 隐私发布：经仓库所有者授权，原始仓库改名后保持私有；新公开仓库使用保留文件树、提交顺序、日期和消息的匿名化历史，提交与标签邮箱统一使用 GitHub 隐私邮箱。此次迁移不覆盖私有存档历史，不强推 main。今后开发只向公开仓库的匿名化历史追加提交，不把存档分支合并回公开仓库。

1. 确认 Git 根目录、branch、remote、status 和历史，避免误操作父目录仓库。
2. 检查工作区 diff、暂存区完整 diff 和 `git diff --cached --name-status`，只暂存本阶段文件。
3. 扫描密钥、token、OAuth 文件、签名文件、真实日记/照片/备份及本地构建产物。`.gitignore` 不能识别源代码里硬编码的密钥，必须审查内容。
4. 构建项目；核心逻辑修改运行相关测试；失败则先修复。记录设备与未验证边界。
5. 提交后检查 status、commit hash，保持 README、CHANGELOG 和 ROADMAP 一致。

## 标签与远端

稳定版本使用 annotated tag，如 `git tag -a v0.1.0 -m "Moodiary v0.1.0 baseline"`，仅在该提交验证成功后创建。使用明确的分支和标签推送，不使用 `--force` 或笼统推送所有标签。

首次推送前向用户汇报状态、文件范围、忽略项、敏感扫描、commit/tag、remote 和 branch。远端地址必须由用户提供；认证交给本机 Git 凭据管理器或 SSH，不在聊天、命令参数或仓库中存放 token。若远端非空，先读取其历史并制定保留历史的集成方案，不能直接覆盖。

## 数据库升级

保留 `app/schemas/` 中每一版 JSON。已发布 schema 改动必须 schema +1、显式 Migration 和从旧版本真实数据库升级的测试，并检查外键、行数据、关联、备份恢复。初始 schema 1 没有更旧生产版本，不伪造迁移。禁止 destructive fallback。
