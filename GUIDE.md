# schedule.u — how to use it

**Install:** `scheduleu-2.5.apk` (in this workspace) → tap it in **Files ▸ Downloads** → if Android
says *install anyway / unknown source*, allow it once.

---

## 1. First three taps (do these once)

- **Allow notifications** when the app asks — this is how the engine tells you what it did.
- The app opens **"Allow the kill switch"** on first launch → tap **Allow and arm**. That single
  VPN permission is what makes *internet off* work with no root. Granting it here does **not** cut
  your internet immediately — it only allows internet steps to run at their time. (Tapping the
  *Internet kill switch* row or the quick-settings tile is what blocks right now.)
- On the home screen, if a card lists missing pieces, tap it and fix the top one — usually
  **Battery: unrestricted** (stops the ROM killing your alarms).

## 2. First launch = already done

- **Sleep mode — 11:30 PM daily** → Internet off (kill switch) + Do Not Disturb on
- **Good morning — 7:00 AM daily** → Internet on + Do Not Disturb off + Ringer normal
- Both are normal schedules: edit, switch off or delete them like any other.

## 3. The home screen, line by line

- **Big time + *tonight*** — the next schedule that will run, with a live **"in 7h 12m"** countdown.
- **LAST RUN …** — what really happened last time, in plain words. Tap it for the full history.
- **Schedules armed** switch — master on/off. If this is off, *nothing* fires.
- **Internet kill switch** switch — turn all internet off/on by hand right now.
- **Schedules list** — every schedule with its next run. Toggle it on the row.
  **Tap** = edit · **long-press = Run now / Delete**.
- **★ +** button (bottom right) — new schedule.
- **Top-right icons** — history, and Setup (permissions, engine, root/Shizuku probe,
  **Self-test**).

## 4. Making a schedule (4 fields, any number of steps)

- **Name** — anything, e.g. *"Bedtime"*.
- **The stack** — tap action chips to add steps: Wifi off, Data off, DND on, Flashlight off,
  Brightness 15%… Tapping one again removes it. Tap a step to set its level (volume/brightness),
  **long-press a step to move it up**, ✕ removes it. Order matters — steps run top to bottom.
- **Time** — tap the big clock to open the picker. Use the **AM / PM buttons** to jump between
  morning and evening (the clock face stays put). **24H** switches the whole app to 24-hour.
  If the time you picked has already passed today, the app says so right under it.
- **Repeat** — Once · Daily · Weekdays · Weekends · Custom (pick days as circles).
- **Method** — leave it on **Auto** and the app picks the best engine per step.
- **SAVE SCHEDULE** — the toast tells you the next run time. Nothing else to arm.

## 5. Check it works *before* the time comes

- **Long-press the schedule → Run now** — runs the whole stack instantly and shows you the result
  in the toast, the history and the LAST RUN line.
- Or inside the builder: **TEST NOW** (bottom left) before saving.

## 6. What works with what

- **No root, no Shizuku** → everything except radios: **kill switch (all internet off/on)**,
  Do Not Disturb, ringer, volume, brightness, auto-rotate, flashlight. For Wi-Fi/data/airplane/
  Bluetooth the app opens the right settings screen and tells you why.
- **+ Shizuku** (Shizuku app + wireless debugging) → same real switching as root, no root needed.
- **+ Root** → Wi-Fi, mobile data, airplane, Bluetooth, hotspot, location, NFC and shell commands,
  all fully automatic.
- The app always says which engine it used — look at the run line: *"(root)"*, *"(shizuku)"*,
  or *"kill switch on"*.

## 7. "The time passed and nothing happened" — checklist

1. **Is the engine armed?** The top switch must say *armed • schedules are live*.
2. **Did the time already pass today?** A 2:47 PM schedule saved at 3:10 PM runs *tomorrow*. The
   builder warns you, and the toast shows the next run time.
3. **Open the app** — it re-arms every alarm and, if a run was missed in the last 30 minutes, runs it
   straight away ("Running … late — the alarm was missed").
4. **Read LAST RUN / History** — every attempt is now logged, including *"skipped (engine paused)"*
   and *"needs one tap: allow the kill switch"*. No more silent no-ops.
5. **Fix the readiness card** (home screen) — notifications, Do Not Disturb access, Modify system
   settings, Internet control, Battery: unrestricted. Each one is a silent killer if missing.
6. **Battery → Unrestricted** for schedule.u, and keep the persistent *schedule.u · armed*
   notification (that is the engine running — swiping it away is fine, but force-stopping the app
   cancels its alarms until you open it again).
7. Anything that needs a *screen* (Wi-Fi/data/airplane without root) needs the screen unlocked,
   because Android forbids apps from opening settings behind your lock.

## 7b. The self-test (Setup ▸ Diagnostics ▸ Self-test)

One tap and it tells you the truth about this phone, then **blinks the flashlight once** as proof
that actions really run. Each line is ✓ or ✕:

- Notifications · Alarms & reminders · Battery unrestricted · Do Not Disturb access ·
  Modify system settings · Internet control (kill switch) · Display over other apps · Root · Shizuku
- then: *"3 schedules armed • next run 11:30 PM"*, or a warning if the master switch is off.

If a line is ✕, that is exactly what to fix in the same screen — no guessing.

## 8. What is new in 2.5 (and 2.4)

- **Fixed the big one:** schedules *you* created never fired. Their database id was dropped when the
  row was saved, so the alarm fired into an empty lookup and gave up silently. Presets worked, your
  own schedules did not — that is the bug you hit. Now: the id is kept, alarms are re-armed every time
  the app opens, and a missed run is caught up within 30 minutes.
- **No silent failures:** a run that is skipped, unknown or failed is written to History and to the
  LAST RUN line with the reason.
- **Wake lock + layered alarm arming** (exact → alarm-clock → inexact) so a run is never dropped.
- **Readiness card** on the home screen lists anything missing, with a one-tap fix.
- **Kill-switch consent** is now asked for automatically the first time a schedule needs it.
- **UI/UX**: staggered row entrances, hero time cross-fade, spring-back chips, FAB press bounce,
  ripple feedback on every row/button, and slide-fade transitions between screens.

- **2.5 fixes:** granting the kill-switch permission no longer turns your internet off on the spot;
  ringer steps now say *"grant Do Not Disturb access"* instead of failing with a bare error (Android
  blocks ringer changes without it); alarms are re-armed when you change the clock or timezone; and
  skipped/unknown runs are now marked amber in History instead of looking like clean runs.
- **Self-test** added in Setup, described above.

## 9. Small things worth knowing

- Times read as **11:30 PM** by default; the **24H** button (or *Setup ▸ Display ▸ Time format*)
  switches everything, including notifications and history, to 24-hour.
- **Disable a schedule** with its row switch — it keeps its settings and stays in the list.
- **History** keeps the last 40 runs with a dot per run: rose = clean, amber = something needed
  your attention.
- The app never uploads anything; everything runs on the phone.
