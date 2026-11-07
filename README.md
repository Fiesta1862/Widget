# ScreenBuddy

A pixel character that floats over your apps in a Deltarune-style dialog box and comments on your screen via OpenRouter.

## Build
1. Create a GitHub repo, push this folder.
2. Open the **Actions** tab. The APK is built on every push.
3. Download it from **Releases > Latest APK** (or the run's **Artifacts**).

## Use
1. Install the APK, open it, paste your OpenRouter key, pick a character, press START.
2. Allow "Display over other apps", press START again, accept screen capture.
3. Tap the character = look now. Tap the box = hide. Long-press = quit.

## Add a character
Copy a block in `Characters.kt`: 16x16 grid, palette, personality prompt, voice pitch.
Pixel font: put `pixel.ttf` in `app/src/main/assets/` (otherwise the workflow downloads VT323).
