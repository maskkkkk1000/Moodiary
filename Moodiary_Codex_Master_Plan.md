# Moodiary — Codex Master Development Plan

> Purpose: This document is the single source of truth for building a near-complete Daylio-style Android application with Codex.
>
> Working title: **Moodiary**
>
> Primary platform: **Android**
>
> Primary goal: Reproduce the useful functional scope of a mature mood-journal app while using original naming, visual design, icons, wording, and assets. Do not copy Daylio trademarks, proprietary artwork, screenshots, exact UI layouts, or other protected assets.

---

## 0. Instructions to Codex

You are implementing this project incrementally.

### Core operating rules

1. Read this document before making architectural decisions.
2. Treat this document as the authoritative product and technical specification unless the user explicitly overrides it.
3. Do not attempt to implement the entire application in one giant change.
4. Work phase-by-phase in the order defined below unless a dependency requires a justified adjustment.
5. Keep the application compiling after every completed task.
6. After each substantial implementation:
   - run the relevant build,
   - run applicable tests,
   - fix compilation errors,
   - fix confirmed regressions,
   - summarize changed files and remaining work.
7. Do not leave placeholder production code, fake implementations, TODO-only functions, or intentionally broken stubs in completed phases.
8. Prefer maintainability, data safety, and predictable behavior over clever abstractions.
9. Avoid overengineering. Add abstraction only when it improves testability, modularity, or future maintenance.
10. Never silently change the database schema without:
    - updating Room entities,
    - updating migrations,
    - updating tests,
    - verifying restore/backup compatibility when relevant.
11. Do not hard-code the app to exactly five moods internally. The default UI may ship with five moods, but the data model must support customization.
12. Do not use a calendar date as an Entry primary key. Multiple entries per day are required.
13. Do not store critical app state only in Compose state.
14. All persistent user data must survive process death and normal app restarts.
15. All destructive actions must require a clear confirmation flow where appropriate.
16. User data is private by default and should remain local unless the user explicitly enables export or cloud backup.
17. Do not upload diary content to any external service as part of the base implementation.
18. No analytics SDK, advertising SDK, behavioral tracking SDK, or telemetry service is required for the self-use version.
19. Use original visual design. Functional similarity is acceptable; pixel-copying another product is not.
20. Prefer small, reviewable commits/tasks rather than sweeping unrelated rewrites.

### Definition of done for every task

A task is not complete until:

- the project compiles,
- relevant tests pass,
- new state survives recreation when persistence is expected,
- navigation to the new feature works,
- empty states are handled,
- basic error states are handled,
- no obvious crash path remains,
- the implementation is integrated with the current architecture.

---

# 1. Product Vision

Moodiary is a local-first personal mood, activity, habit, and life-journal application.

The app should make daily logging extremely fast while providing meaningful long-term analysis.

Primary interaction:

**Mood → Activities → Note → Optional photos → Save**

Long-term value:

**Entries → Calendar → Trends → Goals → Behavioral insights**

The finished Android application should be usable as a daily replacement for a premium mood-tracking journal.

---

# 2. Major Product Principles

## 2.1 Fast entry

A normal entry should require only a few taps.

The user must be able to:

- choose a mood,
- choose zero or more activities,
- optionally type a note,
- optionally attach photos,
- save.

## 2.2 Local-first privacy

The database and media should remain on-device unless explicitly exported or backed up.

## 2.3 Data ownership

The user must be able to export and restore their information without depending on a subscription or remote account.

## 2.4 Useful analytics

Statistics should answer questions such as:

- How has my mood changed?
- Which activities happen most often?
- Which activities are associated with better or worse mood?
- Which activities correlate with next-day mood?
- What days of the week tend to be better?
- Which goals am I actually completing?
- Is an apparent pattern based on enough data to trust?

## 2.5 Robustness over novelty

A boring but reliable database, migration, backup, and restore implementation is preferable to flashy features that can lose data.

---

# 3. Recommended Technology Stack

Use modern stable Android libraries.

## Language

- Kotlin

## UI

- Jetpack Compose
- Material 3

## Architecture

- MVVM
- Repository pattern
- Use-case/domain layer where useful
- Unidirectional UI state where practical

## Persistence

- Room
- DataStore Preferences or Proto DataStore for app settings
- App-private storage for imported photo copies

## Dependency injection

- Hilt

## Navigation

- Navigation Compose

## Async/reactive

- Kotlin Coroutines
- Flow / StateFlow

