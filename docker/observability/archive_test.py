import json
import pathlib
import tempfile
import unittest
from archive import Archive


def envelope(**extra):
    row = {"@timestamp": "2026-11-14T00:00:00Z", "service": {"name": "test"}, "log": {"level": "INFO"}, "message": "controlled", **extra}
    return {"log": json.dumps(row), "container_id": "not copied", "env": "not copied"}


class ArchiveTests(unittest.TestCase):
    def test_ecs_and_metadata(self):
        with tempfile.TemporaryDirectory() as d:
            a = Archive(d)
            a.append(envelope(correlationId="cid", trace={"id": "trace"}, operationId="purchase:1"))
            rows = list(a.rows())
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0]["trace"]["id"], "trace")
            self.assertNotIn("env", rows[0])

    def test_retention_and_capacity_real_files(self):
        with tempfile.TemporaryDirectory() as d:
            a = Archive(d, cap=4096, segment=1024, ttl=10)
            for i in range(100):
                a.append(envelope(message="x"*500, eventId=str(i)))
                self.assertLessEqual(a.stats()["bytes"], 4096)
            self.assertGreater(a.stats()["evictedSegments"], 0)
            a.clock = lambda: __import__('time').time()+11
            a.maintain()
            self.assertEqual(a.stats()["bytes"], 0)
            self.assertGreater(a.stats()["expiredSegments"], 0)

    def test_restart_dedup_and_read_only_query(self):
        with tempfile.TemporaryDirectory() as d:
            a = Archive(d)
            a.append(envelope(eventId="same"))
            b = Archive(d)
            b.append(envelope(eventId="same"))
            self.assertEqual(len(list(b.rows())), 1)
            self.assertEqual(b.stats()["duplicates"], 1)

    def test_secret_canaries_and_malformed(self):
        with tempfile.TemporaryDirectory() as d:
            a = Archive(d)
            for message in ["Authorization: Bearer secret", "password=canary", "client_secret=canary", "eyJcanary.payload.signature", "-----BEGIN RSA PRIVATE KEY-----"]:
                a.append(envelope(message=message))
            a.append({"log": "not JSON"})
            self.assertEqual(list(a.rows()), [])
            self.assertEqual(a.stats()["invalid"], 6)

    def test_foreign_files_are_never_pruned(self):
        with tempfile.TemporaryDirectory() as d:
            p = pathlib.Path(d)/"unrelated.txt"
            p.write_text("untouched")
            a = Archive(d, cap=1024, segment=1024, ttl=1)
            a.maintain(2000)
            self.assertTrue(p.exists())

    def test_malformed_envelopes_do_not_poison_following_records(self):
        with tempfile.TemporaryDirectory() as d:
            a = Archive(d)
            for value in [None, [], "untrusted", {"log": 123}, envelope(eventId=["invalid"]), envelope(trace="invalid")]:
                a.append(value)
            a.append(envelope(eventId="valid"))
            self.assertEqual(a.stats()["invalid"], 6)
            self.assertEqual([r["eventId"] for r in a.rows()], ["valid"])

    def test_nested_metadata_allowlist(self):
        with tempfile.TemporaryDirectory() as d:
            a = Archive(d)
            a.append(envelope(log={"level": "INFO", "logger": "test", "untrusted": "ignored"}, trace={"id": "abc", "untrusted": "ignored"}))
            row = list(a.rows())[0]
            self.assertEqual(row["log"], {"level": "INFO", "logger": "test"})
            self.assertEqual(row["trace"], {"id": "abc"})


if __name__ == '__main__':
    unittest.main()
