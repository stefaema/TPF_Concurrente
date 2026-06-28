#!/usr/bin/env python3
"""Lanzador del app de flashcards. Abre el browser automáticamente."""

import http.server
import socketserver
import webbrowser
import threading
import os
import sys

PORT = 8765
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=SCRIPT_DIR, **kwargs)

    def log_message(self, fmt, *args):
        pass  # silenciar logs por defecto


def main(port=PORT):
    try:
        with socketserver.TCPServer(("", port), Handler) as httpd:
            url = f"http://localhost:{port}/flashcards.html"
            print(f"\n🎴  Flashcards — Concurrencia")
            print(f"    URL: {url}")
            print(f"    Ctrl+C para cerrar\n")
            threading.Timer(0.4, lambda: webbrowser.open(url)).start()
            httpd.serve_forever()
    except OSError:
        print(f"Puerto {port} ocupado. Probando {port + 1}...")
        main(port + 1)
    except KeyboardInterrupt:
        print("\nCerrado.")
        sys.exit(0)


if __name__ == "__main__":
    main()
