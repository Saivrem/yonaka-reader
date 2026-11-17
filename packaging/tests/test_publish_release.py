import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "publish_release", Path(__file__).parents[1] / "tools" / "publish_release.py"
)
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)

VERSION = "0.17.0"
COMMIT = "a" * 40


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        self.source = root / "input"
        self.source.mkdir()
        self.output = root / "output"
        for suffix in release.PACKAGES:
            (self.source / f"package-{VERSION}{suffix}").write_bytes(b"package contents")

    def publish(self):
        release.publish("owner/repo", COMMIT, VERSION, self.source, self.output)

    def test_manifest_covers_all_six_packages(self):
        assets = release.prepare_assets(self.source, self.output, VERSION)
        self.assertEqual(7, len(assets))
        lines = assets[-1].read_text().splitlines()
        self.assertEqual(6, len(lines))
        for path, line in zip(assets, lines):
            self.assertEqual(f"{release.checksum(path)}  {path.name}", line)

    def test_rejects_missing_empty_duplicate_and_wrong_version(self):
        source = self.source / f"package-{VERSION}.dmg"
        for failure in ["missing", "empty", "duplicate", "version"]:
            with self.subTest(failure=failure):
                source.write_bytes(b"data")
                extra = self.source / "other.dmg"
                if failure == "missing":
                    source.unlink()
                elif failure == "empty":
                    source.write_bytes(b"")
                elif failure == "duplicate":
                    extra.write_bytes(b"data")
                else:
                    source.rename(extra)
                with self.assertRaises(ValueError):
                    release.prepare_assets(self.source, self.output, VERSION)
                extra.unlink(missing_ok=True)

    @patch.object(release, "gh")
    @patch.object(release, "api")
    def test_published_version_is_untouched(self, api, gh):
        api.return_value = [{"tag_name": "v0.17.0", "draft": False}]
        self.publish()
        api.assert_called_once()
        gh.assert_not_called()
        self.assertFalse(self.output.exists())

    @patch.object(release, "api")
    def test_conflicting_draft_is_rejected(self, api):
        api.return_value = [{"tag_name": "v0.17.0", "draft": True, "target_commitish": "b" * 40}]
        with self.assertRaises(ValueError):
            self.publish()
        api.assert_called_once()

    @patch.object(release, "api")
    def test_conflicting_tag_is_rejected(self, api):
        api.return_value = {"object": {"type": "commit", "sha": "b" * 40}}
        with self.assertRaises(ValueError):
            release.ensure_tag("owner/repo", "v0.17.0", COMMIT)
        api.assert_called_once()

    @patch.object(release, "api")
    def test_annotated_tag_is_resolved(self, api):
        api.side_effect = [
            {"object": {"type": "tag", "sha": "tag-sha"}},
            {"object": {"type": "commit", "sha": COMMIT}},
        ]
        release.ensure_tag("owner/repo", "v0.17.0", COMMIT)
        self.assertEqual(2, api.call_count)

    @patch.object(release, "api")
    def test_missing_tag_targets_tested_commit(self, api):
        api.return_value = None
        release.ensure_tag("owner/repo", "v0.17.0", COMMIT)
        self.assertEqual({"ref": "refs/tags/v0.17.0", "sha": COMMIT},
                         api.call_args.args[3])

    @patch.object(release, "api")
    def test_invalid_version_or_commit_cannot_mutate_remote(self, api):
        for version, commit in [("0.17", COMMIT), ("01.17.0", COMMIT),
                                (VERSION, "main")]:
            with self.subTest(version=version, commit=commit):
                with self.assertRaises(ValueError):
                    release.publish("owner/repo", commit, version, self.source, self.output)
        api.assert_not_called()

    def uploaded_assets(self):
        assets = release.prepare_assets(self.source, self.output, VERSION)
        return [{"name": path.name, "state": "uploaded", "size": path.stat().st_size,
                 "digest": f"sha256:{release.checksum(path)}"} for path in assets]

    def test_new_release_and_draft_retry_publish_only_after_verified_upload(self):
        for resume in [False, True]:
            with self.subTest(resume=resume), patch.object(release, "api") as api, \
                    patch.object(release, "gh") as gh:
                draft = {"tag_name": "v0.17.0", "draft": True, "target_commitish": COMMIT}
                api.side_effect = [
                    [draft] if resume else [],
                    {"object": {"type": "commit", "sha": COMMIT}},
                    [{"tag_name": "v0.17.0", "id": 1, "assets": self.uploaded_assets()}],
                    {"html_url": "https://example.test/release"},
                ]
                self.publish()
                self.assertEqual(1 if resume else 2, gh.call_count)
                self.assertEqual("upload", gh.call_args.args[1])
                self.assertEqual({"draft": False, "make_latest": "legacy"},
                                 api.call_args.args[3])

    def test_incomplete_or_corrupt_upload_cannot_publish(self):
        for failure in ["missing", "size", "digest", "state", "extra"]:
            with self.subTest(failure=failure), patch.object(release, "api") as api, \
                    patch.object(release, "gh"):
                assets = self.uploaded_assets()
                if failure == "missing":
                    assets.pop()
                elif failure == "extra":
                    assets.append({"name": "unexpected"})
                else:
                    assets[0][failure] = "invalid"
                api.side_effect = [
                    [{"tag_name": "v0.17.0", "draft": True, "target_commitish": COMMIT}],
                    {"object": {"type": "commit", "sha": COMMIT}},
                    [{"tag_name": "v0.17.0", "id": 1, "assets": assets}],
                ]
                with self.assertRaises(ValueError):
                    self.publish()
                self.assertEqual(3, api.call_count)


if __name__ == "__main__":
    unittest.main()
