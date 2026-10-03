
# Moodiary 第一轮正式迭代需求说明

版本目标：v0.2.0
建议开发分支：`feature/iteration-1`

当前稳定基线：v0.1.0

本轮迭代目标：

在保持现有数据、功能和兼容性的前提下，重点提升 Moodiary 的交互体验、自定义能力、统计灵活性和日常使用效率，并新增一套独立的 0/1 小目标系统。

本轮不是简单 UI 微调，而是一次完整的产品体验迭代。

---

# 1. Codex 执行要求

请先完整阅读本文件。

本文件是本轮迭代的权威需求说明。

在开始修改前：

1. 检查当前 Git 状态。
2. 确认当前 branch、remote、commit 和 tag。
3. 确认当前稳定版本为 v0.1.0。
4. 不破坏已有 Git 历史。
5. 不删除现有功能。
6. 不使用 destructive Room migration。
7. 不破坏已有用户数据。
8. 不改变现有备份格式而不做版本兼容处理。
9. 不直接在 main 上进行未经验证的大规模修改。
10. 建议新建：

```text
feature/iteration-1
```

本轮完成并通过测试后，再合并到 main。

每完成一个独立逻辑阶段：

- 检查 git diff
- 构建项目
- 运行相关测试
- 修复确认问题
- 创建清晰 commit

使用 Conventional Commits，例如：

```text
feat: redesign mood entry interactions
feat: add customizable mood and activity icons
feat: add statistics filters
feat: add binary daily goals
fix: preserve statistics filter state
test: add binary goal regression tests
```

不要为了制造历史而伪造不存在的旧 commit。

---

# 2. 本轮核心目标

本轮主要解决以下问题：

1. 当前 UI 过于僵硬、工具化，缺少生活记录类 App 应有的轻盈感。
2. Mood 和 Activity 图标自定义能力不足。
3. Statistics 页面一次展示大量内容，用户无法只看自己关心的部分。
4. App 启动后默认进入历史记录，不符合“快速记录”的核心使用场景。
5. 缺少一种非常简单的每日 0/1 目标系统。
6. 新增功能必须完整接入：
   - Room
   - Backup
   - Restore
   - JSON export
   - Statistics
   - Tests

---

# 3. UI / UX 整体重构

## 3.1 当前问题

现有界面偏向：

```text
工具
后台
设置面板
管理页面
```

整体过于规整和僵硬。

希望调整为：

```text
轻量
自然
柔和
生活化
有反馈
有呼吸感
```

参考方向可以借鉴 Daylio 一类生活记录 App 的“轻盈感”，但不要复制其具体 UI、品牌、图标、布局或文案。

---

# 4. UI 设计目标

重点优化：

- 圆角更柔和
- 减少硬边框
- 增加留白
- 减少密集文字
- 强化 Emoji / Icon
- 强化卡片层次
- 点击时有自然反馈
- 选择时有视觉反馈
- 页面切换更自然
- 统计卡片不应全部堆叠显示
- 编辑页优先突出心情和活动，而不是文字表单

可以合理使用：

```text
AnimatedVisibility
AnimatedContent
Crossfade
animateContentSize
animateFloatAsState
```

但不要为了动画牺牲：

```text
性能
稳定性
可访问性
低端设备流畅度
```

动画应：

```text
轻
短
自然
不过度
```

---

# 5. Mood 图标可自定义

当前 Mood 图标不能满足用户自定义需求。

用户应可以修改：

```text
Mood.icon
```

例如：

```text
开心

😀
↓
😄
↓
🌞
↓
🥳
```

必须支持：

- Emoji 选择
- 自定义 Emoji 输入
- 图标预览
- 修改后持久保存
- 历史 Mood 仍能正常显示

如果当前 Mood 已存在 icon 字段，优先复用。

如果数据库结构不足，则使用显式 Room Migration。

---

# 6. Activity 图标可自定义

Activity 也必须支持图标更换。

例如：

```text
学习

📚
💻
✏️
🧠
```

支持：

```text
Emoji
Material Icon
```

Emoji 优先实现。

允许：