## Background work

- WorkManager
- AlarmManager only where exact user-facing alarm semantics justify it

## Authentication / device security

- Android BiometricPrompt

## File handling

- Storage Access Framework
- Android Photo Picker where supported

## Testing

- JUnit
- Room tests
- ViewModel/use-case tests
- Compose UI tests where valuable

---

# 4. Suggested Project Structure

```text
app/
│
├── core/
│   ├── common/
│   ├── database/
│   ├── datastore/
│   ├── designsystem/
│   ├── notifications/
│   ├── backup/
│   ├── export/
│   └── security/
│
├── data/
│   ├── repository/
│   ├── mapper/
│   └── datasource/
│
├── domain/
│   ├── model/
│   └── usecase/
│
├── feature/
│   ├── home/
│   ├── entries/
│   ├── entryeditor/
│   ├── calendar/
│   ├── statistics/
│   ├── goals/
│   ├── activities/
│   ├── moods/
│   ├── reminders/
│   ├── search/
│   ├── templates/
│   ├── backup/
│   ├── export/
│   ├── security/
│   └── settings/
│
└── navigation/
```

Logical flow:

```text
Compose UI
   ↓
ViewModel
   ↓
Use Case / Domain Logic
   ↓
Repository
   ↓
Room / DataStore / File Storage
```

Do not force every trivial setting through unnecessary layers. Keep architecture pragmatic.

---

# 5. Core Data Model

Exact implementation may evolve, but preserve these concepts.

---

## 5.1 Mood

```text
Mood
-----
id: Long
name: String
score: Double
color: Long/String
icon: String
sortOrder: Int
isArchived: Boolean
createdAt: Instant
updatedAt: Instant
```

Default install may contain:

```text
5.0 Amazing
4.0 Good
3.0 Meh
2.0 Bad
1.0 Awful
```

Requirements:

- user can rename moods,
- user can reorder moods,
- user can choose color,
- user can choose an icon/emoji,
- archived moods remain valid for old entries.

---

## 5.2 ActivityGroup

```text
ActivityGroup
-------------
id: Long
name: String
sortOrder: Int
isArchived: Boolean
createdAt: Instant
updatedAt: Instant
```

Possible defaults:

- Social
- Exercise
- Sleep
- Entertainment
- Study
- Food
- Health
- Work

---

## 5.3 Activity

```text
Activity
--------
id: Long
groupId: Long?
name: String
icon: String
color: Long/String?
sortOrder: Int
isArchived: Boolean
createdAt: Instant
updatedAt: Instant
```

Requirements:

- create,
- edit,
- reorder,
- regroup,
- archive,
- restore/archive handling.

Deleting historical activities should normally archive rather than break old entries.

---

## 5.4 Entry

```text
Entry
-----
id: Long
timestamp: Instant
moodId: Long
note: String
createdAt: Instant
updatedAt: Instant
```

Important:

- many entries may exist on the same day,
- timestamp must include time,
- editing timestamp must be supported,
- time-zone-sensitive UI must be handled carefully.

---

## 5.5 EntryActivity

```text
EntryActivity
-------------
entryId: Long
activityId: Long
```

Composite primary key:

```text
(entryId, activityId)
```

---

## 5.6 EntryPhoto

```text
EntryPhoto
----------
id: Long
entryId: Long
localPath: String
sortOrder: Int
createdAt: Instant
```

Recommended behavior:

1. user selects photo through Photo Picker / SAF,
2. application copies the selected media into app-private storage,
3. database stores the managed local path,
4. deleting an Entry cleans related private media safely,
5. backup includes managed media.

Avoid depending permanently on a transient external URI.

---

## 5.7 Goal

```text
Goal
----
id: Long
name: String
linkedActivityId: Long?
goalType: Enum
targetCount: Int
periodType: Enum
startDate: LocalDate
endDate: LocalDate?
isArchived: Boolean
createdAt: Instant
updatedAt: Instant
```

Supported examples:

- every day,
- selected weekdays,
- N times per week.

---

## 5.8 GoalSchedule

```text
GoalSchedule
------------
goalId: Long
dayOfWeek: Int
```

---

## 5.9 GoalCompletion

```text
GoalCompletion
--------------
id: Long
goalId: Long
date: LocalDate
completedAt: Instant
source: Enum
```

Possible source:

- MANUAL
- LINKED_ACTIVITY

Avoid duplicate completion for the same logical goal/date unless the goal definition explicitly permits multiple completions.

