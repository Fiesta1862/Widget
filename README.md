# ScreenBuddy

A pixel character that floats over your apps and reacts to the visible screen using OpenRouter.

## Build
1. Open the **Actions** tab.
2. Wait for **Build APK** to finish.
3. Download `ScreenBuddy-debug-apk` from the successful run's **Artifacts** section.

## Use
1. Open ScreenBuddy, add your OpenRouter API key, choose a character, and press **START BUDDY**.
2. Allow **Display over other apps**, then accept Android's screen-capture prompt.
3. Drag the character around the full screen. Its position is saved. Tap the character to request an immediate reaction; tap the dialog to hide it. Long-press the character to stop the service.
4. Set the automatic reaction interval in seconds. Use `0` to disable automatic reactions and only react when tapped.

## Custom characters
1. Select **Custom image** in the Character list.
2. Tap **IMPORT IMAGE FROM PHONE** and choose an image.
3. Enter a character name, then start the buddy.
4. ScreenBuddy center-crops the selected image and converts it locally into a 16×16 pixel sprite. The selected image URI is saved on the device so it can be reused.

## Custom personality
Enter your own personality instructions in the **Custom personality** field. For example: "You are a curious space explorer. Speak in short, funny observations and encourage me when I am learning." This personality is sent as part of the OpenRouter system prompt. Leave it blank to use the built-in character's personality.

## Privacy
The imported character image is converted locally and is not uploaded by the image-conversion feature. Screen captures are sent to OpenRouter when the buddy reacts. Avoid displaying passwords, private messages, or other sensitive information while screen reactions are enabled.