- 修改图标
- 修改颜色
- 修改名称
- 排序
- 归档
- 恢复

历史 Entry 不得因为 Activity 修改或归档而损坏。

---

# 7. 小目标图标

本轮新增的小目标系统也支持：

```text
icon
```

例如：

```text
📚 完成一道算法题
🌙 12 点前睡觉
🥤 今天不喝奶茶
🏃 跑步
```

---

# 8. 图标数据要求

图标必须存储在数据层。

不得只写死在 UI。

目标结构：

```text
Mood.icon
Activity.icon
BinaryGoal.icon
```

建议使用：

```text
String
```

保存 Emoji 或内置图标标识。

---

# 9. Statistics 页面重构

## 9.1 当前问题

现在 Statistics 页面倾向于把：

```text
趋势
分布
活动
关联
星期
Goal
其他分析
```

全部一次显示出来。

问题是：

用户很多时候并不想看全部统计。

例如用户可能只想看：

```text
最近30天
学习
运动
心情趋势
```

因此需要完整筛选体系。

---

# 10. Statistics 顶部筛选器

页面顶部增加统一筛选入口。

推荐布局：

```text
[最近30天] [心情] [活动] [统计项目]
```

使用：

```text
Chip
FilterChip
BottomSheet
```

实现。

---

# 11. 时间范围筛选

支持：

```text
7 天
30 天
3 个月
1 年
全部
自定义
```

自定义范围：

```text
Start Date
End Date
```

所有统计模块使用同一时间范围。

---

# 12. Mood 筛选

支持：

```text
全部心情
Amazing
Good
Meh
Bad
Awful
自定义 Mood
```

支持多选。

例如：

```text
只看 Good + Amazing
```

则所有统计基于符合条件的数据重新计算。

---

# 13. Activity 筛选

支持：

```text
全部 Activity
学习
运动
游戏
社交
睡眠
...
```

支持多选。

例如：

```text
学习 + 运动
```

统计结果只针对符合筛选条件的数据。

必须明确 Activity 多选使用：

```text
OR
```

还是：

```text
AND
```

建议默认：

```text
OR
```

并在未来可扩展 AND。

---

# 14. Statistics 模块筛选

用户应可以选择自己想看的统计卡片。

例如：

```text
☑ Mood Trend
☑ Mood Distribution
☐ Activity Frequency
☑ Activity-Mood Association
☐ Next-Day Association
☐ Weekday Pattern
☑ Goal Completion
☑ Binary Goal Completion
```

未选中的模块：

```text
不展示
不计算或尽量延迟计算
```

避免无意义地全部计算再隐藏。

---

# 15. Statistics 筛选状态持久化

用户上一次的统计设置需要保留。

例如：

```text
最近30天
Study + Exercise
Mood Trend
Binary Goals
```

下次进入 Statistics 时仍保持。

使用：

```text
DataStore
```

保存。

必须支持：

```text
Reset Filters
```

恢复默认状态。

---

# 16. Statistics 计算要求

所有现有统计逻辑继续保持：

```text
平均 Mood
Mood Trend
Mood Distribution
Activity Frequency
Activity-Mood Association
Next-Day Association
Weekday Pattern
Goal Statistics
```

新增：

```text
Binary Goal Statistics
```

筛选后的统计必须基于：

```text
Filtered Entries
```

重新计算。

不能出现：

```text
UI 显示已筛选
但底层统计仍使用全部数据
```

---

# 17. Statistics 性能要求

不要每次 Compose recomposition 都重新跑所有统计。

建议：

```text
Filters
↓
ViewModel
↓
UseCase
↓
Background Calculation
↓
UiState
```

对于大量数据：

```text
debounce
distinctUntilChanged
cached state
```

可以合理使用。

---

# 18. App 启动页调整

## 18.1 当前问题

当前打开 App 后优先显示历史日记。

这不符合 Moodiary 的核心场景：

```text
打开
→ 快速记录
```

---

# 19. 新启动逻辑

正常启动 App 时：

```text
Launch
↓
Entry Editor
```

即直接进入：

```text
选择 Mood
选择 Activity
输入 Note
添加 Photo
Save
```

