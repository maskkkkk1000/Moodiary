# 发布检查单

每个发布候选复制本清单并记录 commit、日期、工具链、设备、结果及阻塞。不能用旧测试报告替代代码变更后的验证。0.1.0 的实际记录见 [BASELINE_VERIFICATION](BASELINE_VERIFICATION.md)。

- [ ] 检查工作区、分支、远端、历史和完整 diff，排除无关改动。
- [ ] Debug build：`:app:assembleDebug`。
- [ ] Release build：`:app:assembleRelease`；正式发行还必须使用仓库外签名密钥签名并安装验证。
- [ ] JVM tests：`:domain:test :app:testDebugUnitTest`。
- [ ] Android tests：`:app:connectedDebugAndroidTest`，使用专用测试设备，避免真实日记丢失。
- [ ] Compose tests：同一 connected 测试任务中的关键用户路径，包括语言切换。
- [ ] lint：`:app:lintDebug`，无错误，审查剩余警告。
- [ ] Room schema 与运行时结构一致；历史 JSON 保留；存在升级时执行真实旧版本升级测试。
- [ ] backup → restore 真实归档往返，含照片、设置、损坏归档拒绝和失败/中断回滚。
- [ ] 敏感文件及内容扫描；不得包含真实用户数据、签名密钥、OAuth 凭据、token 或明文 PIN。
- [ ] CHANGELOG、README、ROADMAP 与已验收范围一致，列出未验证和已知风险。
- [ ] versionName/versionCode 符合本次发布；Room schema 与 backup format 独立检查。
- [ ] 对已经验证的提交创建 annotated Git tag，版本号与应用一致。
- [ ] 首次推送前报告文件范围、忽略项、扫描结果、commit/tag、remote、branch。
- [ ] 核对 GitHub 提交与标签；配置 main 保护规则，禁止强推和删除。

1.0 额外要求：真实设备长期测试、无障碍、后台/提醒、生物识别、真实云端恢复与异常处理、迁移和完整可靠性审计全部符合发布标准。任何未完成项必须明确记录，不能默认为通过。
