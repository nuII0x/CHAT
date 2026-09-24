#!/usr/bin/env python3
"""Servidor local, sem logs, para arquivos de atualização publicados por um Onion Service."""

from __future__ import annotations

import argparse
import http.server
import os
from pathlib import Path
from urllib.parse import unquote, urlsplit


class UpdateHandler(http.server.SimpleHTTPRequestHandler):
    server_version = "NullChatUpdate/1"
    sys_version = ""

    def __init__(self, *args, directory: str, **kwargs) -> None:
        super().__init__(*args, directory=directory, **kwargs)

    def do_GET(self) -> None:
        if not self._allowed_path():
            self.send_error(404)
            return
        super().do_GET()

    def do_HEAD(self) -> None:
        if not self._allowed_path():
            self.send_error(404)
            return
        super().do_HEAD()

    def do_POST(self) -> None:
        self.send_error(405)

    def do_PUT(self) -> None:
        self.send_error(405)

    def do_DELETE(self) -> None:
        self.send_error(405)

    def end_headers(self) -> None:
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'none'")
        if urlsplit(self.path).path == "/update.json":
            self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, _format: str, *args: object) -> None:
        return

    def list_directory(self, path: str):
        self.send_error(404)
        return None

    def _allowed_path(self) -> bool:
        path = unquote(urlsplit(self.path).path)
        if path == "/update.json":
            return True
        return path.startswith("/NullChat-") and path.endswith(".apk") and "/" not in path[1:]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Serve releases do NullChat apenas no loopback")
    parser.add_argument("--directory", type=Path, required=True, help="Diretório publicado")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8088)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if args.host not in {"127.0.0.1", "::1"}:
        raise SystemExit("Por segurança, o servidor só pode escutar no loopback")
    if not 1 <= args.port <= 65535:
        raise SystemExit("Porta inválida")
    directory = args.directory.resolve(strict=True)
    if not directory.is_dir():
        raise SystemExit("Diretório publicado inválido")

    handler = lambda *handler_args, **handler_kwargs: UpdateHandler(
        *handler_args,
        directory=os.fspath(directory),
        **handler_kwargs,
    )
    server = http.server.ThreadingHTTPServer((args.host, args.port), handler)
    server.daemon_threads = True
    server.serve_forever()


if __name__ == "__main__":
    main()
