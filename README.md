# ScreenBuddy

A pixel character that floats over your apps in a Deltarune-style dialog box and comments on your screen via OpenRouter.

## Build
1. Create a GitHub repo, push this folder.
2. Open the **Actions** tab. The APK is built on every push.
3. Download it from **Releases > Latest APK** (or the run's **Artifacts**).

## Use
1. Install the APK, open it, paste your OpenRouter key, pick a character, press START.
2. Allow "Display over other apps", press START again, accept screen capture.
3. Drag the character to move it; its position is saved. Tap it = look now. Tap the dialog = hide. Long-press = quit.
4. Tap **Ⅱ QUIET** to pause automatic comments while you work or type; tap **▶ RESUME** to turn them back on. The character also tries to stay silent when it detects the on-screen keyboard or text composition.

## Add a character
Copy a block in `Characters.kt`: 16x16 grid, palette, personality prompt, voice pitch.
Pixel font: put `pixel.ttf` in `app/src/main/assets/` (otherwise the workflow downloads VT323).