这样打开 App 就能立即记录。

---

# 20. 启动逻辑例外

以下情况不得强制跳转新建 Entry：

## Notification Deep Link

如果用户从：

```text
Goal Reminder
Entry Reminder
Binary Goal Reminder
```

进入，应导航到对应目标页面。

---

## Edit Existing Entry

如果用户从：

```text
Timeline
Calendar
Search
```

点击某条历史记录，应进入编辑状态。

---

## Draft Recovery

如果存在合法未保存草稿：

应优先恢复草稿。

---

## Backup Restore State

如果当前处于：

```text
Restore Recovery
Data Recovery Lock
```

不得绕过恢复状态直接创建新 Entry。

---

# 21. 新增 Binary Goal 系统

本轮新增独立系统：

```text
Binary Goal
```

UI 中文建议叫：

```text
小目标
```

它与现有 Goal 系统不同。

---

# 22. Binary Goal 设计理念

现有 Goal 更适合：

```text
每周跑步3次
每天学习
周一三五运动
连续打卡
```

Binary Goal 更简单：

```text
今天做到没有？
```

只有：

```text
1
0
未记录
```

---

# 23. Binary Goal 状态定义

必须区分三种状态：

```text
UNSET
SUCCESS = 1
FAILED = 0
```

重要：

```text
UNSET ≠ FAILED
```

用户今天没有记录结果：

不能自动算作失败。

---

# 24. Binary Goal 示例

用户可以创建：

```text
今天不喝奶茶
今天完成算法题
今天跑步
今天早睡
今天背单词
今天不刷短视频
```

---

# 25. Binary Goal 数据结构

建议新增：

```text
BinaryGoal
----------
id
name
icon
description
sortOrder
isArchived
createdAt
updatedAt
```

以及：

```text
BinaryGoalRecord
----------------
id
goalId
date
value
createdAt
updatedAt
```

其中：

```text
value = 1
```

表示成功。

```text
value = 0
```

表示失败。

没有 BinaryGoalRecord：

表示：

```text
UNSET
```

---

# 26. Binary Goal 唯一性

同一个 Goal 同一天只允许一个逻辑记录。

建议唯一约束：

```text
(goalId, date)
```

再次点击结果：

应更新已有记录。

不得重复插入多条。

---

# 27. Binary Goal 今日页面

在适当位置展示：

```text
今日小目标
```

例如：

```text
📚 完成算法题

[ ✓ ] [ × ]


🌙 12点前睡觉

[ ✓ ] [ × ]


🥤 不喝奶茶

[ ✓ ] [ × ]
```

状态：

```text
未设置
成功
失败
```

再次点击可以修改。

---

# 28. Binary Goal 交互

建议：

成功：

```text
✓
```

失败：

```text
×
```

未设置：

两个按钮均为未选中状态。

点击已选择状态：

可以：

```text
保持
```

或者再次点击取消成 UNSET。

建议支持取消。

---

# 29. Binary Goal 管理页

支持：

```text
Create
Edit
Reorder
Archive
Restore
Delete if safe
```

优先使用：

```text
Archive
```

避免历史统计损坏。

---

# 30. Binary Goal 周统计

例如：

```text
完成算法题

Mon ✓
Tue ✓
Wed ×
Thu ✓
Fri ✓
Sat —
Sun —

Success: 4
Failed: 1
Unset: 2

Completion Rate:
4 / 5 = 80%
```

其中：

```text
—
```

表示未记录。

---

# 31. Binary Goal 月统计

例如：

```text
September

Success: 21
Failed: 6
Unset: 3

Recorded Days:
27

Completion Rate:
21 / 27 = 77.8%
```

---

# 32. Binary Goal 完成率定义

完成率必须使用：

```text
success / (success + failed)
```

不是：

```text
success / natural calendar days
```

UNSET 不进入完成率分母。

---

# 33. Binary Goal Statistics

支持时间范围：

```text
This Week
This Month
7 Days
30 Days
3 Months
1 Year
Custom
All
```

显示：

```text
Success Count
Failed Count
Recorded Count
Unset Count
Completion Rate
```

---

