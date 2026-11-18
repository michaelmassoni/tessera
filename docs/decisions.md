# Decision log

Newest first. Each entry records the date, the decision, the reason and its status. `Proposed`
entries wait for the maintainer's sign-off. Agreed changes to the founding decisions are recorded
here.

## 2026-11-19: material3 pinned to 1.5.0-rc01 (Proposed)

**Decision:** Override the Compose BOM (2026.09.00, which ships material3 1.4.0) with
material3 `1.5.0-rc01`. Every other Compose artifact stays on the stable BOM (Compose 1.12.1).

**Reason:** In material3 1.4.0, the newest stable release (Sep 2025), `MaterialExpressiveTheme`,
`MotionScheme.expressive()` and `ExperimentalMaterial3ExpressiveApi` are `internal`, so the
founding decision "Material 3 Expressive" can't be built on stable. 1.5.0-rc01 (published
2026-11-04) is a release candidate. Its declared dependencies are the stable Compose 1.12.0
libraries.

**Consequences:** Expressive APIs still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
Keep those opt-ins inside `core:design` wherever possible, so that API changes are absorbed in
one module. Move to 1.5.0 stable as soon as it's released.

## 2026-11-19: Optional Nix dev shell (Accepted)

**Decision:** Add `flake.nix` as an optional local toolchain. The build itself doesn't depend
on Nix: CI and F-Droid use a standard JDK and Android SDK.

**Reason:** The maintainer's machine runs NixOS, where AGP's downloaded aapt2 can't run and
the SDK can't be installed imperatively.

## 2026-11-19: Founding decisions (Accepted)

Name Tessera, GPL-3.0, a fresh Compose codebase (Fossify used as a feature checklist only),
Material 3 Expressive with dynamic color, Kotlin + Compose + Gradle KTS with a version catalog,
minSdk 31 and target/compile SDK 37, a monorepo with `core:*` and `apps/*`, no proprietary
dependencies or Play Services, F-Droid/IzzyOnDroid/GitHub Releases first. CLAUDE.md lists the
build order.

Build details added to support these decisions:

- build-tools pinned to 37.0.0, because AGP 9.5 otherwise picks 36.0.0. Pinning also helps
  reproducible builds.
- `dependenciesInfo` disabled in every app. F-Droid rejects the Google-encrypted dependency
  blob.
