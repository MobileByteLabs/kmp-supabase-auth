<!-- Paths are ROOT-ABSOLUTE (leading /) on purpose.
     `relativePath: true` in index.html resolves in-page links against the current page's
     directory — which is what makes the repo's own Markdown work unchanged. But the sidebar is
     rendered on EVERY page, so relative paths here break the moment you navigate into
     cmp-supabase-auth/: `TARGET_MATRIX.md` would resolve to /cmp-supabase-auth/TARGET_MATRIX.md.
     Absolute paths keep the sidebar correct from anywhere. -->

- Getting started
  - [Overview](/docs/home.md)
  - [Install &amp; wire up](/docs/getting-started.md)
  - [Target matrix](/TARGET_MATRIX.md)

- Provider setup
  - [Google Sign-In](/docs/SETUP_GOOGLE.md)
  - [Sign in with Apple](/docs/SETUP_APPLE.md)

- Modules
  - [cmp-supabase-auth](/cmp-supabase-auth/README.md)
  - [cmp-supabase-auth-compose](/cmp-supabase-auth-compose/README.md)

- Reference
  - [Architecture](/docs/architecture.md)
  - [API surface](/docs/api-reference.md)

- Project
  - [README](/README.md)
  - [Contributing](/CONTRIBUTING.md)
  - [cmp-supabase-auth dev](/cmp-supabase-auth/DEVELOPMENT.md)
  - [cmp-supabase-auth-compose dev](/cmp-supabase-auth-compose/DEVELOPMENT.md)