---

## 5.10 Reminder

```text
Reminder
--------
id: Long
type: Enum
targetId: Long?
hour: Int
minute: Int
daysOfWeek: Set<Int>
message: String
enabled: Boolean
createdAt: Instant
updatedAt: Instant
```

---

## 5.11 NoteTemplate

```text
NoteTemplate
------------
id: Long
name: String
content: String
sortOrder: Int
isArchived: Boolean
```

---

## 5.12 ImportantDay

```text
ImportantDay
------------
id: Long
date: LocalDate
title: String
icon: String
note: String?
```

---

# 6. Primary Navigation

Recommended bottom navigation:

```text
Entries | Calendar | + | Statistics | More
```

The central `+` button opens the Entry Editor.

Potential More screen:

```text
Goals
Moods
Activities
Templates
Reminders
Backup & Restore
Export
Appearance
Privacy
Settings
About
```

Navigation should use stable routes and support process recreation.

---

# 7. Functional Scope

Priority meaning:

- P0: essential
- P1: near-complete product
- P2: polish / advanced parity

---

## 7.1 Entries

### P0

- create entry,
- select mood,
- select multiple activities,
- note text,
- choose date/time,
- multiple entries per day,
- edit entry,
- delete entry,
- entry timeline,
- grouped by date.

### P1

- photos,
- multiple photos,
- search,
- filters,
- note templates.

---

## 7.2 Mood system

### P0

- default moods,
- customize names,
- customize score,
- archive moods.

### P1

- icon customization,
- color customization,
- reorder.

---

## 7.3 Activity system

### P0

- custom activities,
- activity groups,
- multi-select,
- reorder,
- archive.

### P1

- custom icon,
- optional custom color,
- activity management screen.

---

## 7.4 Calendar

### P0

- month view,
- mood indication per date,
- click date to view entries,
- navigate months,
- jump to today.

### P1

- multiple-entry day representation,
- configurable daily aggregation,
- marked important days.

Default aggregation recommendation:

- calculate average mood score for the local calendar day,
- map that value to a display color/gradient,
- preserve raw individual entries underneath.

---

## 7.5 Statistics

### P0

- mood trend,
- mood distribution,
- average mood,
- entry count,
- activity frequency,
- 7-day range,
- 30-day range,
- 3-month range,
- 1-year range,
- all-time range.

### P1

- activity vs mood association,
- activity vs next-day mood association,
- mood → common activities,
- weekday mood distribution,
- goal completion trends,
- streak metrics.

### P2

- confidence indicator,
- more rigorous statistical analysis,
- combination-pattern analysis.

Important wording rule:

Do not claim causation from observational diary data.

Prefer:

```text
"associated with"
"correlated with"
"appears alongside"
```

rather than:

```text
"causes"
"improves"
"makes your mood"
```

unless the UI clearly labels it as non-causal shorthand.

---

# 8. Statistics Specification

---

## 8.1 Mood trend

For a selected date range, aggregate entries by local day.

Possible daily aggregation:

```text
dailyMood = arithmetic mean of mood scores for entries on that local date
```

Present:

- line chart,
- rolling average optionally later,
- no misleading interpolation over very large missing periods.

---

## 8.2 Mood distribution

Count entry moods within selected range.

Return percentage by mood.

---

## 8.3 Activity frequency

For each activity:

```text
frequency = number of entries containing activity
```

Optionally provide:

```text
daysContainingActivity
```

as a separate metric.

Do not mix the two silently.

---

## 8.4 Activity and same-entry mood association

Baseline:

```text
meanMoodWithActivity
meanMoodWithoutActivity
difference = meanMoodWithActivity - meanMoodWithoutActivity
```

Show:

- sample count with activity,
- sample count without activity,
- mean difference.

Avoid presenting this as causation.

---

## 8.5 Activity and next-day mood

Suggested day-based implementation:

1. determine local dates containing a given activity,
2. find the following local date,
3. compute next-day average mood if entries exist,
4. compare against an appropriate baseline.

Document exact behavior in code/tests.

Edge cases:

- multiple activity entries on same date,
- no next-day entry,
- timezone change,
- user edits an old entry,
- first/last day of selected range.

---

## 8.6 Confidence indicator

Initial pragmatic rule:

```text
< 5 usable samples       → Insufficient
5–14                     → Low
15–29                    → Medium
30+                      → High
```

This is a product confidence heuristic, not a formal statistical confidence interval.

