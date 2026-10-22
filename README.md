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

## Accounts (sign-in, profiles, stats)

Players sign in with **Google** or **Play as guest** (a Supabase anonymous user). A guest can later press
**Link Google account** on the profile screen to keep their stats. Each user gets a `public.profiles` row from a
database trigger; stats are written only by the server (`record_ai_game()` for games against the computer, and the
edge functions for online games).

### One-time setup

1. **Database:** `supabase db push` (applies `20241002000000_profiles.sql`).
2. **Google Cloud Console** → APIs & Services:
   1. *OAuth consent screen*: External, add your email as a test user while testing.
   2. *Credentials → Create credentials → OAuth client ID → Web application*.
   3. Add this *Authorized redirect URI*: `https://<your-project-ref>.supabase.co/auth/v1/callback`.
   4. Copy the **Client ID** and **Client secret**.
3. **Supabase dashboard → Authentication → Sign In / Providers:**
   - **Google**: enable, paste the Client ID and secret.
   - **Anonymous sign-ins**: enabled (already needed for guests).
   - **Allow manual linking**: enabled (needed for *Link Google account*).
4. **Supabase dashboard → Authentication → URL Configuration → Redirect URLs:** add `quoridor://login-callback`.

No SHA-1 fingerprints or `google-services.json` are needed: Google sign-in runs in the browser and returns to the app
through the `quoridor://login-callback` link.

### Known limits

- `ai_wins` comes from the device (the computer game is played locally), so it can be inflated by a modified app.
  Treat it as a casual stat; online wins are recorded by the server.
- Signing out of a **guest** account loses its stats permanently (the guest has no password). The profile screen warns about this.

## Community stats (Phase 2)

- **Home screen:** "N online · X players joined" (counts up when it appears), refreshed every 30 s while visible.
- **Presence:** while the app is on screen it calls `touch_presence()` once a minute. Someone is *online* if seen in the last 2 minutes.
- **Counters:** `meta_stats` (one row, not readable by clients) holds `total_users` and `games_played`; clients read them only through `get_community_stats()`.
- **Leaderboard:** `get_leaderboard()` returns the top 10 by online wins (players without an online win are not listed).
- **Online results are recorded by the database**, exactly once, when a match first becomes `finished` (win, resignation or timeout): both players' games/wins/losses, the winner's `online_wins`, and the global counter. Only the edge functions can update `matches`, so a client cannot fake a result.

### Database tests

```sh
cd supabase/tests && npm ci && npm test
```

Runs every migration in an in-memory Postgres (PGlite) with Supabase-style default grants and checks counters, presence,
leaderboard ordering, once-only result recording and the access rules. CI runs it on every pull request.

## Release builds (APK)

Debug builds run from Android Studio. To build an installable, signed release APK:

1. Create a signing key **once** and keep it (and its passwords) safe and backed up. Apps signed with a different key
   cannot update an existing install. See `keystore.properties.template`. **Never commit the key or the properties file**
   (both are git-ignored).
2. Build:
   ```sh
   QUORIDOR_KEYSTORE_PROPERTIES=/path/to/keystore.properties ./gradlew assembleRelease
   # -> app/build/outputs/apk/release/app-release.apk
   ```
3. Check the signature: `apksigner verify --verbose --print-certs app-release.apk`.
4. Share it, e.g. as a GitHub Release asset. Testers allow "install unknown apps" for their browser or file manager once.

Without a keystore, `assembleRelease` produces an *unsigned* APK that Android will not install.

## Public pages

`docs/` contains the project homepage and the privacy policy (needed for Google sign-in in production). Publish them with
GitHub Pages: Settings → Pages → *Deploy from a branch* → `main` → `/docs`. Replace `CONTACT_EMAIL_HERE` in both HTML files first.
