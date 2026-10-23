#!/usr/bin/env python3
"""Walks Keel's screens on an emulator and reports what TalkBack and a finger would struggle with.

Usage (debug build installed, from android/):
  python3 tools/a11y_audit.py <serial> <outdir> [screen ...]

A screen is a debug route (home, cycle, more, settings, ...; all of them when none are given) or
`name=<am start extras> [tap:Visible_text ...]`, for example
  "checkin=--ez uitOnboarded true tap:New_check-in tap:Good"
(underscores in a tap stand for spaces). Each screen is scrolled a page at a time; every page's
uiautomator dump and screenshot land in <outdir>.

Reported: actionable nodes with no text or description anywhere inside (TalkBack says only
"Button"), and actionable nodes under 48 dp (Android's minimum touch target). Nodes cut by the
screen edge or a scrolling viewport show up small too; check those in the screenshots. Seed some
records first (see android/CLAUDE.md) so lists aren't empty.
"""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

ADB = os.path.expanduser("~/Library/Android/sdk/platform-tools/adb")
PKG = "com.therecalibrationyears.keel"
serial, outdir = sys.argv[1], sys.argv[2]
os.makedirs(outdir, exist_ok=True)


def adb(*args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, text=True).stdout


px_per_dp = int(re.search(r"(\d+)\s*$", adb("shell", "wm", "density").strip()).group(1)) / 160.0
w, h = map(int, re.search(r"(\d+)x(\d+)\s*$", adb("shell", "wm", "size").strip()).groups())


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))


def has_label(node):
    return any((n.get("text") or "").strip() or (n.get("content-desc") or "").strip() for n in node.iter())


def describe(node):
    texts = [(n.get("text") or n.get("content-desc") or "").strip() for n in node.iter()]
    return (f"{node.get('class', '').split('.')[-1]} text={(node.get('text') or '')[:40]!r} "
            f"desc={(node.get('content-desc') or '')[:40]!r} sub={[t for t in texts if t][:3]}")


def dump(tag):
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    with open(os.path.join(outdir, f"{tag}.xml"), "w") as f:
        f.write(xml)
    try:
        return ET.fromstring(xml[xml.index("<?xml"):] if "<?xml" in xml else xml)
    except Exception as e:  # a dump taken mid-transition
        print(f"  [{tag}] could not parse the dump: {e}")
        return None


def audit(root, seen):
    issues = []
    for n in root.iter("node"):
        if n.get("package") != PKG or not any(n.get(k) == "true" for k in ("clickable", "checkable", "long-clickable")):
            continue
        x1, y1, x2, y2 = bounds(n)
        key = describe(n)
        # Empty, already reported, or cut by the screen edge (judged on another page).
        if x2 <= x1 or y2 <= y1 or key in seen or y1 <= 0 or y2 >= h - 1 or x1 <= 0 or x2 >= w - 1:
            continue
        seen.add(key)
        if not has_label(n):
            issues.append(f"UNLABELLED  {key} @{n.get('bounds')}")
        wdp, hdp = (x2 - x1) / px_per_dp, (y2 - y1) / px_per_dp
        if wdp < 47.5 or hdp < 47.5:
            issues.append(f"SMALL {wdp:.0f}x{hdp:.0f}dp  {key} @{n.get('bounds')}")
    return issues


def screenshot(tag):
    with open(os.path.join(outdir, f"{tag}.png"), "wb") as f:
        f.write(subprocess.run([ADB, "-s", serial, "exec-out", "screencap", "-p"], capture_output=True).stdout)


def tap_text(text):
    root = dump("_tap")
    for n in root.iter("node") if root is not None else []:
        if text in ((n.get("text") or ""), (n.get("content-desc") or "")):
            x1, y1, x2, y2 = bounds(n)
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(3)
            return
    print(f"  could not find {text!r} to tap")


def signature(root):
    return tuple(sorted((n.get("text") or "") + (n.get("content-desc") or "") for n in root.iter("node") if n.get("package") == PKG))


def walk(name, extras, taps, pages=6):
    adb("shell", "am", "start", "-S", "-W", "-n", f"{PKG}/.MainActivity", *extras)
    time.sleep(6)
    for t in taps:
        tap_text(t)
    seen, issues, last = set(), [], None
    for i in range(pages):
        root = dump(f"{name}-{i}")
        if root is None or signature(root) == last:
            break
        last = signature(root)
        screenshot(f"{name}-{i}")
        issues += audit(root, seen)
        # A slow swipe near the left edge (not the back-gesture strip), so nothing is tapped.
        x = str(int(w * 0.08))
        adb("shell", "input", "swipe", x, str(int(h * 0.7)), x, str(int(h * 0.4)), "900")
        time.sleep(1.5)
    print(f"== {name}: {len(issues)} issue(s)")
    for line in issues:
        print("   " + line)


ROUTES = ["home", "cycle", "medications", "patterns", "notes", "more", "gp_summary", "reminders", "profile",
          "activities", "health", "stats", "settings", "app_lock", "themes", "colour_mode", "mood_icons",
          "backup", "support", "connect", "about", "privacy"]

for screen in sys.argv[3:] or ROUTES:
    if "=" in screen:
        name, rest = screen.split("=", 1)
        parts = rest.split()
        walk(name, [p for p in parts if not p.startswith("tap:")], [p[4:].replace("_", " ") for p in parts if p.startswith("tap:")])
    else:
        walk(screen, ["--ez", "uitOnboarded", "true"] + ([] if screen == "home" else ["--es", "uitRoute", screen]), [])
