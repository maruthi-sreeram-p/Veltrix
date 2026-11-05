# CareerFlux — deployment smoke test

Run after every deployment, before telling anyone it is ready.

```bash
BASE_URL=https://your-pilot-origin ./scripts/smoke-test.sh
```

That script covers the checks a machine can make honestly. The rest need a
person, and are marked **manual** — not because automating them is hard, but
because a script clicking through a match list would report success without
anybody having looked at whether the screen made sense. A deployment is not
verified until somebody has signed in.

Add `SMOKE_EMAIL` and `SMOKE_PASSWORD` to include an authenticated round trip.
Use an administrator account, not a user's.

---

## Automated — `scripts/smoke-test.sh`

| # | Check | What a failure means |
|---|---|---|
| 1 | Frontend responds and serves the SPA shell | nginx is down, or the build did not land in the image |
| 2 | `/healthz` responds | Container healthcheck will flap |
| 3 | Deep links (`/app`, `/app/discover`, `/app/profile`) return the shell | `try_files` is missing — every browser refresh 404s |
| 4 | `/api/auth/me` returns 401 | 404 = nginx is not proxying; 502 = backend down; 401 = the whole path works |
| 5 | Unknown API path returns a clean 404 | Error handling is leaking or the proxy is misrouted |
| 6 | Actuator, Swagger, `/v3/api-docs`, H2 console unreachable from the public origin | Developer tooling is exposed |
| 7 | `.map` files refused | Frontend source is downloadable |
| 8 | Security headers present, no version in `Server` | Header inheritance was dropped in an nginx block |
| 9 | Backend actuator health `UP`, no component detail unauthenticated | Database unreachable, or health is describing the infrastructure |
| 10 | Active profiles include `prod` and exclude `dev`, `demo` and `test` | The pilot is not running as a production deployment, or is running development configuration |
| 11 | Seeding and scheduler flags are `false` | Sample jobs may appear in a real corpus |
| 12 | Sign-in is rate limited | The limiter is not wired |
| 13 | Optional authenticated round trip | Sign-in or JWT handling is broken |
| 14 | Schema at V12, no failed migrations | Flyway did not complete |

The rate-limit check deliberately probes an address at `example.invalid`, so
smoke-testing a deployment can never lock a real user out of their account.

---

## Manual

Sign in as each role (user, administrator) and confirm the screen is right. Roughly ten minutes.

### As a user

- [ ] **Register** a new account. You land on resume upload, not an empty dashboard.
- [ ] **Sign in** — the dashboard loads and greets the right person.
- [ ] **Profile** — edit and save; reload and confirm it persisted.
- [ ] **Resume upload** — upload a PDF. It appears in the list. Download it back
      and confirm it is the same file.
- [ ] **Resume persistence** — restart the stack (`stop` then `up -d`, *not*
      `down -v`) and confirm the resume is still there and still downloads. This
      is the check that catches resume storage having been mounted somewhere
      ephemeral, which is silent until it is not.
- [ ] **Job discovery** — matches are listed with scores that look sane.
- [ ] **Apply link** — opens the employer's real posting in a new tab, and the
      URL is the employer's, not CareerFlux's.
- [ ] **AI fallback** — with no `GEMINI_API_KEY` set, resume parsing and match
      narratives still work and the UI says which path it used. Absence of a key
      is a supported mode; if anything errors, that is a bug.
- [ ] **Your data** — the profile's Privacy tab downloads a JSON copy of your data.
- [ ] **Isolation** — a user cannot open another user's profile or resume; changing
      an id in the URL returns 404.

### As an administrator

- [ ] **Sign in** and reach the platform console.
- [ ] **Sources** are listed. **Do not enable the scheduler** to test this.

### Operations

- [ ] **Kafka event flow** — the consumer group is `Stable` with lag at or near
      zero (command in `docs/RUNBOOK.md`).
- [ ] **Backup** — run `scripts/backup.sh` once by hand. Confirm
      `last-success.txt` appears with a current timestamp, a plausible byte
      count, and the retention counts you expect.
- [ ] **Restore** — restore into a scratch database and compare row counts. Do
      this at least once before real users depend on the system, and once a
      term after. A backup nobody has restored is a guess.
- [ ] **Restart** — `stop` then `up -d`. Everything returns, resumes intact,
      database intact, no migration re-runs.
- [ ] **Graceful shutdown** — the backend log shows shutdown, not a kill. No
      request is cut mid-response.
- [ ] **Logs contain no secrets** — grep the backend log for the JWT secret, the
      database password and the Gemini key. All three must be absent.

---

## After the first week

- [ ] `last-success.txt` timestamp is under seven hours old. If it is not, the
      schedule was never installed or has been failing silently — which is the
      single most likely way this deployment quietly stops being recoverable.
