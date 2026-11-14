#!/system/bin/sh
# Minimal visible marker only; no daemon, listener, or unrestricted shell.
mkdir -p /data/adb/gorilla-control
echo "installed" > /data/adb/gorilla-control/status
chmod 600 /data/adb/gorilla-control/status
