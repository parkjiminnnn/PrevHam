# Releasing

PrevHam publishes two artifacts to Maven Central:

- `io.github.parkjiminnnn:prevham-runtime` — the `@Prev` annotation
- `io.github.parkjiminnnn:prevham-compiler` — the KSP processor

Releases are automated by [`.github/workflows/release.yml`](../.github/workflows/release.yml). This
document covers how to cut one and how the automation decides what to do.

## Cutting a release

1. Open a PR that changes `VERSION_NAME` in `gradle.properties` from `X.Y.Z-SNAPSHOT` to `X.Y.Z`,
   and merge it to `develop`.
2. Merge `develop` into `main`.
3. That's it. The workflow runs the tests, publishes to Maven Central, creates the `vX.Y.Z` tag,
   and opens a GitHub Release with generated notes.
4. Open a follow-up PR bumping `VERSION_NAME` to the next `-SNAPSHOT` (e.g. `X.Y.(Z+1)-SNAPSHOT`)
   so ongoing work isn't labelled as the released version.

**The version bump PR is the release decision**, and the only place a human decides anything.
Review it accordingly: once it reaches `main`, publication is automatic and Maven Central artifacts
can never be deleted or overwritten.

> **Merge `develop` into `main` with a merge commit, not a squash.** Release notes are built by
> mapping the commits in the release range back to their pull requests. A squash collapses every
> feature PR into a single commit, so the notes would list only the `develop` → `main` PR instead of
> the work it contains. Squashing individual feature PRs into `develop` is fine — it's this last hop
> that has to preserve history.

## How the workflow decides

The workflow triggers on every push to `main`, but a release only happens under specific conditions.
The `check` job reads `VERSION_NAME` from `gradle.properties` and:

| Version in `gradle.properties` | Tag `vX.Y.Z` exists? | Outcome |
|---|---|---|
| `1.0.0-SNAPSHOT` | — | Skipped — nothing to release |
| `1.0.0` | no | **Released** |
| `1.0.0` | yes | Skipped — already released |
| *(missing)* | — | Workflow fails |

So ordinary `develop` → `main` merges cost nothing: the `check` job exits in seconds without even
installing a JDK. Only a version bump triggers the real work.

## What runs on a release

1. **Tests** — the full suite, as a last gate before an irreversible publish
2. **Publish** — `./gradlew publish`, which builds, signs, uploads, and releases the deployment
3. **Tag + GitHub Release** — created only *after* a successful publish, so a failed run leaves no
   dangling tag to clean up. Notes are generated from the PRs merged since the previous release and
   grouped by label (Features, Bug Fixes, Documentation, ...) per
   [`.github/release.yml`](../.github/release.yml), so the `[Type] Title` PR convention carries
   straight through to the changelog

Sonatype independently validates every deployment (GPG signatures, POM completeness, required
sources/javadoc artifacts) and refuses to publish one that fails. A malformed release therefore
fails at Sonatype rather than becoming public.

## Credentials

The workflow needs five repository secrets (`Settings` → `Secrets and variables` → `Actions`):

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Central Portal user token username |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype Central Portal user token password |
| `SIGNING_IN_MEMORY_KEY` | GPG private key, ASCII-armored and flattened to one line |
| `SIGNING_IN_MEMORY_KEY_ID` | Last 8 characters of the GPG key fingerprint |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | GPG key passphrase |

Gradle reads `ORG_GRADLE_PROJECT_`-prefixed environment variables as Gradle properties, so the
publishing convention plugin needs no CI-specific configuration — the same
[`prevham.publishing`](../build-logic/src/main/kotlin/prevham.publishing.gradle.kts) config works
locally and in CI.

### Preparing the signing key

CI cannot use a local keyring, so the in-memory key format is required. Export it, strip the
armor header/footer and all line breaks, and copy the result:

```bash
gpg --export-secret-keys --armor <KEY_ID> > ~/private-key.asc
awk 'NR>1 && !/^=/ && !/^-----END/' ~/private-key.asc | tr -d '\n' | pbcopy
rm ~/private-key.asc
```

Never write these values into the project's `gradle.properties` — it is tracked by git. For local
publishing, put them in `~/.gradle/gradle.properties` instead, which lives outside the repository.

Use a **separate Sonatype token for CI** from the one used locally. CI has a wider exposure surface
(workflow logs, third-party actions), and separate tokens mean a leak can be revoked without
breaking local publishing.

## Publishing locally

Rarely needed, but useful for verifying configuration changes:

```bash
# Build and inspect artifacts without touching the network
./gradlew publishToMavenLocal -PVERSION_NAME=1.0.0-SNAPSHOT

# Real upload; requires credentials in ~/.gradle/gradle.properties
./gradlew publish -PVERSION_NAME=1.0.0
```

Signing is only enforced for non-`SNAPSHOT` versions, so `publishToMavenLocal` with a snapshot
version works without any GPG configuration.

## Verifying a release

- Deployment status: https://central.sonatype.com/publishing/deployments
- Published artifacts: https://central.sonatype.com/namespace/io.github.parkjiminnnn

Artifacts typically take several minutes to appear on Maven Central after publication, and longer
to show up in search indexes.