UI must not mislabel it as statistical certainty.

Later enhancement may introduce:

- variance,
- bootstrap confidence intervals,
- effect sizes,
- hypothesis testing,
- correction for repeated comparisons.

---

# 9. Goal System

Support:

## Daily goal

Example:

```text
Meditate every day
```

## Selected weekdays

Example:

```text
Gym on Monday, Wednesday, Friday
```

## Weekly count

Example:

```text
Run 3 times per week
```

Goal metrics:

- current streak,
- longest streak,
- completion percentage,
- weekly completion,
- recent history.

Linked activity behavior:

If a Goal is linked to an Activity and the relevant activity appears in a qualifying Entry, the app may automatically create a GoalCompletion.

This must be idempotent.

Editing/deleting an old entry must recalculate the affected automatic completion correctly.

---

# 10. Reminder System

Support:

- multiple reminders,
- selected weekdays,
- custom message,
- enable/disable,
- diary reminders,
- goal reminders.

Recommended implementation:

- WorkManager for resilient deferred/periodic work where exact-minute execution is not required,
- AlarmManager where exact user-selected timing behavior is truly required and platform rules permit it.

Respect Android notification permission requirements.

---

# 11. Search and Filters

Search should support:

- note text,
- activity names.

Filters:

- date range,
- mood,
- activity,
- has photo,
- optionally important day.

Combination filters should work together.

Example:

```text
Mood = Bad
Activity = Study
Range = Last 90 days
Has photo = No
```

---

# 12. Note Templates

Allow:

- create template,
- rename,
- edit,
- reorder,
- archive/delete,
- insert template into an entry note.

Example default template:

```text
Best thing today:

Hardest thing today:

What I learned:

Tomorrow:
```

Never overwrite typed user text without confirmation.

---

# 13. Appearance

## P0

- system theme,
- light theme,
- dark theme.

## P1

- preset color themes.

## P2

- custom accent color,
- typography preferences if useful.

Theme preferences persist in DataStore.

---

# 14. Privacy Lock

Support:

- PIN,
- biometric unlock when device supports it,
- configurable relock delay.

Possible delays:

- immediately,
- 1 minute,
- 5 minutes,
- 30 minutes.

Security notes:

- do not store plaintext PIN,
- use appropriate secure hashing/storage strategy,
- biometrics should rely on Android platform APIs,
- app lock does not replace full-device encryption.

---

# 15. Backup Format

Backup is a critical feature.

Recommended archive:

```text
moodiary-backup.zip
│
├── metadata.json
├── database.json
├── settings.json
└── photos/
    ├── ...
    └── ...
```

Example metadata:

```json
{
  "formatVersion": 1,
  "createdAt": "ISO-8601",
  "appVersion": "1.0.0",
  "platform": "android"
}
```

Requirements:

- versioned format,
- deterministic schema,
- validate before restore,
- reject unsupported future backup versions safely,
- never partially destroy current data because a restore failed,
- restore through a transaction/staging strategy where possible,
- verify photo references,
- handle duplicate IDs intentionally.

Recommended restore strategy:

1. inspect archive,
2. validate metadata,
3. validate JSON schema,
4. validate referenced media,
5. create temporary/staging representation,
6. only then replace/merge current state based on chosen restore policy.

Initial restore mode can be:

**Replace current local data after explicit confirmation.**

Merge restore may be deferred.

---

# 16. Cloud Backup

P1 target:

- Google Drive backup,
- manual upload,
- automatic periodic backup,
- restore from selected backup.

Important:

- cloud backup is optional,
- local app must remain fully functional without Google Drive,
- isolate cloud code behind an interface,
- do not make cloud services part of the Room repository contract.

Suggested abstraction:

```text
BackupProvider
├── LocalBackupProvider
└── GoogleDriveBackupProvider
```

---

# 17. Export

---

## 17.1 JSON

P0/P1.

Export complete structured data suitable for future import or analysis.

---

## 17.2 CSV

Suggested columns:

```text
EntryId
Date
Time
Timestamp
Mood
MoodScore
Activities
Note
PhotoCount
CreatedAt
UpdatedAt
```

Activity list should use a documented delimiter/escaping convention.

---

## 17.3 PDF

Allow range:

- 7 days,
- 30 days,
- current month,
- custom,
- all.

Possible report sections:

- summary,
- mood counts,
- activity counts,
- entry list,
- selected photos.

Generate a readable report, not a direct screenshot of app UI.

---

