"""Local readiness endpoint for the Conta Squi TV pilot."""

from contextlib import closing
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, HTTPServer
import json

import psycopg2


def database_ready():
    try:
        with closing(psycopg2.connect(
            dbname="squitv_account_pilot",
            user="squitv_account_api",
            host="/var/run/postgresql",
            connect_timeout=2,
            options="-c statement_timeout=1500",
        )) as connection:
            with connection.cursor() as cursor:
                cursor.execute("SELECT max(version) FROM squitv.schema_migrations")
                return cursor.fetchone() == (1,)
    except psycopg2.Error:
        return False


class HealthHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/health":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        ready = database_ready()
        body = json.dumps({"ok": ready}).encode("utf-8")
        self.send_response(HTTPStatus.OK if ready else HTTPStatus.SERVICE_UNAVAILABLE)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Request URLs and headers are intentionally kept out of the pilot log.
        pass


if __name__ == "__main__":
    with HTTPServer(("127.0.0.1", 8765), HealthHandler) as server:
        server.serve_forever()
