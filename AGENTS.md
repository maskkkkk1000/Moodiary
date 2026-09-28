# Moodiary development rules

Read `Moodiary_Codex_Master_Plan.md` as the product specification. The user's later instructions take priority. Communicate progress in Chinese.

## Version control

- Inspect repository root, branch, remotes, status, history and sensitive/unrelated files before Git/GitHub operations. Preserve all existing history.
- Follow `docs/VERSION_CONTROL.md`, `docs/ROADMAP.md` and `docs/RELEASE_CHECKLIST.md`.
- Current app version is **0.1.0**, versionCode **1**; Room schema is independently **1**. Never call this V1.0 simply because many features exist.
- Commit each coherent, independently verified phase using Conventional Commits. Do not invent historical commits, combine unrelated phases, or mechanically split tiny changes.
- Before every commit inspect the full diff and staged file list, scan for sensitive information, confirm the app builds and run tests relevant to core changes. Never commit failed or unverified production changes to main.
- Use `main` for verified stable states, `feature/*` for features, `fix/*` for fixes, `release/*` for release preparation. Large work must use a separate branch and be verified before merging.
- Never force-push main, delete history, or arbitrarily rebase published history. Never commit real journals/photos/backups, secrets, tokens, plaintext PINs or signing keys. Synthetic test inputs are not user data.
- Annotated version tags must point to verified commits. Do not move published tags.
- Before the first GitHub push report status, file scope, ignored files, secret-scan results, commit/tag, remote and branch. If repository URL or authorization is missing, stop at that step and ask. Never guess an account or repository.
- Keep README, CHANGELOG and ROADMAP current. Use the full release checklist before version releases.

## Data integrity

- Every change to a released Room schema requires a new schema version, explicit Migration, retained historical schema JSON and real upgrade tests.
- Never use destructive migration fallback for user data. Test backup/restore round-trips and compatibility when persistence changes.
- App SemVer, Android versionCode, Room schema and backup format are separate version axes.
- Preserve existing functionality. Validate behavior, not just file/interface presence. Record exact external blockers and unverified behavior.
