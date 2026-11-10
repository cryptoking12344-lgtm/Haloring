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
4. Tap "Preview ring" and use the sliders until the ring sits on the camera hole.
5. Tap "Send test notification in 8 seconds" and turn the screen off.

## What this build does

- Ring-only mode (default): when a notification arrives with the screen off, a
  black screen shows just the animated ring for 10 seconds, then the screen
  turns off. It reminds for 4 seconds every 2 minutes, for up to an hour, until
  the phone is woken.
- All apps, or only the apps you tick.
- A colour per app, a default colour, and a separate missed-call colour.
- Six animations: Swirl, Pulse, Blink, Double spin, Ripple, Rainbow.
- Speed, size, thickness and position controls.
- AOD mode (experimental switch): draws the ring on Samsung's Always On Display.
