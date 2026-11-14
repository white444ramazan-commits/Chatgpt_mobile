# GORILLA CONTROL — remote MCP server

This is the HTTPS bridge between ChatGPT and one paired Android device. It uses Cloudflare Workers, D1 and KV. The Android app makes outbound HTTPS requests; no inbound port or public ADB endpoint is opened.

## Safety model
- OAuth 2.1 protects the MCP endpoint and binds tokens to the exact Worker MCP URL.
- The OAuth consent page displays the client, granted scopes and redirect host, and uses Cloudflare's browser-bound, single-use consent handle.
- A separate pairing secret enrolls the device.
- The device bearer token is stored hashed in D1; the raw token is returned once at pairing.
- MCP tools only enqueue allowlisted requests: tap, swipe, and launch app.
- Commands expire after 90 seconds.
- The Android client must display each request and the user must approve it locally before execution.
- There is no arbitrary shell tool, remote file browser, hidden screen capture, or silent input injection.

## One-time setup
1. Create a Cloudflare account and find your **Account ID** in the account overview.
2. Create a Cloudflare API token with **Workers Scripts: Edit**, **D1: Edit**, and **Workers KV Storage: Edit** permissions for that account.
3. In the GitHub repository open **Settings → Secrets and variables → Actions** and add:
   - `CLOUDFLARE_API_TOKEN`
   - `CLOUDFLARE_ACCOUNT_ID`
   - `DEVICE_PAIRING_CODE` — a long random secret; enter the same value in the Android app.
   - `OWNER_PASSPHRASE` — a different long random secret; enter this only on the OAuth authorization page.
4. Open **Actions → Deploy GORILLA MCP → Run workflow**. The workflow provisions a workers.dev subdomain, D1 database and KV namespace, applies the schema, sets Worker secrets and deploys the Worker.
5. Copy the Worker HTTPS URL from the deploy log, e.g. `https://gorilla-control-mcp.<your-subdomain>.workers.dev`.

Never put secrets in source files, issues, screenshots, or chat messages. A public GitHub repository is okay because the secrets live in GitHub Actions Secrets.

## Pair Android
1. Install the GORILLA CONTROL 0.4.0 APK.
2. In the app, enter the Worker HTTPS URL and the `DEVICE_PAIRING_CODE`.
3. Tap **PAIR AND CONNECT**. Approve the KernelSU Root request if prompted.
4. Keep the GORILLA CONTROL foreground-service notification enabled. The app polls for requests over HTTPS.
5. Each request is shown in the app; tap **РАЗРЕШИТЬ** or **ОТКЛОНИТЬ**.

## Connect ChatGPT
Custom MCP connections with write/control actions require a ChatGPT Business or Enterprise/Edu workspace on ChatGPT web. Pro supports read/fetch access only; Free does not currently support custom MCP connections. When the account/workspace permits it, add a custom MCP server with URL `https://<your-worker>.workers.dev/mcp`, OAuth authentication, and approve the requested scopes. ChatGPT mobile does not provide the custom-server connection setup flow.

## MCP tools
- `get_device_status`
- `list_recent_commands`
- `get_command_result`
- `request_tap`
- `request_swipe`
- `request_launch_app`

The first three are read tools. The last three only queue a command; device-side confirmation is mandatory.
