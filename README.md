# Quoridor

Android Quoridor (Kotlin, Jetpack Compose, Hilt) with an online two-player mode backed by Supabase.
Every online move is validated server-side by an edge function; clients only read state via Realtime.

## Layout

| Path | What |
|------|------|
| `app/` | Android app (`domain/rules` + `domain/ai`, `data/` Supabase client, `ui/` Compose screens) |
| `supabase/migrations/` | Postgres schema + RLS |
| `supabase/functions/` | Edge functions: `create-room`, `join-room`, `get-room`, `submit-action`, `cleanup-rooms` |
| `supabase/functions/_shared/quoridor.ts` | Server-side rule engine (must stay in sync with `QuoridorRules.kt`) |

## Setup

1. Install Android Studio (JDK 17) and the Android SDK 34.
2. Create a Supabase project. In **Authentication → Providers**, enable **Anonymous sign-ins**.
3. `cp local.properties.template local.properties` and fill in `sdk.dir`, `SUPABASE_URL`, `SUPABASE_ANON_KEY`.
4. Deploy the backend:
   ```sh
   supabase link --project-ref <ref>
   supabase db push
   supabase functions deploy
   ```
5. Run the app: `./gradlew installDebug` (or press Run in Android Studio).

Single-player (Easy / Medium / Hard computer opponent) runs fully on-device, but the app still expects the Supabase values in `local.properties` to build.

## Tests

```sh
./gradlew test                         # Kotlin rules + AI tests
deno test supabase/functions           # server rule engine tests
```

Both run in CI (`.github/workflows/ci.yml`).

## Game rules enforced by the server

- Every action is validated in `submit-action`; clients cannot write to `matches`.
- A player may **resign** at any time.
- A player idle for 2 minutes (`TURN_TIMEOUT_MS`) can be forfeited by the opponent via `claim-timeout`.
- `cleanup-rooms` deletes rooms older than 24h. Set a secret and schedule it (e.g. hourly with pg_cron / a cron caller):
  ```sh
  supabase secrets set CLEANUP_SECRET=<random>
  curl -X POST https://<ref>.supabase.co/functions/v1/cleanup-rooms -H "x-cleanup-secret: <random>"
  ```
