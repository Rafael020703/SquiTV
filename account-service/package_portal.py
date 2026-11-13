"""Prepare static portal files with content-addressed asset URLs."""

import argparse
import hashlib
from pathlib import Path


def package_portal(destination):
    source = Path(__file__).resolve().parent / "portal"
    html = (source / "index.html").read_text(encoding="utf-8")
    assets = destination / "assets"
    assets.mkdir(parents=True, exist_ok=True)

    for name in ("account.js", "style.css"):
        path = source / name
        data = path.read_bytes()
        version = hashlib.sha256(data).hexdigest()[:16]
        asset = f"{path.stem}-{version}{path.suffix}"
        (assets / asset).write_bytes(data)
        reference = f'"/{name}"'
        if html.count(reference) != 1:
            raise ValueError(f"Unexpected reference for {name}")
        html = html.replace(reference, f'"/assets/{asset}"')
        if name == "style.css":
            # The authentication pages have their own deployment lifecycle.
            (destination / name).write_bytes(data)

    (destination / "index.html").write_text(html, encoding="utf-8", newline="\n")

    auth_css = (source / "auth.css").read_bytes()
    auth_version = hashlib.sha256(auth_css).hexdigest()[:16]
    auth_asset = f"auth-{auth_version}.css"
    (assets / auth_asset).write_bytes(auth_css)
    login_theme = source.parent / "identity" / "themes" / "squitv" / "login"
    login_output = destination / "theme" / "login"
    login_output.mkdir(parents=True, exist_ok=True)
    for path in login_theme.iterdir():
        if path.is_file():
            contents = path.read_bytes()
            if path.name == "template.ftl":
                contents = contents.replace(b'"/auth.css"', f'"/assets/{auth_asset}"'.encode("ascii"))
            (login_output / path.name).write_bytes(contents)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("destination", type=Path)
    package_portal(parser.parse_args().destination)