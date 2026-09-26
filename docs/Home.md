---
title: TEMPLATE_LIBRARY_NAME
description: TEMPLATE_DESCRIPTION
---

# TEMPLATE_LIBRARY_NAME

> TEMPLATE_DESCRIPTION

!!! abstract "Welcome to your library's docs site"
    After `customizer.sh` rebrands this template, replace this placeholder
    with your library's overview — value proposition, hero diagram or
    screenshot, primary use case in 1-2 sentences.

## Quick start

```kotlin
// Replace this snippet with your library's smallest meaningful API.
// Goal: ≤ 10 lines of copy-paste-runnable code demonstrating the single
// most important capability.
```

## Where to go next

- [Getting started](getting-started/installation.md) — install + first usage
- [Features](features/) — one page per top-level capability
- [Platform support](platform-support/) — Android, iOS, Desktop, Web matrices

## Authoring docs

The site is built with [mkdocs-material](https://squidfunk.github.io/mkdocs-material/).
Authoring conventions live in:

- [`DEVELOPMENT-TEMPLATE.md`](DEVELOPMENT-TEMPLATE.md) — generic blueprint (sync'd from `mbl-library-template-kmp`)
- [`DEVELOPMENT.md`](DEVELOPMENT.md) — per-library extensions (owned by this repo)

```bash
pip install -r docs/requirements.txt
mkdocs serve   # → http://127.0.0.1:8000
```

!!! tip "Both `index.md` and `Home.md` exist on purpose"
    mkdocs uses `index.md` for the root URL; the GitHub Wiki uses `Home.md`
    for its landing page. Edit both with the same content in every commit.