# 18. Important Days

Allow user to mark a date:

```text
Birthday
First day of university
Trip
Exam
Anniversary
Custom
```

Display markers in Calendar.

Possible future analytics:

- include/exclude important dates in some analyses.

---

# 19. Achievements

P2 only.

Examples:

- First Entry
- 7 Days Recorded
- 30 Days Recorded
- 100 Entries
- 365 Entries
- 7-Day Streak
- 30-Day Streak
- 100-Day Streak

Achievements must not interfere with actual journal data.

---

# 20. Recommended Release Milestones

---

## V0.1 — Core usable journal

Must contain:

- project architecture,
- Room database,
- default moods,
- activity groups,
- activities,
- create entry,
- edit entry,
- delete entry,
- multiple entries per day,
- notes,
- timeline,
- calendar,
- basic settings,
- dark mode.

Exit criteria:

A user can install the app and safely use it as a basic daily mood journal.

---

## V0.5 — Daily-use replacement

Add:

- photos,
- reminders,
- goals,
- streaks,
- basic statistics,
- search,
- filters,
- theme presets,
- activity/mood management.

Exit criteria:

The application is useful enough for normal everyday use without another mood-journal app.

---

## V0.8 — Near-complete premium replacement

Add:

- advanced activity/mood analytics,
- next-day analysis,
- confidence indicators,
- note templates,
- PIN,
- biometrics,
- local full backup,
- restore,
- JSON export,
- CSV export,
- PDF export,
- Google Drive backup.

Exit criteria:

The core premium-like functionality is complete and data ownership is strong.

---

## V1.0 — Mature release

Add/finalize:

- important days,
- achievements,
- polished animations,
- accessibility,
- internationalization foundation,
- performance tuning,
- comprehensive tests,
- migration audit,
- restore stress tests,
- UI consistency pass,
- crash/error-state pass.

---

# 21. Codex Task Plan

Use the following as the implementation sequence.

---

## Task 1 — Project bootstrap

Implement:

- Kotlin Android app,
- Compose,
- Material 3,
- Navigation Compose,
- Room,
- Hilt,
- DataStore,
- WorkManager,
- Coroutines/Flow,
- initial module/package structure.

Create base destinations:

- Entries,
- Calendar,
- Statistics,
- More,
- Entry Editor.

Acceptance:

- debug build succeeds,
- navigation works,
- no broken placeholder route.

---

## Task 2 — Core database

Implement entities and relationships:

- Mood,
- ActivityGroup,
- Activity,
- Entry,
- EntryActivity.

Implement:

- DAOs,
- database,
- repositories,
- basic Room tests,
- initial seed data.

Queries:

- insert,
- update,
- delete where appropriate,
- archive,
- observe,
- query by date,
- query by date range.

Acceptance:

- persistence survives restart,
- relations return correct data,
- tests verify CRUD and multi-activity entries.

---

## Task 3 — Entry Editor

Implement complete create/edit flow:

```text
Mood
→ Activities
→ Note
→ Date/time
→ Save
```

Acceptance:

- create,
- edit,
- delete,
- multiple entries same day,
- validation,
- proper back navigation,
- data retained correctly.

---

## Task 4 — Entry timeline

Implement:

- reverse chronological entries,
- date grouping,
- mood display,
- activity chips,
- notes,
- edit navigation,
- delete confirmation,
- empty state.

Acceptance:

- performant with at least 1,000 seeded entries.

---

## Task 5 — Calendar

Implement:

- month view,
- local-date aggregation,
- mood color/status,
- today action,
- month navigation,
- selected-day entry list.

Acceptance:

- correct across month/year boundaries,
- supports days with multiple entries.

---

## Task 6 — Mood and Activity management

Implement:

- add,
- edit,
- reorder,
- archive,
- group activities,
- preserve historical references.

Acceptance:

- historical entries do not break when items are archived.

---

## Task 7 — Basic statistics

Implement:

- mood trend,
- average mood,
- mood distribution,
- activity frequency,
- entry count,
- date ranges.

Acceptance:

- calculations have unit tests,
- empty data handled cleanly.

---

## Task 8 — Advanced activity/mood analytics

Implement:

- mean mood with activity,
- mean mood without activity,
- difference,
- mood → activity frequency,
- next-day activity association,
- weekday mood patterns.

Acceptance:

- algorithms covered by tests,
- UI uses non-causal wording,
- missing-day behavior documented.

---

## Task 9 — Confidence system

