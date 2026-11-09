#!/usr/bin/env python3
"""Publish a built, signed player APK and its update record to GitHub Releases.

Run scripts/build.sh live first. Uses the existing git credential helper in memory.
Never uploads source configuration, signing files, or the admin APK.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
REPO = "web-enthusiast-prottoy/antihack-tour"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--notes", type=Path, required=True)
    parser.add_argument("--commit", required=True, help="Pushed source commit used for this APK")
    args = parser.parse_args()
    bundled_java = ROOT / ".tools/amazon-corretto-21.jdk/Contents/Home"
    if bundled_java.is_dir():
        os.environ.setdefault("JAVA_HOME", str(bundled_java))
    apk = ROOT / "releases/antihack-tour-live-candidate.apk"
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Library/Android/sdk")))
    tools = sdk / "build-tools/34.0.0"
    badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
    match = re.search(r"package: name='bd.arena.prototype' versionCode='(\d+)' versionName='([\d.]+)'", badging)
    if not match or "application-debuggable" in badging:
        raise SystemExit("Expected a non-debuggable production player APK.")
    subprocess.run([str(tools / "apksigner"), "verify", str(apk)], check=True)
    code, version = int(match[1]), match[2]
    tag = f"player-v{version}"
    filename = f"antihack-tour-{version}.apk"
    manifest = json.dumps({
        "packageId": "bd.arena.prototype", "versionCode": code, "versionName": version,
        "apkUrl": f"https://github.com/{REPO}/releases/download/{tag}/{filename}",
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    }, indent=2).encode()
    credential = subprocess.run(
        ["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
        text=True, capture_output=True, check=True,
        env={**os.environ, "GIT_TERMINAL_PROMPT": "0"}, timeout=30,
    )
    token = dict(line.split("=", 1) for line in credential.stdout.splitlines() if "=" in line).get("password")
    if not token:
        raise SystemExit("Existing GitHub sign-in is required.")

    def request(url, data=None, content_type="application/json", method=None):
        req = urllib.request.Request(url, data=data, method=method, headers={
            "Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
            "Content-Type": content_type, "User-Agent": "AntihackTour-release",
        })
        with urllib.request.urlopen(req, timeout=180) as response:
            return json.load(response)

    # Create a draft first: incomplete uploads cannot become visible to the updater.
    release = request(f"https://api.github.com/repos/{REPO}/releases", json.dumps({
        "tag_name": tag, "target_commitish": args.commit, "name": f"Antihack Tour {version}",
        "body": args.notes.read_text().rstrip() + "\n\n<!-- antihack-player-update\n" + manifest.decode() + "\n-->", "draft": True, "prerelease": False,
    }).encode())
    print(f"Created draft release {release['id']}", flush=True)
    upload = release["upload_url"].split("{")[0]
    for name, data, mime in [(filename, apk.read_bytes(), "application/vnd.android.package-archive"),
                             ("player-update.json", manifest, "application/json")]:
        request(upload + "?name=" + urllib.parse.quote(name), data, mime)
        print(f"Uploaded {name}", flush=True)
    published = request(release["url"], json.dumps({"draft": False, "make_latest": "true"}).encode(), method="PATCH")
    print(published["html_url"])


if __name__ == "__main__":
    main()
