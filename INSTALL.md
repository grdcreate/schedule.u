# schedule.u won't install? — Android 15 / 16 fix

Short version: **the APK is not broken.** It is signed with two valid signature schemes
(v1 + v2 + v3), targets API 34 (Android 14) with minSdk 26, and has the correct package name —
it installs fine on Android 16. What blocks it almost always is the *phone's* install guard:
**Google Play Protect** or the **"install unknown apps"** permission. Nobody phones ships with
sideloading switched on by default.

Work down this list. One of them is your blocker.

---

## 1. "Unsafe app blocked" / "Blocked by Play Protect" / a Play Protect notification

This is the most common one for any app that isn't from the Play Store.

**Way A — on the popup:** tap **More details** (or the three-dot menu) → **Install anyway**.
There is no "Install" button until you expand it — that's why it looks like a hard block.

**Way B — switch the scanner off for 2 minutes:**
1. Open the **Play Store** app.
2. Tap your **profile picture** (top right) → **Play Protect** → **⚙ gear icon** (top right).
3. Turn **OFF** *"Scan apps with Play Protect"* (and *"Improve harmful app detection"* if present).
4. Install schedule.u now — it goes straight through.
5. Turn the scanner **back ON** afterwards.

Also kill the notification: long-press it → **Turn off notifications** for Play Protect, it will
not appear again for this app.

## 2. "You can't install apps from this source" / "not allowed to install unknown apps"

The app you're *installing from* needs permission — the APK's permission is not the issue.

Settings → **Apps** → **Special app access** (or the ⋮ menu) → **Install unknown apps** →
pick the app you opened the file with (**Files**, **Chrome**, **My Files**, **Telegram**…) → **Allow from this source**.

On Nothing OS the path is: *Settings → Apps → See all apps → ⋮ Special access → Install unknown apps*.

## 3. "For your security, your phone can't install apps from outside the Play Store" (Android 16)

Android 16's **Advanced Protection** shuts off sideloading entirely.

Settings → **Security & privacy** → **Advanced protection** → turn it **off** (or turn off only
"Block installing apps from outside Play Store") → install → turn it back on if you want it.

## 4. "App not installed" with no reason given

* Uninstall any older **schedule.u** first — if an old copy was signed with a different key, Android
  refuses the upgrade (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Just remove it and install fresh.
* Re-copy the file: the APK must be **~110 KB**. A half-downloaded APK is rejected as "invalid package".
* Don't install straight from **Google Drive / WhatsApp / Telegram previews** — they hand Android a
  temporary file that the installer can refuse. Open **Files → Downloads** and tap it *there*.

## 5. Tapping the APK does nothing / "Can't open file"

Some file managers refuse APK MIME types. Copy it to **Internal storage/Download** using the Files app,
then tap it in **Files** (not in the browser's download list). Still nothing → use the ADB route below.

## 6. Guaranteed route — install over USB with ADB

Works even when the on-device installer refuses (does not need unknown-apps permission):

```bash
# on your PC:  platform-tools installed, USB debugging ON in Developer options
adb devices
adb install -r scheduleu-2.5.apk
```
If it reports `INSTALL_FAILED_USER_RESTRICTED`, enable **Developer options → Install via USB**
(and turn *Verify apps over USB* off) on the phone, then run the command again.

---

## The install-blocking bug — fixed in 2.2.2 (still fixed here)

Your message was **"App not installed as package appears to be invalid"**. That is not Play Protect
and not a permission — it is Android refusing the APK *file itself*, because it could not find the
app's code inside it.

The build script had packed the compiled code into a **`dex/` sub-folder**
(`dex/classes.dex`, `dex/classes2.dex`, `dex/classes3.dex`) instead of the APK root, where Android
looks for `classes.dex`. An APK with no readable code at the root is rejected exactly this way —
on every version Android 8 through 16. It has now been repackaged so the code sits at the root,
and `build.sh` now **fails the build** if `classes.dex` is ever missing there again.

The new file is `scheduleu-2.5.apk` (`122914` bytes, SHA-256 `51030d25be4b33b226ecfbea08220e671ef35bd942e21d7982a682303892882c`):

* **size**: `122914` bytes (≈ 120 KB) — if the file you copy to the phone is smaller, the download was cut short
* **SHA-256**: `51030d25be4b33b226ecfbea08220e671ef35bd942e21d7982a682303892882c`
* versionCode **9** / versionName **2.5** — installs over anything older (same package, same key)
* signatures: **v1 + v2 + v3** all valid · zipalign OK · dex at root verified · 16/16 tests pass
* all 8 manifest components (launcher, engine service, alarm/boot receivers, Shizuku provider) verified
  present inside the packaged dex

Everything else — presets, engines, kill switch — is unchanged and 16/16 tests still pass.
If your phone still refuses after steps 1–6, tell me the **exact wording** of the message (a screenshot
is perfect) and I'll match it to the precise cause.