Implement heuristic confidence labels.

Minimum display:

```text
Sample count
Effect/difference
Confidence label
```

Acceptance:

- thresholds centralized,
- tested,
- UI explains confidence is based primarily on available sample size.

---

## Task 10 — Goal system

Implement:

- daily goals,
- weekday goals,
- N-times-per-week goals,
- linked activities,
- manual completion,
- automatic completion.

Acceptance:

- automatic completion is idempotent,
- entry edits/deletes correctly update linked goal state.

---

## Task 11 — Streaks and goal metrics

Implement:

- current streak,
- longest streak,
- completion rate,
- recent weekly completion.

Acceptance:

- date boundary and missing-day tests.

---

## Task 12 — Reminders

Implement:

- diary reminders,
- goal reminders,
- weekday selection,
- custom text,
- notification permission flow.

Acceptance:

- disabling reminder cancels future work,
- editing reminder replaces old schedule.

---

## Task 13 — Photo support

Add EntryPhoto and photo storage.

Implement:

- Photo Picker,
- multiple photos,
- preview,
- full-screen view,
- remove photo,
- app-private copy,
- cleanup orphan handling.

Acceptance:

- selected images remain available after source image relocation/removal where allowed by platform behavior because the app maintains its own private copy,
- deleting entry cleans owned media safely.

---

## Task 14 — Search and filters

Implement:

- note text search,
- activity search,
- mood filter,
- activity filter,
- date range,
- has photo.

Acceptance:

- filters compose correctly.

---

## Task 15 — Note templates

Implement CRUD + insertion into Entry Editor.

Acceptance:

- never destroys existing draft text without confirmation.

---

## Task 16 — Theme system

Implement:

- system,
- light,
- dark,
- preset themes,
- persisted preference.

Acceptance:

- no unreadable contrast in core screens.

---

## Task 17 — App lock

Implement:

- PIN setup,
- PIN verification,
- biometric unlock,
- relock delay.

Acceptance:

- no plaintext PIN,
- process restart behavior tested,
- app handles unavailable/removed biometric state.

---

## Task 18 — Local full backup

Implement versioned ZIP backup.

Must include:

- structured database export,
- settings,
- photos,
- metadata.

Acceptance:

- backup of seeded dataset succeeds,
- archive contents validated.

---

## Task 19 — Full restore

Implement validated restore.

Acceptance:

- corrupted backup rejected safely,
- unsupported version rejected clearly,
- successful restore recreates entries, activities, moods, goals, photos, settings,
- failed restore does not destroy existing data.

---

## Task 20 — Google Drive backup

Implement optional cloud provider abstraction and Drive integration.

Acceptance:

- app remains functional when not signed in,
- provider errors do not affect local data,
- backup can be manually triggered.

---

## Task 21 — Automatic backup

Implement configurable automatic backup scheduling.

Acceptance:

- retries safely,
- does not create uncontrolled duplicate uploads,
- clear last-success and last-error status.

---

## Task 22 — JSON and CSV export

Implement:

- full JSON export,
- analysis-friendly CSV export.

Acceptance:

- escaping and Unicode tested,
- exported data matches source entries.

---

## Task 23 — PDF export

Implement formatted report generation.

Acceptance:

- handles long notes,
- handles Unicode,
- handles optional photos,
- range selection works.

---

## Task 24 — Important days

Implement:

- CRUD,
- calendar markers.

---

## Task 25 — Achievements

Implement lightweight local achievement engine.

Do not couple achievements tightly to journal persistence.

---

## Task 26 — Settings consolidation

Create coherent Settings/More experience.

Sections:

```text
General
Appearance
Moods
Activities
Goals
Reminders
Templates
Backup & Restore
Export
Privacy
Data
About
```

---

## Task 27 — Data integrity audit

Audit and fix:

- transactions,
- cascading behavior,
- orphan EntryActivity rows,
- orphan media,
- stale goal completions,
- duplicate automatic completions,
- timezone handling,
- month/day boundaries,
- restore interruption,
- backup corruption,
- migration paths.

Add regression tests for confirmed issues.

---

## Task 28 — Unit/integration test expansion

Cover at minimum:

- mood aggregation,
- activity statistics,
- next-day association,
- goal logic,
- streak logic,
- local date conversion,
- search filters,
- backup serialization,
- restore validation.

---

## Task 29 — Compose UI tests

Cover critical user flows:

