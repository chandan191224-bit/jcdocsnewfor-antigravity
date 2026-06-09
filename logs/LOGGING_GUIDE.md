# Logging Guide — JCdocs

## Purpose
Log files preserve my understanding of the project across sessions.
Always update these files after every change.

## Files

### `PROJECT_UNDERSTANDING.md`
High-level overview of architecture, tech stack, key files, and schema.
Update when adding/removing major features, changing architecture, or modifying schema.

### `CHANGELOG.md`
Sequential record of all sessions and changes.
Each session = one entry with summary, files added, files modified.

### `SESSION_LOG_<YYYY-MM-DD>.md`
Detailed log for each session. One file per day.

## Workflow

### Every change MUST include:
1. Update `CHANGELOG.md` — add entry for this session
2. Update `SESSION_LOG_<date>.md` — document what was done
3. Update `PROJECT_UNDERSTANDING.md` if architecture/schema/tooling changed
4. Commit logs together with code changes

### Never
- Delete old log entries
- Modify past session logs (create new session instead)
