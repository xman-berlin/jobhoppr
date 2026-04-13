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
