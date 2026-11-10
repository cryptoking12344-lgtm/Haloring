# Halo Ring (test build)

Notification ring around the camera hole for the Galaxy S23 Ultra, shown when a
notification arrives while the screen is off.

## Build

Push to `main` (or run the "Build APK" workflow by hand). The APK is in the
workflow run under Artifacts > HaloRing-apk.

## Set up on the phone

1. Install the APK. Then open App info for Halo Ring > three dots (top right) >
   "Allow restricted settings". Sideloaded apps need this before steps 2 and 3
   can be switched on.
2. In the app, tap "Allow notification access" and switch Halo Ring on.
3. Tap "Turn on Halo Ring in Accessibility" > Installed apps > Halo Ring > on.
4. Samsung Settings > Lock screen and AOD > Always On Display > When to show >
   "For new notifications".
5. Tap "Preview ring" and use the sliders until the ring sits on the camera hole.
6. Tap "Send test notification in 8 seconds" and turn the screen off.

## What this build does

- Ring for every non-silent notification that arrives while the screen is off.
- Animates for 20 seconds, then holds a static ring until you wake the phone.
- Colour, speed, size, thickness and position controls.

Not in this build yet: per-app on/off and colours, more animation styles,
night hours, black-screen mode.
