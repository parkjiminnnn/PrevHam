# CLAUDE.md

## Project Overview

PrevHam is a compile-time Android library that automatically generates **Jetpack Compose Preview functions and mock data** using Kotlin Symbol Processing (KSP).

The primary goal is to eliminate repetitive Preview boilerplate by allowing developers to annotate Composable functions with `@Prev`.

During compilation, PrevHam analyzes the annotated Composable function and generates Preview code with appropriate mock objects for supported parameter types.

All Preview and mock generation must happen at compile time.

The library must not rely on runtime reflection.

---

## Architecture

The project consists of the following modules:

```text
PrevHam
│
├── sample/
├── compiler/
├── runtime/
├── gradle-plugin/
├── build-logic/
├── docs/
└── .github/
```

### runtime

**Responsibilities**

- Provides the `@Prev` annotation.
- Contains only the public APIs required by library users.

**Constraints**

- Must not contain any code generation logic.
- Should have minimal dependencies.
- Should remain lightweight and stable.

---

### compiler

**Responsibilities**

- Implements the KSP `SymbolProcessor`.
- Analyzes the `@Prev` annotation.
- Generates Preview source code using KotlinPoet.
- Generates mock data for supported parameter types.

**Constraints**

- All code generation must happen at compile time.
- Runtime reflection is strictly prohibited.
- Generated code should be deterministic and reproducible.
- Code generation should be modular and easily extensible.

---

### sample

**Responsibilities**

- Demonstrates how to use PrevHam.
- Serves as a verification project for generated Preview code.
- Demonstrates supported mock generation features.
- Acts as a reference implementation for library users.

---

### gradle-plugin

**Responsibilities**

- Provides the `io.github.parkjiminnnn.prevham` Gradle plugin.
- Declares the `runtime`, `compiler` and MockK dependencies on the consumer's behalf, at the plugin's own version.

**Constraints**

- Must not apply KSP. A KSP version is tied to a Kotlin version, so applying it would pin the consumer's Kotlin version to PrevHam's; the KSP Gradle plugin is `compileOnly` for the same reason.
- Must fail with an actionable message when KSP or a Kotlin plugin is missing.
- Behaviour is verified with Gradle TestKit, against real builds.

---

### build-logic

**Responsibilities**

- Manages shared Gradle configuration.
- Provides Convention Plugins.
- Centralizes build configuration across modules.

---

### docs

**Responsibilities**

- Contains architecture documentation.
- Documents KSP implementation details.
- Documents the mock generation architecture.
- Provides release and publishing guides.

---

### .github

**Responsibilities**

- Stores GitHub Actions workflows.
- Contains Issue and Pull Request templates.
- Manages CI/CD configuration.

---

## Development Principles

- Generate all Preview and mock code at compile time.
- Never use runtime reflection.
- Use KotlinPoet for all generated Kotlin source.
- Use KSP APIs instead of reflection whenever possible.
- Follow SOLID principles, especially the Single Responsibility Principle (SRP).
- Keep the architecture modular and extensible.
- Separate code generation responsibilities into dedicated components.
- Implement one feature per branch and one responsibility per Pull Request.
- Follow the priorities tracked in GitHub Issues.

## Build / Test Commands

## Documentation Convention

Whenever work in this project changes or adds functionality, features, module responsibilities, or architecture, `README.md` and any affected files under `docs/` must be updated in the same change to stay in sync.

This applies to (non-exhaustive):

- New or changed features → update the **Features** table.
- Module additions, removals, or responsibility changes → update **Project Structure** and **Architecture**.
- Changes to the compile-time flow (KSP processing steps, codegen pipeline) → update **How It Works**, including the Mermaid diagram if the flow changed.
- New setup/config steps required to use the library → update **Quick Start**.
- Dependency or tooling changes (Kotlin, KSP, AGP, etc.) → update **Tech Stack** and version badges.

Treat a code change as incomplete until the corresponding `README.md` sections reflect it. If a change has no user- or contributor-visible effect (e.g. internal refactor with no behavior change), no README update is required — but call that out explicitly rather than skipping silently.

## Commit Convention

Use the following commit message format.

```

<type>: <description>

```

Supported types:

- feat