# 34. Binary Goal 趋势

支持：

```text
Weekly Completion Rate
Monthly Completion Rate
```

例如：

```text
Week 1  65%
Week 2  78%
Week 3  84%
Week 4  90%
```

---

# 35. Binary Goal 总统计接入

Statistics 页面新增：

```text
Binary Goal Statistics
```

允许：

```text
All Binary Goals
Single Goal
Multiple Goals
```

支持：

```text
Time Filter
Goal Filter
Module Filter
```

---

# 36. Binary Goal 与现有 Goal 的关系

不要强行合并。

保持：

```text
Goal
→ Habit / Frequency / Streak

Binary Goal
→ Daily Success / Failure
```

两套逻辑独立。

未来如果需要，可以在 UI 层统一归入：

```text
Goals
```

但数据模型不要强耦合。

---

# 37. Binary Goal 提醒

如果实现成本合理，可以在本轮支持：

```text
Daily Reminder
```

例如：

```text
今天的小目标完成了吗？
```

但如果会扩大本轮范围，可以留到下一版本。

核心 Binary Goal 不依赖提醒才能工作。

---

# 38. 数据库升级要求

当前：

```text
Room Schema = 1
```

如果本轮新增表或字段：

必须：

```text
Schema 1
↓
Schema 2
```

使用显式：

```text
Migration(1, 2)
```

禁止：

```text
fallbackToDestructiveMigration
```

---

# 39. Migration 测试

必须验证：

```text
v0.1.0 existing database
↓
upgrade app
↓
v0.2.0 database
```

保证：

```text
Entries
Moods
Activities
Goals
Photos
Templates
Important Days
Settings
```

全部保留。

新增 BinaryGoal 表为空即可。

---

# 40. Backup 格式升级

当前 Backup Format 需要加入：

```text
BinaryGoals
BinaryGoalRecords
```

如果 backup format 有版本号：

建议升级。

例如：

```text
formatVersion 1
↓
formatVersion 2
```

必须考虑：

```text
旧备份恢复到新 App
```

仍然可用。

---

# 41. Restore

恢复必须支持：

```text
BinaryGoal
BinaryGoalRecord
```

旧版本 backup 不包含这些数据时：

```text
BinaryGoal = empty
```

不能恢复失败。

---

# 42. JSON Export

JSON Export 新增：

```json
{
  "binaryGoals": [],
  "binaryGoalRecords": []
}
```

必须与 Backup 数据保持一致。

---

# 43. CSV

可以考虑新增独立：

```text
binary-goals.csv
```

或者增加导出选项。

不是本轮必须项。

---

# 44. PDF

可以在统计报告中加入：

```text
Binary Goal Summary
```

例如：

```text
Goal
Success
Failed
Completion Rate
```

如果当前 PDF 架构易扩展则实现。

否则可列为后续增强。

---

# 45. UI 页面建议

本轮最终可能形成：

```text
Entry Editor
Timeline
Calendar
Statistics
More
```

More：

```text
Goals
Binary Goals
Moods
Activities
Templates
Reminders
Backup
Export
Privacy
Settings
```

---

# 46. Entry Editor 灵动化

重点优化：

```text
Mood selection
Activity selection
Save interaction
```

Mood 可以使用更大：

```text
Emoji / Icon
```

选中时：

```text
scale
background
subtle animation
```

Activity 推荐：

```text
FlowRow
Chip
Icon
```

而不是硬列表。

---

# 47. Timeline 灵动化

每条 Entry 卡片可以包含：

```text
Mood Icon
Time
Activities
Note Preview
Photo Preview
```

弱化：

```text
边框
分割线
```

强化：

```text
卡片
间距
图标
层次
```

---

# 48. Statistics 灵动化

Statistics 不应成为：

```text
几十张卡片无限向下堆
```

建议：

```text
Filters
↓
Selected Modules
↓
Cards
```

每个模块：

```text
可折叠
可隐藏
```

可以考虑：

```text
AnimatedVisibility
```

---

# 49. Empty State

所有新页面必须有空状态。

例如：

```text
还没有小目标

创建一个今天想做到的小事情吧
```

