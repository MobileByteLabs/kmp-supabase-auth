#!/usr/bin/env python3
"""Verify every relative Markdown link in the assembled docsify site resolves.

Runs over the STAGED site, not the repo. That distinction is the whole point: the docs reuse the
repo's real Markdown (README, TARGET_MATRIX, per-module READMEs), whose relative links were
written for the repo layout. A link can be perfectly valid in the repo and still 404 once those
files are staged into the site — which is exactly the break this catches.

Usage: assert-docs-links.py <site-dir>
"""
import os
import re
import sys

LINK = re.compile(r"\]\(([^)\s]+)")
# Links inside HTML comments are never rendered, so they cannot 404 — but a comment EXPLAINING
# a link (e.g. documenting docsify's `![color](...)` directive) would otherwise be flagged.
HTML_COMMENT = re.compile(r"<!--.*?-->", re.S)
SKIP_PREFIXES = ("http://", "https://", "mailto:", "#", "<", "data:")

# docsify cover pages carry styling directives in link position — `![color](linear-gradient(...))`
# and `![color](#hex)`. They are instructions to docsify, not files, and flagging them would make
# the gate cry wolf on a page that is perfectly correct.
SKIP_CONTAINS = ("linear-gradient", "radial-gradient", "rgb(", "hsl(")


def main(site: str) -> int:
    missing = []
    for root, _dirs, files in os.walk(site):
        for name in files:
            if not name.endswith(".md"):
                continue
            page = os.path.join(root, name)
            with open(page, encoding="utf-8", errors="replace") as fh:
                body = HTML_COMMENT.sub("", fh.read())
            for target in LINK.findall(body):
                if target.startswith(SKIP_PREFIXES) or not target:
                    continue
                if any(token in target for token in SKIP_CONTAINS):
                    continue
                path = target.split("#", 1)[0]
                if not path:
                    continue
                # A leading "/" is SITE-root-relative (docsify), not filesystem-absolute —
                # the sidebar uses that form so it stays correct on every page.
                if path.startswith("/"):
                    resolved = os.path.normpath(os.path.join(site, path.lstrip("/")))
                else:
                    resolved = os.path.normpath(os.path.join(root, path))
                if not os.path.exists(resolved):
                    missing.append((os.path.relpath(page, site), target))

    for page, target in missing:
        print(f"MISSING: {target}  (from {page})")
    if missing:
        print(f"::error::{len(missing)} link(s) do not resolve in the assembled site")
        return 1
    print("All relative links resolve in the assembled site.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "_site"))
