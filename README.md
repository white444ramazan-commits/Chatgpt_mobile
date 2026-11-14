# GORILLA CONTROL
Android companion for a device you own. Initial milestone: dark dashboard, KernelSU detection, Accessibility setup, and CI-built debug APK.

## Build
Open Actions → Android CI → Run workflow. Download the gorilla-control-debug-apk artifact from the completed run.

## Setup
Install the APK. Enable the Accessibility service only if you want to use explicitly initiated UI gestures. KernelSU access is checked locally; this milestone does not expose an unrestricted remote root shell.

Screen capture will require Android's system consent dialog. Remote ChatGPT/MCP connectivity is a later milestone requiring authenticated encrypted transport and device-side approval.