- create Entry,
- edit Entry,
- delete Entry,
- calendar navigation,
- goal completion,
- search,
- backup flow,
- restore confirmation.

---

## Task 30 — Performance pass

Generate large test data:

- 10 entries,
- 100 entries,
- 1,000 entries,
- 10,000 entries,
- 50,000 entries.

Measure/inspect:

- timeline,
- calendar,
- statistics,
- search,
- backup,
- restore.

Add indices based on actual query patterns.

Likely index candidates:

- Entry.timestamp,
- Entry.moodId,
- EntryActivity.activityId,
- GoalCompletion.date.

Do not add indices blindly without checking queries.

---

## Task 31 — Accessibility and UX pass

Check:

- content descriptions,
- touch target sizes,
- text scaling,
- contrast,
- TalkBack-friendly labels,
- meaningful error messages,
- confirmation dialogs,
- loading states,
- empty states.

---

## Task 32 — Final architecture/reliability audit

Perform a complete architecture and reliability audit.

Inspect:

- database design,
- Room migrations,
- transactions,
- concurrency,
- Flow collection,
- Compose recomposition,
- lifecycle handling,
- error handling,
- navigation state,
- background jobs,
- backup/restore safety,
- privacy,
- statistics correctness,
- large-dataset performance,
- test coverage.

Fix confirmed problems.

Then:

- run build,
- run unit tests,
- run applicable UI/instrumented tests,
- report any remaining known issues.

---

# 22. Database and Migration Rules

1. Every schema version change must increment the Room version.
2. Provide explicit migrations for production versions.
3. Do not use destructive migration in release builds for user-owned diary data.
4. Migration tests should be added once a production-like schema exists.
5. Archived records referenced by history should remain resolvable.
6. Use foreign keys intentionally.
7. Define cascade behavior deliberately rather than relying on defaults.
8. Entry deletion may cascade relation rows and owned EntryPhoto metadata, but file deletion must also be coordinated safely.
9. User customization must not rewrite historical Entry meaning unexpectedly.

---

# 23. Time and Date Rules

Time handling is a major source of bugs.

Store event timestamps as an absolute instant where appropriate.

Display and aggregate according to the user's current/local timezone policy.

For day-based objects such as GoalCompletion or ImportantDay, use LocalDate semantics.

Tests must include:

- year boundary,
- month boundary,
- leap day,
- DST transition where applicable,
- timezone change,
- entry edited into another day.

Document the chosen behavior when timezone changes cause an entry to appear on a different local day.

---

# 24. Data Safety Rules

Critical user content:

- entries,
- notes,
- moods,
- activities,
- goals,
- photos,
- settings,
- backup metadata.

Never perform destructive migration automatically.

Before destructive reset/import/restore:

- explain impact,
- require confirmation,
- recommend/export backup where practical.

Backup restore must be tested using real archive round-trips.

---

# 25. Original Design Requirement

The app may provide functionality comparable to established mood-journal applications, but must use an original product identity.

Do not copy:

- app name,
- logo,
- proprietary illustration sets,
- exact icon packs,
- exact wording,
- screenshots,
- pixel-identical screen layouts,
- branded achievements,
- proprietary onboarding copy.

Create a distinct design system.

Suggested personality:

- calm,
- minimal,
- fast,
- data-focused,
- low visual clutter.

---

# 26. Optional Future Differentiator: Insights

After V0.8/V1.0 stability, add an advanced Insights feature.

Examples:

```text
Exercise
Mood association: +0.64
Samples: 41
Confidence: High
```

```text
Late sleep
Next-day mood association: -0.71
Samples: 33
Confidence: High
```

```text
Study + Exercise
Observed average mood: 4.2
Comparison baseline: 3.5
```

Rules:

- never imply causality from observational data,
- expose sample size,
- avoid confident interpretation of tiny datasets,
- explain methodology in an About Statistics screen.

Possible future technical enhancements:

- bootstrap confidence intervals,
- effect size,
- robust regression,
- lagged analysis,
- user-defined variables,
- custom tags,
- correlations with sleep/health data if explicitly integrated later.

---

# 27. Out of Scope for Initial Release

Do not add these until the core product is stable:

- social feed,
- public profiles,
- ads,
- subscription billing,
- server account system,
- community features,
- arbitrary AI cloud processing,
- medical diagnosis,
- therapist portal,
- wearable sync,
- iOS version,
- web version.

They may be reconsidered later.

---

# 28. Suggested Codex Model Allocation

When model choice is available:

## Routine model

