# GORILLA CONTROL

Android companion for a device you own. Current milestone: dark dashboard, KernelSU status check, Android 16 Restricted Settings guidance, an OS confirmation-flow launcher, and GitHub Actions APK builds.

## Build
Open **Actions → Android CI → Run workflow**, or push to `main`. Download the `gorilla-control-debug-apk` artifact from the completed run.

## Target device
The current compatibility profile is Xiaomi Redmi Pad 2 Pro / HyperOS 3 / Android 16. The app remains Android-compatible from API 26 upward.

## Enable Accessibility on Android 16
1. Install and open GORILLA CONTROL.
2. Tap **Prepare Restricted Settings**. This asks Android to show its own restricted-setting explanation; it does not grant permission.
3. Open the app's system info page from the app.
4. If HyperOS exposes **Allow restricted settings**, enable it and confirm with the device screen lock.
5. Return to **Settings → Accessibility → Downloaded apps/services → GORILLA CONTROL** and enable the service.
6. Re-open GORILLA CONTROL and tap **Check Accessibility**.

Some OEM settings apps may hide or omit the Allow restricted settings entry. A normal application cannot grant itself Accessibility, clear the system restriction, or replace the user's confirmation. Do not use root commands to force-enable this sensitive access.

## Service binding
The Accessibility service is exported so Android's system process can bind to it, and protected by `android.permission.BIND_ACCESSIBILITY_SERVICE`. This is the required Android service pattern. The current service implementation is intentionally minimal and does not yet provide remote device control.

## Safety and scope
- Accessibility activation is always user-controlled.
- Screen capture requires Android's system consent dialog.
- Root status is checked locally; this milestone does not expose an unrestricted remote root shell.
- No hidden monitoring, stealth, or security-prompt bypass.
- Remote ChatGPT/MCP connectivity is a later milestone and must use authenticated encrypted transport with device-side approval.
