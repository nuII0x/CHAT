#!/usr/bin/env python3
"""Publica APK e update.json de forma atômica para o servidor onion local."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import tempfile
from pathlib import Path

ONION_V3 = re.compile(r"^[a-z2-7]{56}\.onion$")
VERSION_NAME = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+$")
MAX_APK_BYTES = 512 * 1024 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Publica uma release assinada do NullChat")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--public-dir", type=Path, required=True)
    parser.add_argument("--onion-host", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--release-notes", default="Correções e melhorias.")
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_copy(source: Path, destination: Path) -> None:
    fd, temporary_name = tempfile.mkstemp(prefix=f".{destination.name}.", dir=destination.parent)
    try:
        with os.fdopen(fd, "wb") as output, source.open("rb") as input_file:
            shutil.copyfileobj(input_file, output, length=1024 * 1024)
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary_name, 0o644)
        os.replace(temporary_name, destination)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def atomic_json(payload: dict[str, object], destination: Path) -> None:
    fd, temporary_name = tempfile.mkstemp(prefix=".update.", dir=destination.parent, text=True)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            json.dump(payload, output, ensure_ascii=False, indent=2)
            output.write("\n")
            output.flush()
            os.fsync(output.fileno())
        os.chmod(temporary_name, 0o644)
        os.replace(temporary_name, destination)
    finally:
        if os.path.exists(temporary_name):
            os.unlink(temporary_name)


def main() -> None:
    args = parse_args()
    apk = args.apk.resolve(strict=True)
    if not apk.is_file() or apk.suffix.lower() != ".apk":
        raise SystemExit("Informe um APK válido")
    if not ONION_V3.fullmatch(args.onion_host.lower()):
        raise SystemExit("Informe o hostname onion v3, sem http:// ou caminho")
    if args.version_code <= 0 or not VERSION_NAME.fullmatch(args.version_name):
        raise SystemExit("Versão inválida")
    apk_size = apk.stat().st_size
    if not 1 <= apk_size <= MAX_APK_BYTES:
        raise SystemExit("APK vazio ou maior que 512 MiB")

    public_dir = args.public_dir.resolve()
    public_dir.mkdir(parents=True, exist_ok=True, mode=0o755)
    apk_name = f"NullChat-{args.version_name}.apk"
    destination = public_dir / apk_name
    atomic_copy(apk, destination)

    manifest = {
        "schemaVersion": 1,
        "versionCode": args.version_code,
        "versionName": args.version_name,
        "apkUrl": f"http://{args.onion_host.lower()}/{apk_name}",
        "apkSha256": sha256(destination),
        "apkSize": apk_size,
        "releaseNotes": args.release_notes.strip(),
    }
    atomic_json(manifest, public_dir / "update.json")
    print(f"Release {args.version_name} publicada em {public_dir}")
    print(f"SHA-256: {manifest['apkSha256']}")


if __name__ == "__main__":
    main()
