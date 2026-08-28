#!/usr/bin/env python3
"""Fetch TestFlight beta feedback (tester screenshots + crash submissions) from the
App Store Connect API, so we don't have to copy-paste it out of App Store Connect.

No third-party packages: the ES256 JWT is signed by shelling out to `openssl`, and
everything else is Python standard library.

Credentials (create an API key at App Store Connect > Users and Access >
Integrations > App Store Connect API, role "Developer" or higher). Provide via env:

    ASC_KEY_ID      Key ID, e.g. "2X9R4HXF34"
    ASC_ISSUER_ID   Issuer ID (a UUID shown above the keys table)
    ASC_KEY_PATH    Path to the downloaded AuthKey_<KEYID>.p8 file

Optional:
    ASC_APP_ID      Numeric App Store Connect app id (otherwise resolved from bundle id)
    ASC_BUNDLE_ID   Bundle id to resolve the app id (default: com.keel)

Usage:
    python3 scripts/testflight_feedback.py [--limit N] [--kind screenshot|crash|both]
                                           [--download DIR] [--json]
    python3 scripts/testflight_feedback.py --selftest   # verify JWT signing, no creds

The .p8 is a secret: keep it out of the repo and pass it by path.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API_HOST = "https://api.appstoreconnect.apple.com"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def der_to_raw_ecdsa(der: bytes) -> bytes:
    """Convert an OpenSSL DER ECDSA signature (SEQUENCE{INTEGER r, INTEGER s}) into the
    JOSE raw form r||s, each left-padded to 32 bytes (P-256 / ES256)."""
    if der[0] != 0x30:
        raise ValueError("bad DER: expected SEQUENCE")
    i = 2  # P-256 signatures are short (<128 bytes), so the length is single-byte
    if der[i] != 0x02:
        raise ValueError("bad DER: expected INTEGER r")
    rlen = der[i + 1]
    r = der[i + 2 : i + 2 + rlen]
    i = i + 2 + rlen
    if der[i] != 0x02:
        raise ValueError("bad DER: expected INTEGER s")
    slen = der[i + 1]
    s = der[i + 2 : i + 2 + slen]
    r = r.lstrip(b"\x00").rjust(32, b"\x00")
    s = s.lstrip(b"\x00").rjust(32, b"\x00")
    return r + s


def sign_es256(signing_input: bytes, key_path: str) -> bytes:
    """ES256-sign with openssl and return the raw 64-byte JOSE signature."""
    proc = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", key_path],
        input=signing_input, capture_output=True,
    )
    if proc.returncode != 0:
        raise RuntimeError("openssl signing failed: " + proc.stderr.decode(errors="replace"))
    return der_to_raw_ecdsa(proc.stdout)


def make_jwt(key_id: str, issuer_id: str, key_path: str, ttl_seconds: int = 1200) -> str:
    header = {"alg": "ES256", "kid": key_id, "typ": "JWT"}
    now = int(time.time())
    payload = {"iss": issuer_id, "iat": now, "exp": now + ttl_seconds, "aud": "appstoreconnect-v1"}
    signing_input = (b64url(json.dumps(header, separators=(",", ":")).encode())
                     + "." + b64url(json.dumps(payload, separators=(",", ":")).encode())).encode()
    signature = sign_es256(signing_input, key_path)
    return signing_input.decode() + "." + b64url(signature)


def api_get(path_or_url: str, token: str) -> dict:
    url = path_or_url if path_or_url.startswith("http") else API_HOST + path_or_url
    req = urllib.request.Request(url, headers={
        "Authorization": "Bearer " + token,
        "Accept": "application/json",
    })
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        raise RuntimeError(f"HTTP {e.code} for {url}\n{body}")


def resolve_app_id(token: str, bundle_id: str) -> str:
    q = urllib.parse.urlencode({"filter[bundleId]": bundle_id, "fields[apps]": "bundleId,name"})
    data = api_get(f"/v1/apps?{q}", token).get("data", [])
    if not data:
        raise SystemExit(f"No app found for bundle id {bundle_id!r}. Set ASC_APP_ID explicitly.")
    app = data[0]
    print(f"App: {app['attributes'].get('name')}  (id {app['id']}, {bundle_id})", file=sys.stderr)
    return app["id"]


def fetch_all(token: str, path: str, limit: int) -> list:
    """Follow pagination up to `limit` items."""
    items, url = [], path
    while url and len(items) < limit:
        page = api_get(url, token)
        items.extend(page.get("data", []))
        url = page.get("links", {}).get("next")
    return items[:limit]


def show(submissions: list, kind: str, token: str, download: str | None, since: str | None = None):
    if since:
        submissions = [s for s in submissions
                       if ((s.get("attributes") or {}).get("createdDate") or "")[:10] >= since]
    if not submissions:
        print(f"\n== {kind}: none" + (f" on/after {since}" if since else "") + " ==")
        return
    print(f"\n== {kind}: {len(submissions)}" + (f" on/after {since}" if since else "") + " ==")
    for s in submissions:
        attrs = s.get("attributes", {}) or {}
        print("-" * 68)
        print(f"id: {s.get('id')}")
        for key in ("createdDate", "comment", "deviceModel", "osVersion", "locale",
                    "appPlatform", "devicePlatform", "buildBundleId"):
            if attrs.get(key):
                print(f"  {key}: {attrs[key]}")
        # Print any remaining attributes we didn't name explicitly, so nothing is lost.
        for key, val in attrs.items():
            if key not in ("createdDate", "comment", "deviceModel", "osVersion", "locale",
                           "appPlatform", "devicePlatform", "buildBundleId") and val not in (None, ""):
                print(f"  {key}: {val}")
        # Screenshot images / crash logs hang off relationships; follow the related
        # link generically so we don't hard-code endpoint names that may change.
        for rel_name, rel in (s.get("relationships", {}) or {}).items():
            related = (rel.get("links") or {}).get("related")
            if related and any(w in rel_name.lower() for w in ("screenshot", "image", "crash", "log")):
                try:
                    for item in api_get(related, token).get("data", []):
                        a = item.get("attributes", {}) or {}
                        for f in ("url", "fileUrl", "downloadUrl", "fileName"):
                            if a.get(f):
                                print(f"  {rel_name}.{f}: {a[f]}")
                        if download and a.get("url") and a.get("fileName"):
                            dest = os.path.join(download, a["fileName"])
                            urllib.request.urlretrieve(a["url"], dest)
                            print(f"  downloaded -> {dest}")
                except Exception as ex:  # tolerate shape changes; don't abort the run
                    print(f"  ({rel_name}: could not fetch — {ex})")


def selftest() -> int:
    """Prove the ES256 signing pipeline with a throwaway key (no ASC needed)."""
    import tempfile
    d = tempfile.mkdtemp()
    key = os.path.join(d, "k.p8")
    pub = os.path.join(d, "k.pub")
    subprocess.run(["openssl", "genpkey", "-algorithm", "EC",
                    "-pkeyopt", "ec_paramgen_curve:P-256", "-out", key], check=True, capture_output=True)
    subprocess.run(["openssl", "pkey", "-in", key, "-pubout", "-out", pub], check=True, capture_output=True)
    jwt = make_jwt("TESTKEYID", "00000000-0000-0000-0000-000000000000", key)
    h, p, sig = jwt.split(".")
    # Re-encode the JOSE signature back to DER and verify against the public key.
    raw = base64.urlsafe_b64decode(sig + "=" * (-len(sig) % 4))
    r, s = raw[:32], raw[32:]
    def der_int(b):
        b = b.lstrip(b"\x00") or b"\x00"
        if b[0] & 0x80:
            b = b"\x00" + b
        return b"\x02" + bytes([len(b)]) + b
    body = der_int(r) + der_int(s)
    der = b"\x30" + bytes([len(body)]) + body
    sig_file = os.path.join(d, "sig.der")
    with open(sig_file, "wb") as f:
        f.write(der)
    v = subprocess.run(["openssl", "dgst", "-sha256", "-verify", pub, "-signature", sig_file],
                       input=(h + "." + p).encode(), capture_output=True)
    ok = v.returncode == 0
    payload = json.loads(base64.urlsafe_b64decode(p + "=" * (-len(p) % 4)))
    print("decoded payload:", payload)
    print("JWT signature verifies:", ok)
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser(description="Fetch TestFlight beta feedback from App Store Connect.")
    ap.add_argument("--limit", type=int, default=50)
    ap.add_argument("--kind", choices=("screenshot", "crash", "both"), default="both")
    ap.add_argument("--download", metavar="DIR", help="download screenshots into DIR")
    ap.add_argument("--since", metavar="YYYY-MM-DD", help="only feedback created on/after this date")
    ap.add_argument("--json", action="store_true", help="dump raw JSON instead of a summary")
    ap.add_argument("--selftest", action="store_true", help="verify JWT signing and exit")
    args = ap.parse_args()

    if args.selftest:
        sys.exit(selftest())

    key_id = os.environ.get("ASC_KEY_ID")
    issuer = os.environ.get("ASC_ISSUER_ID")
    key_path = os.environ.get("ASC_KEY_PATH")
    missing = [n for n, v in (("ASC_KEY_ID", key_id), ("ASC_ISSUER_ID", issuer), ("ASC_KEY_PATH", key_path)) if not v]
    if missing:
        sys.exit("Missing env: " + ", ".join(missing) + "\nSee the header of this file for setup.")
    key_path = os.path.expanduser(key_path)
    if not os.path.exists(key_path):
        sys.exit(f"Key file not found: {key_path}")

    token = make_jwt(key_id, issuer, key_path)
    app_id = os.environ.get("ASC_APP_ID") or resolve_app_id(token, os.environ.get("ASC_BUNDLE_ID", "com.keel"))
    if args.download:
        os.makedirs(args.download, exist_ok=True)

    for kind, rel in (("screenshot", "betaFeedbackScreenshotSubmissions"),
                      ("crash", "betaFeedbackCrashSubmissions")):
        if args.kind not in (kind, "both"):
            continue
        q = urllib.parse.urlencode({"limit": min(args.limit, 200)})
        subs = fetch_all(token, f"/v1/apps/{app_id}/{rel}?{q}", args.limit)
        if args.json:
            print(json.dumps(subs, indent=2))
        else:
            show(subs, kind, token, args.download, args.since)


if __name__ == "__main__":
    main()
