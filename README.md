# GORILLA CONTROL

Android + KernelSU device controller with a remote ChatGPT MCP bridge.

## Android
The app uses a local allowlist of Root operations and a foreground HTTPS polling connection to the paired Worker. It does not require Accessibility. Remote tap, swipe and app-launch requests remain queued until the device owner taps **РАЗРЕШИТЬ** in the app.

## Remote MCP server
See [server/README.md](server/README.md) for Cloudflare D1/KV provisioning, GitHub Actions secrets, deployment and ChatGPT setup.

## Build APK
Open **Actions → Android CI**. The workflow builds a debug APK and uploads it as `gorilla-control-debug-apk`.

## Limitations
- No arbitrary remote shell, hidden screen monitoring, or inbound ADB listener.
- The app requires the user to pair it with a Cloudflare Worker and approve each command.
- ChatGPT custom MCP write actions require a compatible ChatGPT workspace/account and are connected on the web, not the mobile app.
