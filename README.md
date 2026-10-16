# schedule.u

[![build](https://github.com/grdcreate/schedule.u/actions/workflows/build.yml/badge.svg)](https://github.com/grdcreate/schedule.u/actions/workflows/build.yml)
[![license: MIT OR Apache-2.0](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-FF2D6F.svg)](#license)

**A tiny Android scheduler that really switches your phone — at the times you choose, every day,
without you lifting a finger.** It does not remind you. It does the thing.

Built without Gradle: `aapt2` → `javac` → `d8` → `zipalign` → `apksigner`, in one `build.sh`.
Ships at **121 KB**, targets Android 14 (API 34), runs on Android 8.0+, no root required.

---

## Get the app

| where | what |
|---|---|
| **[`dist/scheduleu-2.5.apk`](dist/scheduleu-2.5.apk)** | the signed, installable build (121 KB, versionCode 9 / 2.5) |
| [`dist/SHA256SUMS`](dist/SHA256SUMS) | checksum of that exact file |
| [Releases](../../releases) | same APK attached, if you prefer downloading from a release page |
| [`docs/preview.html`](docs/preview.html) | what the four screens look like (open in a browser) |
| [`GUIDE.md`](GUIDE.md) | how to use it, step by step |
| [`INSTALL.md`](INSTALL.md) | if Android refuses the install (Play Protect, unknown sources) |
| [`docs/schedule.u-pitch-english.pdf`](docs/schedule.u-pitch-english.pdf) | the pitch: daily-life problems it solves |
| [`docs/schedule.u-guide-bengalish.pdf`](docs/schedule.u-guide-bengalish.pdf) | same story in Bengalish |

Install: copy the APK to the phone, open it from **Files ▸ Downloads**, allow "install unknown apps"
once, and if Play Protect objects tap **More details → Install anyway** (it is not from the Play Store,
that is all that warning means).

## What it does

Ready on first launch — two schedules already exist, so the phone starts managing itself with zero setup:

| time | name | steps |
|---|---|---|
| **11:30 PM** daily | Sleep mode | Internet off (kill switch) + Do Not Disturb on |
| **7:00 AM** daily | Good morning | Internet on + Do Not Disturb off + Ringer normal |

One tap on *Allow and arm* (the VPN consent) and the pair runs nightly and every morning on its own.

Everything else is a schedule you build from the same parts:

* **One schedule = many steps**, run in order: data off, Wi-Fi off, DND on, flashlight off,
  brightness 12%, volume 30%, airplane mode, hotspot off, any shell command.
* **Repeat rules**: once, daily, weekdays, weekends, or any set of days.
* **Engine per step**, chosen automatically: root → Shizuku → built-in kill switch → settings screen.

## Engines (honest table)

| engine | needs | switches for real |
|---|---|---|
| Direct | nothing | Internet on/off (kill switch), DND, ringer, volume, brightness, auto-rotate, flashlight |
| Shizuku | Shizuku app + wireless debugging | + Wi-Fi, mobile data, airplane, Bluetooth, hotspot, location, NFC |
| Root | rooted phone | everything above, plus shell commands |
| Screens (fallback) | nothing | opens the matching settings screen and says why |

The app always reports which engine it used — the run line says *"(root)"*, *"(Shizuku)"* or
*"kill switch on"* — and every step that could not run is written to History with the reason.

## UI

Editorial dark theme: pure-black canvas, 1 dp hairlines instead of cards, small-caps section labels,
a **rotated clock rail** beside a huge next-run display with a script-italic accent word, flat rose
accents, ripples and staggered row entrances. Times read as **11:30 PM** by default; a **24H** toggle
switches the whole app (and its notifications) to 24-hour.

## Reliability

* Exact alarms, with an alarm-clock fallback and an inexact fallback behind it, plus a wake lock per run.
* Re-armed on boot, on app update, on clock/timezone change, and every time the app is opened.
* A run missed in the last 30 minutes is executed late and says so, instead of vanishing.
* Nothing fails silently: skipped, unknown, blocked and "needs a permission" runs are all logged.

## Continuous integration

[`.github/workflows/build.yml`](.github/workflows/build.yml) runs on every push to `main`, on pull
requests, on version tags, and on demand (*Actions ▸ build ▸ Run workflow*):

1. JDK 17 + Android SDK platform 34 / build-tools 34.0.0
2. optionally restores a release keystore from the `KEYSTORE_JKS_BASE64` secret
3. `./build.sh` — aapt2 → javac → d8 → zipalign → apksigner
4. `./tests/run.sh` — the 32 logic tests on the real classes
5. verifies the package: badging, **v1/v2/v3 signatures**, `zipalign -c 4`, zip integrity, and that
   `classes.dex` / `classes2.dex` / `classes3.dex` sit at the APK root (the exact bug that once made
   Android reject the app as an invalid package)
6. uploads `scheduleu-CI.apk` + its SHA-256 as a build artifact
7. on a `v*` tag it also creates a **GitHub Release** with the APK attached

**Signing in CI.** Without a secret, `build.sh` mints a throwaway key for that run — fine for testing,
but each run's APK has a different signature, so it will not upgrade over an installed copy (uninstall
first). To make CI produce builds signed with the same key as `dist/`:

```bash
base64 -w0 keystore.jks        # copy the output
```
then add it as repository secret **`KEYSTORE_JKS_BASE64`** (Settings ▸ Secrets and variables ▸
Actions). The keystore's store/key password is the one `build.sh` expects (`controlscheduler`); the
file itself stays out of the repo either way.

## Build from source

```bash
git clone https://github.com/grdcreate/schedule.u.git
cd schedule.u
./fetch-toolchain.sh     # JDK 17 + Android SDK into .cache/toolchain (one time, ~600 MB)
./build.sh               # aapt2 -> javac -> d8 (3 dex) -> zipalign -> apksigner
./tests/run.sh           # 32 logic tests on the real classes (scheduling, AM/PM maths, catch-up, states)
```

Nothing else is required: `aapt2`, `javac`, `d8`, `zipalign` and `apksigner` all come from the
toolchain that `fetch-toolchain.sh` downloads into `.cache/toolchain/` (which is git-ignored).

> The release keystore (`keystore.jks`) is deliberately **not** in this repo. `build.sh` generates a
> fresh self-signed one on first run, so a local build is signed with a different key than the APK in
> `dist/` — install one or the other, not both.

## Repository layout

```
.github/workflows/      CI: build, test, verify, publish the APK (and release on v* tags)
AndroidManifest.xml     package com.controlscheduler.app, 12 permissions, target API 34
src/com/controlscheduler/app/
  MainActivity          home: next-run hero, countdown, readiness card, schedule list
  AddScheduleActivity   the builder: action stack, time (AM/PM or 24H), repeat, method, Test now
  SettingsActivity      permissions, engine, root/Shizuku probe, Self-test
  LogsActivity          the last 40 runs with per-step results
  EngineService         VpnService: the internet kill switch + the armed background engine
  Scheduler             alarm maths (next / previous occurrence, repeat rules, layered arming)
  AlarmReceiver         fires a schedule, hands long stacks to the service
  BootReceiver          re-arms everything after reboot, update, clock or timezone change
  Runner                executes steps: direct actions, kill switch, root/Shizuku, settings screens
  Shell                 root / Shizuku command execution + the radio command recipes
  Presets               the two first-launch schedules
  SelfTest              one-tap probe of every permission, blinks the flashlight as proof
  TimeFmt / Ui / Prefs / Db / Schedule / Step / Action / Schedule / QuickTileService / VpnConsentActivity
res/                    layouts, drawables, the editorial palette, AM/PM state lists
tests/                  Harness.java + run.sh - logic tests on the JVM
libs/                   Shizuku 13.0.0 jars (Apache-2.0, see libs/NOTICE.md)
dist/                   the signed APK + checksums
docs/                   screen preview + the two PDFs
```

## Changelog

* **2.5** — audit pass: consent no longer cuts internet instantly; ringer steps report the Do Not
  Disturb requirement; clock/timezone changes re-arm alarms; skipped/unknown runs show as "needs
  attention"; new **Self-test**.
* **2.4** — **fixed schedules created by hand never firing** (the row was saved without its database
  id, so the alarm hit nothing and gave up silently); self-healing alarms; 30-minute missed-run
  catch-up; readiness card; LAST RUN line + live countdown; animations and ripples.
* **2.3** — AM/PM buttons and a 24H toggle in the timer section; 12-hour times everywhere.
* **2.2.2** — fixed the packaging bug that made Android reject the APK as an invalid package
  (`classes.dex` was inside a sub-folder instead of the APK root).
* **2.2** — rename to schedule.u; editorial UI rebuild; Sleep mode + Good morning presets.
* **2.1** — kill-switch/foreground-service/OEM fixes found by auditing against the Android docs.

## License

Dual-licensed — use it under **either** license, at your option:

* **MIT** — see [LICENSE](LICENSE)
* **Apache-2.0** — see [LICENSE-APACHE](LICENSE-APACHE)

Apache-2.0 adds an explicit patent grant, which is why it is handy to have alongside MIT
(the `MIT OR Apache-2.0` arrangement used by many Rust projects). Contributions are accepted under
the same dual terms.

The Shizuku jars bundled in `libs/` are Apache-2.0 and keep that license
(see [`libs/NOTICE.md`](libs/NOTICE.md)).
