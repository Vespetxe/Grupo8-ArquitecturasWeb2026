"""Bounded ECS archive. No database, credentials, socket mounts or public query API.

The input is Docker's Forward envelope, not a trusted audit source. This archive
never decides business outcomes. Query output is explicitly requested locally.
"""
import argparse
import collections
import http.server
import json
import os
import pathlib
import re
import signal
import subprocess
import threading
import time

FIELDS = set("@timestamp log service message ecs errorCode errorCategory exceptionType rootErrorCategory errorOrigin compraId devolucionId eventoId etapa trace span traceId spanId eventType correlationId httpMethod httpRoute httpStatus durationMs actorType actorId completion eventId timestamp action auditScope resourceType resourceId operationId outcome resourceState actorVerified initiatorId initiatorType".split())
SEGMENT = re.compile(r"segment-[0-9]+\.jsonl\Z")
SENSITIVE = re.compile(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|-----BEGIN .*PRIVATE KEY-----|(?i:authorization\s*[:=]|client_secret\s*[:=]|password\s*[:=]|cookie\s*[:=])")
QUERY = {"correlationId", "traceId", "operationId"}


class Archive:
    def __init__(self, directory, cap=64*1024*1024, segment=1024*1024, ttl=86400, clock=time.time):
        if not (1024 <= segment <= cap <= 1024*1024*1024) or ttl < 1:
            raise ValueError("Invalid archive limits")
        self.directory = pathlib.Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.cap, self.segment, self.ttl, self.clock = cap, segment, ttl, clock
        self.lock = threading.RLock()
        self.counts = collections.Counter()
        self.seen = collections.OrderedDict()
        self.maintain()
        # Restore a bounded dedup window after restart. IDs come from the source.
        for row in self.rows():
            if row.get("eventId"):
                self.remember(row["eventId"])

    def files(self):
        return sorted((p for p in self.directory.iterdir() if SEGMENT.fullmatch(p.name) and p.is_file() and not p.is_symlink()), key=lambda p: p.name)

    def remember(self, event_id):
        self.seen[event_id] = None
        if len(self.seen) > 100000:
            self.seen.popitem(last=False)

    def maintain(self, incoming=0):
        with self.lock:
            files = self.files()
            for p in list(files):
                # mtime is local ingestion time. Producers cannot extend retention.
                if self.clock() - p.stat().st_mtime >= self.ttl:
                    p.unlink()
                    self.counts["expiredSegments"] += 1
                    files.remove(p)
            size = sum(p.stat().st_size for p in files)
            while files and size + incoming > self.cap:
                p = files.pop(0)
                size -= p.stat().st_size
                p.unlink()
                self.counts["evictedSegments"] += 1

    def append(self, envelope):
        with self.lock:
            if not isinstance(envelope, dict):
                self.counts["invalid"] += 1
                return
            raw = envelope.get("log", "")
            if not isinstance(raw, str) or len(raw.encode()) > min(65536, self.segment):
                self.counts["invalid"] += 1
                return
            try:
                row = json.loads(raw)
                assert isinstance(row, dict) and row.get("@timestamp") and isinstance(row.get("service"), dict) and row["service"].get("name")
                assert isinstance(row.get("log"), dict) and row["log"].get("level") and isinstance(row.get("message"), str)
                assert not row.get("eventId") or (isinstance(row["eventId"], str) and len(row["eventId"]) <= 160)
                row = {k: v for k, v in row.items() if k in FIELDS}
                for field, keys in {"log": {"level", "logger"}, "service": {"name"}, "ecs": {"version"}, "trace": {"id"}, "span": {"id"}}.items():
                    if field in row:
                        assert isinstance(row[field], dict)
                        row[field] = {k: v for k, v in row[field].items() if k in keys}
                encoded = (json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n").encode()
                assert not SENSITIVE.search(encoded.decode())
            except (ValueError, AssertionError, TypeError):
                self.counts["invalid"] += 1
                return
            if len(encoded) > self.segment:
                self.counts["invalid"] += 1
                return
            event_id = row.get("eventId")
            if event_id and event_id in self.seen:
                self.counts["duplicates"] += 1
                return
            self.maintain(len(encoded))
            files = self.files()
            now = int(self.clock()*1000000000)
            p = files[-1] if files else None
            if p is None or p.stat().st_size + len(encoded) > self.segment or now-int(p.stem.split("-")[1]) > 60*1000000000:
                # Exclusive creation, so restart never truncates a previous segment.
                p = self.directory / f"segment-{now:020d}.jsonl"
                while p.exists():
                    now += 1
                    p = self.directory / f"segment-{now:020d}.jsonl"
                with p.open("xb"):
                    pass
            with p.open("ab") as out:
                out.write(encoded)
                out.flush()
                os.fsync(out.fileno())
            if event_id:
                self.remember(event_id)
            self.counts["accepted"] += 1

    def rows(self):
        # Local CLI may race retention: a disappearing segment is harmless.
        for p in self.files():
            try:
                with p.open(encoding="utf-8") as stream:
                    for line in stream:
                        try:
                            yield json.loads(line)
                        except ValueError:
                            continue  # interrupted final write cannot invent an event
            except FileNotFoundError:
                continue

    def stats(self):
        with self.lock:
            self.maintain()
            return dict(self.counts, bytes=sum(p.stat().st_size for p in self.files()), segments=len(self.files()), cap=self.cap, segment=self.segment, ttl=self.ttl)


def serve():
    archive = Archive("/data/archive", int(os.environ.get("ARCHIVE_CAP_BYTES", 64*1024*1024)), int(os.environ.get("ARCHIVE_SEGMENT_BYTES", 1024*1024)), int(os.environ.get("ARCHIVE_TTL_SECONDS", 86400)))

    class Handler(http.server.BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass  # Never dump untrusted paths, headers or bodies.

        def do_GET(self):
            payload = json.dumps(archive.stats()).encode()
            self.send_response(200 if self.path == "/health" else 404)
            self.end_headers()
            self.wfile.write(payload)

        def do_POST(self):
            self.connection.settimeout(5)
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if self.path != "/ingest" or not 0 < size <= 2*1024*1024:
                    raise ValueError()
                data = self.rfile.read(size)
                if len(data) != size:
                    raise ValueError()
                for line in data.splitlines():
                    archive.append(json.loads(line))
                self.send_response(200)
            except (ValueError, TypeError, TimeoutError):
                self.send_response(400)
            self.end_headers()

    server = http.server.HTTPServer(("127.0.0.1", 9881), Handler)
    server.timeout = 1
    process = subprocess.Popen(["/fluent-bit/bin/fluent-bit", "-c", "/collector/fluent-bit.conf"])
    stopping = threading.Event()

    def stop(*_):
        stopping.set()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        while not stopping.is_set() and process.poll() is None:
            server.handle_request()
            archive.maintain()
    finally:
        server.server_close()
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
    return 0 if stopping.is_set() else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--query", choices=sorted(QUERY))
    parser.add_argument("--value")
    parser.add_argument("--limit", type=int, default=100)
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()
    if args.query or args.all:
        if not 1 <= args.limit <= 100000 or (args.query and (not args.value or len(args.value) > 160)):
            parser.error("Invalid query bounds")
        # Queries are strictly read-only: do not initialize or prune the archive.
        reader = object.__new__(Archive)
        reader.directory = pathlib.Path("/data/archive")
        count = 0
        for row in reader.rows():
            actual = row.get("trace", {}).get("id") if args.query == "traceId" else row.get(args.query)
            if args.all or actual == args.value:
                print(json.dumps(row, ensure_ascii=False))
                count += 1
                if count >= args.limit:
                    break
    else:
        raise SystemExit(serve())
