# Contributing to PrevHam

Thanks for considering a contribution. This guide covers the local workflow and the conventions
this repository expects — see [`CLAUDE.md`](CLAUDE.md) for the authoritative, full detail on every
convention below.

## Project overview

Start with [`docs/architecture.md`](docs/architecture.md) for how the `runtime`/`compiler`/`sample`/
`build-logic` modules fit together before making changes — most contributions touch either `runtime`
(the public `@Prev` annotation) or `compiler` (the KSP processor and mock generators).

## Local setup

```bash
git clone https://github.com/parkjiminnnn/PrevHam.git
cd PrevHam
```

Requires a JDK compatible with the project's configured Kotlin/AGP toolchain (see
[`build-logic`](build-logic)) and Android SDK command-line tools available to Gradle.

### Build and test

```bash
./gradlew build            # full build across all modules
./gradlew :runtime:test    # runtime module unit tests
./gradlew :compiler:test   # compiler module unit tests
./gradlew :sample:kspDebugKotlin       # run KSP only, to inspect generated Preview files
./gradlew :sample:compileDebugKotlin   # verify generated Preview code actually compiles
```

Generated Preview files land under `sample/build/generated/ksp/debug/kotlin/...` — when working on
`compiler`, inspecting that output directly is the fastest way to confirm a change behaves as
expected, rather than reasoning about KSP behavior in the abstract.

### Lint

```bash
./gradlew ktlintCheck    # verify formatting
./gradlew ktlintFormat   # auto-fix formatting
```

CI enforces `ktlintCheck`; run it (or `ktlintFormat`) before opening a PR.

## Workflow

1. **Start from an issue.** Every change should map to a GitHub issue. If one doesn't exist yet for
   what you want to do, open one first using the matching template in
   [`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE).
2. **Branch from the issue**, not a plain `git checkout -b`:
   ```bash
   gh issue develop <issue-number> --base develop --name <type>-<issue-number>-<description> --checkout
   ```
   This repo's default branch is `main`, but active development happens on `develop` — `--base develop`
   avoids branching off the stale default. `--name` keeps the branch on the `<type>-<issueNumber>-<description>`
   convention the PR title/body automation depends on. See [`CLAUDE.md`](CLAUDE.md#issue-convention) for why
   both flags matter.
3. **One feature per branch, one responsibility per PR.** Don't bundle unrelated changes.
4. **Commit using the convention**: `<type>: <description>` (`feat`, `fix`, `docs`, `refactor`, `test`,
   `ci`, `build`, `chore`). See [`CLAUDE.md`](CLAUDE.md#commit-convention).
5. **Keep `README.md` in sync.** Per [`CLAUDE.md`](CLAUDE.md#documentation-convention), any change to
   functionality, module responsibilities, the compile-time flow, or roadmap status must update the
   corresponding `README.md` section in the same change.
6. **Open a PR against `develop`** using the `[Type] Title` format and the Summary/Related
   Issue/Changes template described in [`CLAUDE.md`](CLAUDE.md#pr-convention). Include `Closes #<issue-number>`.

## Adding a new mock generator

If you're extending what types PrevHam can mock, see
[`docs/extending-mock-generators.md`](docs/extending-mock-generators.md) for how the
`MockGenerator`/`MockGeneratorRegistry` pipeline is structured and where a new generator needs to be
registered.

## Releasing

Releases are automated: bumping `VERSION_NAME` in `gradle.properties` to a non-`SNAPSHOT` version
and merging to `main` publishes to Maven Central, tags the commit, and creates a GitHub Release.
Ordinary merges to `main` do nothing. See [`docs/release.md`](docs/release.md).