而不是空白页面。

---

# 50. Error State

至少覆盖：

```text
database error
backup error
statistics calculation error
invalid custom range
```

不要直接崩溃。

---

# 51. Loading State

统计量较大时：

使用：

```text
Loading
```

而不是主线程卡死。

---

# 52. Accessibility

保持：

```text
48dp minimum touch targets
contentDescription
text scaling
contrast
dark mode
```

新增 Icon Picker 也必须支持语义标签。

---

# 53. 本轮测试要求

至少新增以下测试。

## Binary Goal

```text
Create Goal
Edit Goal
Archive Goal
Record SUCCESS
Record FAILED
Reset to UNSET
Same day update
Unique goal/date behavior
Week statistics
Month statistics
Custom statistics
Completion Rate
```

---

# 54. Completion Rate Tests

必须验证：

```text
1,1,1,0
```

结果：

```text
75%
```

验证：

```text
1,1,UNSET,0
```

结果仍然：

```text
66.67%
```

而不是：

```text
50%
```

---

# 55. Statistics Filter Tests

测试：

```text
Date only
Mood only
Activity only
Multiple Activity
Mood + Activity
Date + Mood
Date + Activity
All Filters Combined
```

---

# 56. Filter Persistence Tests

验证：

```text
change filter
↓
leave screen
↓
return
```

仍然保留。

App restart 后仍然保留。

---

# 57. Startup Navigation Tests

测试：

```text
Normal Launch
→ Entry Editor
```

测试：

```text
Notification Deep Link
→ Correct Destination
```

测试：

```text
Edit Entry
→ Existing Entry Editor
```

测试：

```text
Draft Recovery
→ Draft
```

---

# 58. Migration Tests

测试：

```text
Schema 1
↓
Schema 2
```

已有数据必须全部保留。

---

# 59. Backup Round Trip

测试：

```text
Create Binary Goals
Record Results
Backup
Clear Data
Restore
```

验证：

```text
Goal names
icons
records
statistics
```

全部恢复。

---

# 60. Performance

至少测试：

```text
10 goals
100 goals
1000 goals
```

以及：

```text
1 year BinaryGoalRecords
```

统计不得明显卡顿。

---

# 61. 本轮任务建议顺序

## Iteration 1.1

UI / Design System 调整

---

## Iteration 1.2

Mood / Activity Icon Picker

---

## Iteration 1.3

Statistics Filters

---

## Iteration 1.4

Statistics Module Visibility

---

## Iteration 1.5

Startup Navigation

---

## Iteration 1.6

Binary Goal Database

---

## Iteration 1.7

Binary Goal Management

---

## Iteration 1.8

Daily Binary Goal UI

---

## Iteration 1.9

Weekly / Monthly Statistics

---

## Iteration 1.10

Integrate Binary Goals into Statistics

---

## Iteration 1.11

Migration + Backup + Restore

---

## Iteration 1.12

Tests + Regression Audit

---

# 62. 本轮版本规划

当前：

```text
v0.1.0
```

开发分支：

```text
feature/iteration-1
```

本轮完成：

```text
v0.2.0
```

原因：

本轮包含：

```text
重大 UI 改进
图标系统
统计筛选
启动行为变化
完整 Binary Goal 系统
数据库升级
备份升级
```

属于：

```text
MINOR RELEASE
```

不是 PATCH。

---

# 63. Git 要求

每个逻辑阶段：

```text
commit
```

建议：

```text
feat: refresh Moodiary interaction design

feat: add customizable mood and activity icons

feat: add persistent statistics filters

feat: make entry editor the default launch destination

feat: add binary daily goals

feat: add binary goal statistics

feat: migrate database for iteration 1

feat: extend backup format for binary goals

test: add iteration 1 regression coverage
```

---

# 64. Merge 要求

在合并：

```text
feature/iteration-1
→
main
```

之前必须：

```text
assembleDebug
assembleRelease
unit tests
Room tests
Compose tests
lint
migration tests
backup round trip
```

全部通过。

---

# 65. Release Checklist

创建：

```text
v0.2.0
```

之前：

