#!/usr/bin/env python3
"""Stage a signed Android release and schema-v1 manifest from the APK itself."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ANDROID_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
VERSION = re.compile(r"[vV]?(?:0|[1-9][0-9]*)(?:\.(?:0|[1-9][0-9]*)){0,3}\Z")


def version_parts(value):
    if len(value) > 64 or not VERSION.fullmatch(value):
        raise ValueError("Invalid stable version: " + value)
    parts = [int(part) for part in value.lstrip("vV").split(".")]
    if any(part > 2_147_483_647 for part in parts):
        raise ValueError("Version component overflow")
    return tuple(parts + [0] * (4 - len(parts)))


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def find_sdk(explicit=None):
    if explicit:
        return Path(explicit)
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key])
    local = Path(__file__).resolve().parents[1] / "local.properties"
    if local.is_file():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.partition("=")[2].replace(r"\:", ":").replace("\\\\", "\\"))
    raise ValueError("Pass --sdk or configure ANDROID_HOME; no SDK was found")


def find_tools(sdk):
    root = sdk / "build-tools"
    versions = [path for path in root.iterdir()
                if path.is_dir() and re.fullmatch(r"\d+\.\d+\.\d+", path.name)]
    if not versions:
        raise ValueError("No stable Android Build Tools found")
    tools = max(versions, key=lambda path: tuple(map(int, path.name.split("."))))
    aapt2 = tools / ("aapt2.exe" if os.name == "nt" else "aapt2")
    signer = tools / "lib" / "apksigner.jar"
    if not aapt2.is_file() or not signer.is_file():
        raise ValueError("Build Tools must contain aapt2 and lib/apksigner.jar")
    return aapt2, signer


def run_tool(arguments):
    result = subprocess.run([str(arg) for arg in arguments], capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=90)
    if result.returncode:
        raise ValueError("Android tool failed: " + (result.stderr or result.stdout).strip())
    return result.stdout


def parse_badging(text):
    package = next((line for line in text.splitlines() if line.startswith("package: ")), "")
    fields = dict(re.findall(r"(\w+)='([^']*)'", package))
    if fields.get("name") != "rsv.squitv" or "application-debuggable" in text.splitlines():
        raise ValueError("Only non-debug rsv.squitv release APKs can be distributed")
    name = fields.get("versionName", "")
    version_parts(name)
    if name.startswith(("v", "V")):
        raise ValueError("APK versionName must not include the tag prefix")
    code = int(fields.get("versionCode", "0"))
    if not 1 <= code <= 2_100_000_000 or int(fields.get("versionCodeMajor", "0")):
        raise ValueError("Invalid Android versionCode")
    sdk_match = re.search(r"^(?:sdkVersion|minSdkVersion):'(\d+)'$", text, re.MULTILINE)
    if not sdk_match or int(sdk_match[1]) < 1:
        raise ValueError("APK minimum SDK missing or invalid")
    return {"applicationId": fields["name"], "versionName": name,
            "versionCode": code, "minSdk": int(sdk_match[1])}


def verify_signature(apk, signer):
    java_home = os.environ.get("JAVA_HOME")
    java = Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java") if java_home else shutil.which("java")
    if not java:
        raise ValueError("Configure JAVA_HOME with a JDK supported by Android Build Tools")
    run_tool([java, "-jar", signer, "verify", "--verbose", apk])


def apk_abis(apk):
    with zipfile.ZipFile(apk) as archive:
        abis = sorted({name.split("/")[1] for name in archive.namelist()
                       if name.startswith("lib/") and len(name.split("/")) == 3 and name.endswith(".so")})
    if any(abi not in ANDROID_ABIS for abi in abis):
        raise ValueError("APK contains an unsupported native ABI")
    return abis


def generate(apk, tag, sdk, output_dir, not_latest=False):
    apk = Path(apk).resolve()
    output_dir = Path(output_dir).resolve()
    if not apk.is_file() or apk.suffix.lower() != ".apk":
        raise ValueError("Input must be an existing APK")
    version_parts(tag)
    aapt2, signer = find_tools(Path(sdk))
    metadata = parse_badging(run_tool([aapt2, "dump", "badging", apk]))
    if version_parts(metadata["versionName"]) > version_parts(tag):
        raise ValueError("APK version exceeds the product release tag")
    if not not_latest and version_parts(metadata["versionName"]) != version_parts(tag):
        raise ValueError("A latest release must carry the Android version corresponding to its tag")
    verify_signature(apk, signer)
    abis = apk_abis(apk)
    architecture = abis[0] if len(abis) == 1 else "universal" if abis else "any"
    asset_name = "SquiTV-v" + metadata["versionName"] + ".apk"
    digest = sha256(apk)
    artifact = dict(platform="android", kind="apk", architecture=architecture,
                    assetName=asset_name, sizeBytes=apk.stat().st_size, sha256=digest,
                    abis=abis, **metadata)
    manifest = dict(schemaVersion=1, productId="squi-tv", channel="stable",
                    releaseTag=tag, artifacts=[artifact])
    payload = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    checksum = digest + "  " + asset_name + "\n"
    destination = output_dir / asset_name
    manifest_path = output_dir / "squitv-update.json"
    checksum_path = output_dir / (asset_name + ".sha256")
    # Check every destination before writing. Never overwrite a different staged release.
    if destination.exists() and sha256(destination) != digest:
        raise FileExistsError("Output APK exists with different contents; choose a new output directory")
    for path, text in ((manifest_path, payload), (checksum_path, checksum)):
        if path.exists() and path.read_text(encoding="utf-8") != text:
            raise FileExistsError("Output metadata already differs; choose a new output directory")
    output_dir.mkdir(parents=True, exist_ok=True)
    if destination != apk and not destination.exists():
        shutil.copyfile(apk, destination)
    if sha256(destination) != digest:
        raise ValueError("Staged APK checksum mismatch")
    checksum_path.write_text(checksum, encoding="utf-8", newline="\n")
    manifest_path.write_text(payload, encoding="utf-8", newline="\n")
    return manifest_path, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--sdk", type=Path)
    parser.add_argument("--not-latest", action="store_true",
                        help="Allow an older Android artifact in a product release not advertised as latest")
    args = parser.parse_args()
    try:
        path, manifest = generate(args.apk, args.tag, find_sdk(args.sdk), args.output_dir, args.not_latest)
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.TimeoutExpired) as error:
        parser.exit(1, str(error) + "\n")
    print(path)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
