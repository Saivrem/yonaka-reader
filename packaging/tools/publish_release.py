"""Publish the already-tested CI artifacts, resuming incomplete draft releases."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess


PACKAGES = {
    ".dmg": "YonakaReader-{version}-macos-arm64.dmg",
    ".msi": "YonakaReader-{version}-windows-x64.msi",
    ".zip": "YonakaReader-{version}-windows-x64.zip",
    ".deb": "YonakaReader-{version}-linux-x64.deb",
    ".tar.gz": "YonakaReader-{version}-linux-x64.tar.gz",
    ".jar": "yonaka-reader-{version}.jar",
}


def gh(*arguments, optional=False, payload=None):
    result = subprocess.run(["gh", *arguments], input=payload, text=True, capture_output=True)
    if result.returncode:
        if optional and "(HTTP 404)" in result.stderr:
            return None
        raise RuntimeError(result.stderr.strip())
    return result.stdout


def api(repository, endpoint, method="GET", data=None, optional=False):
    arguments = ["api", f"repos/{repository}/{endpoint}", "--method", method]
    if data is not None:
        arguments += ["--input", "-"]
    result = gh(*arguments, optional=optional,
                payload=json.dumps(data) if data is not None else None)
    return json.loads(result) if result is not None else None


def find_release(repository, tag):
    # Listing includes authenticated drafts; the by-tag endpoint documents published releases.
    page = 1
    while True:
        releases = api(repository, f"releases?per_page=100&page={page}")
        for item in releases:
            if item["tag_name"] == tag:
                return item
        if len(releases) < 100:
            return None
        page += 1


def checksum(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def prepare_assets(artifacts, output, version):
    files = [path for path in artifacts.rglob("*") if path.is_file()]
    assets = []
    output.mkdir(parents=True, exist_ok=True)
    version_pattern = re.compile(rf"(?<![\d.]){re.escape(version)}(?!\d|\.\d)")

    for suffix, template in PACKAGES.items():
        matches = [path for path in files if path.name.endswith(suffix)]
        if len(matches) != 1:
            raise ValueError(f"Expected exactly one {suffix} artifact, found {len(matches)}")
        source = matches[0]
        if not version_pattern.search(source.name) or source.stat().st_size == 0:
            raise ValueError(f"Empty artifact or incorrect version: {source.name}")
        destination = output / template.format(version=version)
        shutil.copyfile(source, destination)
        assets.append(destination)

    manifest = output / "SHA256SUMS"
    manifest.write_text("".join(f"{checksum(path)}  {path.name}\n" for path in assets))
    return [*assets, manifest]


def ensure_tag(repository, tag, commit):
    reference = api(repository, f"git/ref/tags/{tag}", optional=True)
    if reference is None:
        api(repository, "git/refs", "POST", {"ref": f"refs/tags/{tag}", "sha": commit})
        return

    target = reference["object"]
    while target["type"] == "tag":
        target = api(repository, f"git/tags/{target['sha']}")["object"]
    if target["type"] != "commit" or target["sha"] != commit:
        raise ValueError(f"Tag {tag} belongs to a different commit; refusing to replace it")


def verify_assets(release, assets):
    uploaded = {asset["name"]: asset for asset in release["assets"]}
    if set(uploaded) != {path.name for path in assets}:
        raise ValueError("Draft release has missing or unexpected assets")
    for path in assets:
        asset = uploaded[path.name]
        if asset["state"] != "uploaded" or asset["size"] != path.stat().st_size:
            raise ValueError(f"Incomplete upload: {path.name}")
        if asset.get("digest") != f"sha256:{checksum(path)}":
            raise ValueError(f"Upload checksum mismatch: {path.name}")


def publish(repository, commit, version, artifacts, output):
    if not re.fullmatch(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", version):
        raise ValueError("Release version must be MAJOR.MINOR.PATCH")
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Release target must be a full commit SHA")

    tag = f"v{version}"
    release = find_release(repository, tag)
    if release is not None and not release["draft"]:
        print(f"{tag} is already published; no changes made")
        return
    if release is not None and release["target_commitish"] != commit:
        raise ValueError("Draft belongs to a different commit; refusing to mix builds")

    assets = prepare_assets(artifacts, output, version)
    ensure_tag(repository, tag, commit)
    if release is None:
        gh("release", "create", tag, "--repo", repository, "--target", commit,
           "--verify-tag", "--draft", "--generate-notes", "--title", f"Yonaka Reader {version}")

    gh("release", "upload", tag, "--repo", repository, "--clobber", *map(str, assets))
    release = find_release(repository, tag)
    verify_assets(release, assets)
    result = api(repository, f"releases/{release['id']}", "PATCH",
                 {"draft": False, "make_latest": "legacy"})
    print(f"Published {result['html_url']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    publish(arguments.repository, arguments.commit, arguments.version,
            arguments.artifacts, arguments.output)


if __name__ == "__main__":
    main()
