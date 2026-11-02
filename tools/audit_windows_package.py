"""Read-only inventory of a jpackage image. Does not open or install the application."""
import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path
import zipfile


def audit(image: Path, installer: Path | None = None) -> dict:
    image = image.resolve(strict=True)
    if not (image / "app").is_dir() or not (image / "runtime").is_dir():
        raise ValueError("Expected a jpackage image with app/ and runtime/ directories")
    files = sorted(path for path in image.rglob("*") if path.is_file())
    groups = defaultdict(int)
    vlc_groups = defaultdict(int)
    inventory = []
    jars = []
    for path in files:
        relative = path.relative_to(image).as_posix()
        size = path.stat().st_size
        parts = relative.split("/")
        group = "/".join(parts[:3]) if parts[:2] == ["app", "resources"] else parts[0]
        groups[group] += size
        inventory.append({"path": relative, "bytes": size})
        if relative.startswith("app/resources/vlc/"):
            tail = parts[3:]
            key = "/".join(tail[:2]) if tail[0] == "plugins" else tail[0]
            vlc_groups[key] += size
        if path.suffix.lower() == ".jar":
            with zipfile.ZipFile(path) as archive:
                entries = archive.infolist()
                jars.append({"path": relative, "bytes": size, "entries": len(entries),
                             "expanded_bytes": sum(entry.file_size for entry in entries)})
    result = {"image": image.name, "total_bytes": sum(groups.values()), "groups": dict(groups),
              "vlc_groups": dict(vlc_groups), "jars": sorted(jars, key=lambda row: -row["bytes"]),
              "files": inventory}
    if installer is not None:
        with installer.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        result["installer"] = {"name": installer.name, "bytes": installer.stat().st_size, "sha256": digest}
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("--installer", type=Path)
    parser.add_argument("--output", type=Path, help="New JSON file; refuses to replace an existing report")
    args = parser.parse_args()
    data = audit(args.image, args.installer)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("x", encoding="utf-8") as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
    summary = {key: data[key] for key in ("image", "total_bytes", "groups", "vlc_groups")}
    if "installer" in data:
        summary["installer"] = data["installer"]
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
