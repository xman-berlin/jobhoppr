# Lessons Learned

Append-only. Never overwrite.

---

## 2026-04-13 — Spring Boot test-run procedure

### Problem
`mvn spring-boot:run` launched with the Bash tool times out at 120 s (the tool's limit), but the
JVM process keeps running in the background. A second launch attempt then fails immediately with
"Port 8080 was already in use." The fix (`lsof -ti :8080 | xargs kill -9`) kills the hidden
process, but if that second kill is done inside the same timed-out session the cycle repeats.

### Correct procedure for runtime verification

```bash
# 1. Always kill whatever is on 8080 before starting
lsof -ti :8080 | xargs kill -9 2>/dev/null; sleep 1

# 2. Start detached with setsid (NOT nohup) so the process survives shell exit
# nohup alone is NOT enough: the shell sends SIGTERM to the whole process group on exit.
# setsid creates a new session — the JVM is no longer in the shell's process group.
setsid mvn spring-boot:run > /tmp/jobhoppr-boot.log 2>&1 &
echo "PID=$!"

# 3. Poll in a separate Bash call (not chained with &&)
for i in $(seq 1 30); do
  sleep 2
  grep -q "Started JobhopprApplication" /tmp/jobhoppr-boot.log 2>/dev/null && echo "UP after ${i}x2s" && break
  grep -q "APPLICATION FAILED\|BUILD FAILURE" /tmp/jobhoppr-boot.log 2>/dev/null && echo "FAILED" && tail -30 /tmp/jobhoppr-boot.log && break
done

# 4. Hit the endpoint
curl -s -H "HX-Request: true" "http://localhost:8080/..."

# 5. After verification, kill the backend
lsof -ti :8080 | xargs kill -9 2>/dev/null
```

### Rules
- **Never** chain `mvn spring-boot:run` and curl in a single `&&` command — the tool may time out
  mid-run, orphaning the process.
- **Always** kill port 8080 before any new start attempt.
- **Always** use `setsid ... &` (not `nohup`) — `nohup` alone doesn't prevent SIGTERM on shell exit.
  `setsid` creates a new process session so the JVM is fully detached from the shell's process group.
- **Separate** the start command and the poll loop into two distinct Bash tool calls.

---

## 2026-04-14 — Flyway migrations must be created even for already-running DBs

### Problem
PostgreSQL functions (`match_arbeitszeit`, `match_arbeitszeit_details`) were created directly
via `docker exec psql` in an earlier session. On a fresh DB (after `docker compose down -v`) they
were missing → app started but matching returned 0 results or failed at runtime.

### Rule
- **Every** schema/function change must live in a Flyway migration file.
- Never create DB objects directly via `psql` — always add a `V<n>__<description>.sql` migration.
- If you discover an object that exists in the running DB but has no migration, create the migration
  immediately and verify it runs cleanly on a fresh DB.

---

## 2026-04-14 — NULL::TEXT[] placeholders in SQL queries cause silent bugs

### Problem
`MatchRepository` had `NULL::TEXT[] AS matching_az` as a placeholder in both CTE queries.
The outer SELECT referenced `s.matching_az` / `p.matching_az` but `bd.matching_az` was missing
from the `scores` CTE — so the column was always NULL even after adding the real function call.

### Rule
- SQL `NULL::TYPE AS column` is a valid placeholder but leaves a subtle trap: you must also
  propagate the column through every downstream CTE that references it.
- After replacing a NULL placeholder with a real expression, grep for the column alias in ALL
  downstream CTEs and the final SELECT to ensure full propagation.
- Write a quick smoke-test after: if the field is always empty/null in the UI, the propagation
  chain is broken somewhere.

---

## 2026-04-14 — Plan before implementing non-trivial tasks

### Problem
I jumped into implementing Phase 1 of the geo-hierarchy feature without first creating a detailed plan.
The task had multiple steps (new endpoints, new fragments, UI changes in two templates, JavaScript handlers).
AGENTS.md already specifies the workflow, but I skipped it.

### Rule
For ANY task that involves 3+ distinct implementation steps or architectural decisions:
1. Create a task file in `tasks/` (or update existing one)
2. Write the plan with specific implementation steps
3. Verify the plan before starting
4. Mark items as completed while working

This applies even when the plan already exists in a planning doc — the act of creating a task file ensures conscious thought about scope.
