# Quoridor

Android Quoridor (Kotlin, Jetpack Compose, Hilt) with an online two-player mode backed by Supabase.
Every online move is validated server-side by an edge function; clients only read state via Realtime.

## Layout

| Path | What |
|------|------|
| `app/` | Android app (`domain/` rules + AI, `data/` Supabase client, `ui/` Compose screens) |
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

Single-player (vs AI) needs no Supabase setup at all.

## Tests

```sh
./gradlew test                         # Kotlin rules + AI tests
deno test supabase/functions           # server rule engine tests
```

Both run in CI (`.github/workflows/ci.yml`).