```text
[ ] Build Debug
[ ] Build Release
[ ] JVM Tests
[ ] Android Tests
[ ] Compose Tests
[ ] Room Migration Test
[ ] Backup Restore Test
[ ] Statistics Filter Test
[ ] Binary Goal Tests
[ ] Sensitive File Scan
[ ] CHANGELOG Update
[ ] README Update
[ ] ROADMAP Update
[ ] versionName Update
[ ] versionCode Update
```

全部完成。

---

# 66. 本轮最终验收标准

本轮完成后：

## UI

```text
[ ] UI 比当前明显更加轻盈
[ ] 卡片层级自然
[ ] 动画自然
[ ] 深色模式正常
[ ] 大字号正常
```

## Icon

```text
[ ] Mood 图标可更换
[ ] Activity 图标可更换
[ ] Binary Goal 图标可更换
[ ] 图标持久保存
```

## Statistics

```text
[ ] 时间筛选
[ ] Mood 筛选
[ ] Activity 筛选
[ ] 模块筛选
[ ] 自定义日期
[ ] 筛选状态保存
[ ] Reset Filters
```

## Startup

```text
[ ] 正常启动直接进入 Entry Editor
[ ] Deep Link 不受影响
[ ] Edit Entry 不受影响
[ ] Draft Recovery 不受影响
```

## Binary Goal

```text
[ ] Create
[ ] Edit
[ ] Archive
[ ] Icon
[ ] SUCCESS
[ ] FAILED
[ ] UNSET
[ ] Weekly Statistics
[ ] Monthly Statistics
[ ] Custom Range
[ ] Completion Rate
```

## Data

```text
[ ] Room Migration
[ ] Existing Data Preserved
[ ] Backup Updated
[ ] Restore Updated
[ ] JSON Export Updated
```

## Tests

```text
[ ] Unit Tests Pass
[ ] Room Tests Pass
[ ] Compose Tests Pass
[ ] Migration Tests Pass
[ ] Backup Tests Pass
[ ] lint has no blocking errors
```

---

# 67. 最终原则

本轮不要为了“更像 Daylio”而直接复制 Daylio。

目标是：

```text
更轻盈
更快速
更可定制
更灵活
更符合日常使用
```

Moodiary 应继续保持自己的产品身份。

本轮最重要的体验变化应该是：

```text
打开 App
↓
立刻记录

想看统计
↓
只看我关心的

想设置习惯
↓
可以用复杂 Goal

只想判断今天做没做到
↓
使用 Binary Goal
```

---

# 68. Codex 最终执行指令

请完整实现本文件定义的第一轮迭代。

不要只创建接口、占位类或假实现。

保持当前已有功能。

优先保证：

1. 数据安全
2. 老版本兼容
3. Migration 正确
4. Backup / Restore 正确
5. 统计逻辑正确
6. UI 体验
7. 性能

完成后：

1. 执行完整构建。
2. 执行所有相关测试。
3. 执行 lint。
4. 执行 Schema 1 → Schema 2 migration test。
5. 执行完整 backup → restore round trip。
6. 检查现有 v0.1.0 数据是否完整保留。
7. 检查 Git diff。
8. 更新 CHANGELOG.md。
9. 更新 ROADMAP.md。
10. 更新 README。
11. 提交经过验证的 commit。
12. 如果全部满足要求，将版本准备为 v0.2.0。
13. 不要在测试未完成时声称 v0.2.0 已稳定发布。

最终报告必须明确列出：

- 已完成内容
- 未完成内容
- 数据库版本
- Migration 状态
- Backup / Restore 状态
- 测试结果
- 构建结果
- 已知问题
- 性能情况
- Git branch
- 最新 commit
- 是否建议合并 main
- 是否建议创建 v0.2.0 tag
```

这版比我上一条更适合直接丢给 Codex，因为我把**数据库迁移、备份兼容、统计定义、测试、Git 和 v0.2.0 发布条件**都补进去了。

另外我特意把你说的“**完成=1、失败=0**”扩展成了 `1 / 0 / UNSET` 三状态。这样“今天忘记填”不会被错误统计成失败，否则一个月统计会很容易失真。
