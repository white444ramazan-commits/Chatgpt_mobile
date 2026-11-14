# GORILLA CONTROL

Local-first Android device controller for a device owned by the user.

## Current milestone: Root Control 0.3.0
- Dark dashboard for Xiaomi Redmi Pad 2 Pro / HyperOS 3 / Android 16.
- KernelSU root availability check.
- User-initiated screen tap and swipe through a fixed allowlist of Android shell commands.
- User-initiated app launch using a validated package name.
- User-initiated PNG screenshot using `screencap -p`.
- No Accessibility Service requirement. This avoids the Android 13+ Restricted Settings gate for Accessibility.

## Install and use
1. Install the debug APK built by GitHub Actions.
2. Open GORILLA CONTROL.
3. Tap **ПРОВЕРИТЬ ROOT** and approve the KernelSU prompt for this app.
4. Use screen coordinates for TAP/SWIPE. Coordinates are validated against the current output of `wm size`.
5. Use screenshot to verify the current display.

## Security model
Root commands are fixed functions; the app does not expose a free-form shell command box or a network listener. Root permission remains controlled by KernelSU. Do not grant Root to an app you do not trust. Screenshots and touch input can expose private on-screen information and affect the device.

## Scope
This milestone is local-only. Direct ChatGPT/MCP connectivity is not present yet. A future remote link must use authenticated encrypted transport, per-command approval and explicit pairing. No hidden monitoring or stealth control is implemented.
