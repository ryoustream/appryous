#!/usr/bin/env python3
"""
Baca JSON release dari stdin, print URL APK yang paling sesuai.
Priority: universal > arm64-v8a > arm64 > apapun yang .apk
"""
import sys
import json

data = json.load(sys.stdin)
assets = data.get("assets", [])

for priority in ["universal", "arm64-v8a", "arm64", ""]:
    for asset in assets:
        name = asset["name"].lower()
        if name.endswith(".apk") and (priority == "" or priority in name):
            print(asset["browser_download_url"])
            sys.exit(0)

# Tidak ada APK ditemukan
print("ERROR: Tidak ada APK ditemukan", file=sys.stderr)
for asset in assets:
    print(f"  - {asset['name']}", file=sys.stderr)
sys.exit(1)