Use the normal strong coding model for:

- Compose screens,
- CRUD,
- ViewModels,
- simple Room DAO work,
- settings,
- theme implementation,
- search UI,
- ordinary refactors.

## Highest-reasoning model

Reserve the highest-reasoning model for:

- initial data architecture,
- difficult Room migrations,
- backup/restore design,
- statistics algorithms,
- goal/streak edge cases,
- concurrency bugs,
- cross-module refactors,
- difficult build failures,
- final architecture/reliability audit.

The project should not depend on using the most expensive model for every small UI change.

---

# 29. Completion Checklist

The app should not be called V1.0 until all applicable items below are satisfied.

## Entries

- [ ] Create
- [ ] Edit
- [ ] Delete
- [ ] Multiple per day
- [ ] Notes
- [ ] Activities
- [ ] Photos
- [ ] Timeline
- [ ] Search
- [ ] Filters

## Customization

- [ ] Custom moods
- [ ] Custom activities
- [ ] Groups
- [ ] Reordering
- [ ] Archiving
- [ ] Themes
- [ ] Note templates

## Calendar

- [ ] Month view
- [ ] Mood display
- [ ] Multi-entry dates
- [ ] Jump to today
- [ ] Important day markers

## Statistics

- [ ] Trend
- [ ] Distribution
- [ ] Average
- [ ] Activity frequency
- [ ] Activity association
- [ ] Next-day association
- [ ] Weekday analysis
- [ ] Confidence/sample count

## Goals

- [ ] Daily
- [ ] Selected weekday
- [ ] Weekly count
- [ ] Linked activity
- [ ] Manual completion
- [ ] Current streak
- [ ] Longest streak
- [ ] Completion rate

## Reminders

- [ ] Multiple reminders
- [ ] Weekday selection
- [ ] Goal reminders
- [ ] Notification permission handling

## Privacy

- [ ] PIN
- [ ] Biometric
- [ ] Relock timeout
- [ ] No plaintext secrets

## Backup

- [ ] Full local backup
- [ ] Version metadata
- [ ] Photos included
- [ ] Restore validation
- [ ] Safe failed restore
- [ ] Drive manual backup
- [ ] Automatic backup

## Export

- [ ] JSON
- [ ] CSV
- [ ] PDF

## Reliability

- [ ] Explicit Room migrations
- [ ] No destructive release migration
- [ ] Data integrity tests
- [ ] Backup round-trip tests
- [ ] Timezone/date tests
- [ ] Goal/streak tests
- [ ] Statistics tests
- [ ] Large dataset performance pass

## UX

- [ ] Empty states
- [ ] Error states
- [ ] Loading states
- [ ] Accessibility pass
- [ ] Dark mode
- [ ] Consistent navigation
- [ ] Destructive-action confirmation

---

# 30. First Instruction to Codex

When this document is first added to a new repository, use this instruction:

> Read `Moodiary_Codex_Master_Plan.md` completely. Treat it as the project specification and long-term source of truth. Start with Task 1 only. Inspect the repository first, then implement the project bootstrap and architecture required by Task 1. Do not implement later phases prematurely. Build the project, resolve compilation errors, and report exactly what you changed, what you tested, and what remains before Task 2.

After Task 1 succeeds, subsequent prompts may simply say:

> Continue with the next incomplete task in `Moodiary_Codex_Master_Plan.md`. Inspect the current repository state first, preserve completed functionality, implement only the next logical task and its direct dependencies, run relevant tests/builds, fix confirmed failures, and update progress.

---

# 31. Final Audit Prompt

When all planned features have been implemented:

> Read `Moodiary_Codex_Master_Plan.md` and perform the final V1.0 audit. Compare the implemented repository against every requirement and completion checklist item. Do not assume features work merely because classes exist. Verify architecture, data integrity, migrations, Room relationships, date/time handling, goal/streak logic, statistics, search, media lifecycle, notification scheduling, security, backup/restore, exports, Compose lifecycle behavior, accessibility, and large-dataset performance. Fix confirmed defects, add regression tests, run all practical builds/tests, and produce a final gap report listing anything still incomplete or risky.

---

# 32. Development Philosophy

The application is successful when it is:

1. fast enough to log every day,
2. trustworthy enough to store years of personal records,
3. private by default,
4. easy to back up and leave,
5. analytically useful without making false causal claims,
6. maintainable by one developer with Codex assistance.

The implementation should optimize for those six outcomes above all else.
