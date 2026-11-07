# ScreenBuddy

ScreenBuddy is an Android pixel companion that floats above other apps and can react to the visible screen using a vision-capable model through OpenRouter.

## ScreenBuddy Studio

The redesigned app home screen groups setup into four sections:

- **Character:** choose a built-in buddy or import a custom image, preview it, and set a custom name.
- **Personality:** provide custom behavior and speaking-style instructions.
- **AI connection:** configure your OpenRouter API key and vision model.
- **Behavior:** set the automatic reaction interval and choose touch-through or draggable mode.

The app uses native Android views and has no additional UI library dependencies.

## Build

1. Open the **Actions** tab.
2. Wait for **Build APK** to finish successfully.
3. Download `ScreenBuddy-debug-apk` from the successful run's **Artifacts** section.

## Use

1. Open ScreenBuddy and configure your OpenRouter API key, character, and model.
2. Allow **Display over other apps** when Android asks.
3. Accept Android's screen-capture prompt. ScreenBuddy then starts as a foreground service.
4. Use **Touch-through mode** when taps should pass to the app underneath. Turn it off when you want to drag the character around the screen. The position is saved.
5. Set the automatic reaction interval in seconds. Use `0` to disable automatic reactions.

## Custom characters

1. Select **Custom image** in the Character section.
2. Tap **Import image** and choose an image from your phone.
3. Enter a character name.
4. ScreenBuddy center-crops and converts the image locally into a 16×16 pixel sprite. The selected image URI is saved on the device so it can be reused.

## Custom personality

Enter instructions in the **Custom personality** field. For example: "You are a curious space explorer. Speak in short, funny observations and encourage me when I am learning." This personality is sent as part of the OpenRouter system prompt. Leave it blank to use the built-in character's personality.

## Privacy

The imported character image is converted locally and is not uploaded by the image-conversion feature. Screen captures are sent to OpenRouter when the buddy reacts. Avoid displaying passwords, private messages, or other sensitive information while screen reactions are enabled. Your OpenRouter API key is stored in the app's local preferences.
