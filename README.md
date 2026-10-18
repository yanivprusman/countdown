# countdown

Count down toward a goal you set — 100 days of work, 30 runs, 12 books.

The number you start from is kept and stays on screen; what is left is always derived
from it (start minus what you have counted), so "where I started" is never lost. One tap
counts one off. Several countdowns run side by side, each with its own history.

## How it behaves

- **One tap, with Undo.** The big button inside a countdown and the small `−1` on its
  card do the same thing: count one, buzz, and offer Undo. Only one Undo stands at a
  time — the newest replaces the one on screen.
- **Once a day (default on).** A second count on the same calendar day asks first, so a
  double tap cannot cost you a day. Turn it off for goals counted several times a day.
- **The Undo bar sits above the screen's bottom button, never over it.** Each screen
  reports its bottom action's measured height (`reportsBottomAction`) and the bar is
  placed above it. In tally the bar covered the FAB (issue #8); here it covered the
  count button and swallowed the second tap.
- **History** is grouped by day, numbered from the first tick ("Day 1"), each row with
  what was left after it. Tap a row for a note; swipe it left to remove it.
- **Opens back inside the countdown you were in** — you come back daily to tap once.
- Edit, Duplicate (a what-if fork with every tick), Start over (clear the ticks) and
  Delete, the last two confirmed and undoable.

## Where the records live

Everything tally learned, applied from day one:

- **In the `countdown` MySQL database (system MySQL, 3306), not on the phone.** The
  device holds a view. Off the VPN the app says so and refuses, rather than pretending
  to save; there is no local write queue, on purpose.
- **A delete is a real `DELETE`.** Undo works because the app holds what it removed in
  memory while the Undo bar is up and writes back only the rows that disappeared.
- **Writes go out one at a time, in order** — a count and its undo are two requests,
  and an undo that overtook its count would leave the tick alive.
- **The client owns the clock.** Every write carries an explicit UTC time; nothing
  relies on `CURRENT_TIMESTAMP` (MySQL writes it in the server's zone). "Today" and the
  streak are worked out in the phone's own zone.
- **Which countdown was open** is the one thing kept on the phone — it is where this
  phone was, not a record.

## Layout

| Path | What it is |
| :--- | :--- |
| `db/schema.sql` | Two tables, re-runnable: `sudo mysql countdown < db/schema.sql`. |
| `lib/countdowns.ts` | Every query. |
| `lib/auth.ts` | The bearer-token guard. A missing token refuses everything. |
| `app/api/` | The REST surface the phone talks to. |
| `mobile/shared/src/commonMain/` | Every screen, `data/CountdownApi.kt`, `data/CountdownStore.kt`. Compose Multiplatform. |
| `mobile/app/` | The Android host: base URL, token, back button, launcher icon. |

## Config (both gitignored)

- `.env.local`: `COUNTDOWN_API_TOKEN`, `DB_PASSWORD`.
- `mobile/.env`: `API_BASE_URL` (`http://10.7.0.2:3179/`), `API_TOKEN` (same token).

## Build and install

```bash
cd mobile && ./gradlew :shared:testDebugUnitTest   # model + navigator tests
./gradlew assembleDevDebug                          # always the dev flavor
androidDeploy countdown                             # chunked install over WireGuard
```

Web side: `d startApp --app countdown` (dev 3179, prod 3178). Android package
`com.automatelinux.countdown.dev`, launcher label **Countdown**.