- fix

- docs

- refactor

- test

- ci

- build

- chore

Example:

```

feat: add AutoPreview annotation

fix: resolve preview generation issue

ci: add GitHub Actions workflow

docs: update README

```

## Issue Convention

When creating a GitHub Issue, always follow these rules:

- **Labels**: automatically apply the label matching the issue type inferred from the title prefix, using the repository's existing labels: `[Feat]` → `feat`, `[Fix]` → `bug`, `[Docs]` → `docs`, `[CI]` → `ci`, `[Build]` → `build`, `[Test]` → `test`, `[Refactor]` → `refactor`, `[Release]` → `release`.
- **Assignee**: assign the issue to the repository owner (`--assignee @me`).
- Use the structure of the matching template in `.github/ISSUE_TEMPLATE/` (Summary / Motivation / Proposal / Tasks for a feature issue).
- **Branch creation**: when starting work on an issue, create the branch with:

  ```
  gh issue develop <issue-number> --base develop --name <type>-<issue-number>-<description> --checkout
  ```

  instead of a plain `git checkout -b`. This links the branch to the issue's Development section immediately, independent of which branch the eventual PR targets (see the Development note under [PR Rules](#pr-rules) for why this matters in this repo).

  Both flags are required, not optional:
  - `--base develop`: this repo's default branch (`main`) is far behind `develop` (all feature work happens on `develop`; `main` only advances on release). Without `--base develop`, the command silently branches off the stale default branch instead.
  - `--name <type>-<issue-number>-<description>`: without it, the branch is named `<issue-number>-<type>-<slug>` (e.g. `18-feat-generate-mocks-...`), which doesn't match the `type-issueNumber-description` convention the PR title/body automation depends on.

## PR Convention

### PR Title

The PR title must follow the format below.

```
[Type] Title
```

Examples

```
[Feat] Set up project
[Fix] Resolve preview generation issue
[Docs] Update README
[CI] Add GitHub Actions workflow
```

If the branch name follows the convention below:

```
feat-1-set-up-project
fix-12-preview-generation
docs-3-update-readme
ci-5-add-github-actions
```

Automatically infer the following:

- Branch prefix → PR type
- Issue number → Related issue number

Example

Branch

```
feat-1-set-up-project
```

Generated PR title

```
[Feat] Set up project
```

---

### PR Body

Always use the following template.

```markdown
## Summary

<!-- Briefly describe the purpose of this pull request. -->

## Related Issue

Closes #<issue-number>

## Changes

- Change 1
- Change 2
```

The issue number should be automatically extracted from the branch name.

Example

Branch

```
feat-12-add-auto-preview
```

Generated PR title

```
[Feat] Add Auto Preview
```

Generated PR body

```markdown
## Summary

Add the initial AutoPreview annotation and complete the project setup.

## Related Issue

Closes #12

## Changes

- Add AutoPreview annotation
- Implement the annotation module
- Set up the initial project structure
```

---

### PR Rules

When creating a Pull Request, always follow these rules:

- Use the `[Type] Title` format for the PR title.
- Automatically extract the issue number from the branch name and include `Closes #<issue-number>`.
- Describe the purpose of the PR in the **Summary** section.
- List the actual implementation details as bullet points in the **Changes** section.
- Write both the PR title and body in **English**.
- Assume the branch name follows the `type-issueNumber-description` convention.
- **Labels**: automatically apply the label matching the PR type, using the same mapping as [Issue Convention](#issue-convention) (`feat` branch → `feat` label, `fix` → `bug`, `docs` → `docs`, `ci` → `ci`, `build` → `build`, `test` → `test`, `refactor` → `refactor`).
- **Assignee**: assign the PR to the repository owner (`--assignee @me`).
- **Development**: this repository's default branch is `main`, but feature branches are merged into `develop`. GitHub only auto-links a PR to an issue's Development section (and auto-closes it on merge) via `Closes #<issue-number>` when the PR targets the *default* branch — so a `feature → develop` PR does **not** get linked that way, even though the text is still correct and useful to keep. This is why branches must be created via the `gh issue develop` command described under [Issue Convention](#issue-convention): it links the branch to the issue's Development section regardless of the PR's target branch.

