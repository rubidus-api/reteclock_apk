# Installing and using reteclock

## Install

Copy the APK to the device and open it, or use adb:

```sh
adb install -r dist/reteclock-0.1.0-debug.apk
```

On Android 4.4, "Unknown sources" must be enabled in Settings > Security. On newer Android, the
file manager or browser asks for permission to install unknown apps.

If an older reteclock is already installed and it was signed with a different key, uninstall it
first; Android refuses to replace an app with one signed by another key.

## Use

- Open reteclock from the launcher. The clock fills the screen and keeps the screen on.
- Turn the phone: the wide layout shows a big `HH:MM` on the left and seconds, weekday, date and
  year on the right; the tall layout stacks hour, minute, weekday with date, and a small line.
- Press Back to leave.

## Start when charging

By default, plugging in the charger opens the clock, and the clock shows over the lock screen.
Long press the clock to turn this on or off; a short message confirms the new state.

Android 10 and newer do not allow an app to start itself from the background. On those devices,
open the clock from the launcher, or use it as a screensaver.

## Screensaver (Daydream)

On Android 4.2 and newer:

Settings > Display > Daydream (or Screen saver) > reteclock, and set it to start when docked or
while charging.

## Burn-in

The clock moves its content by a few pixels once a minute, so an OLED panel does not keep the same
bright pixels lit. This is intentional and cannot be turned off.
