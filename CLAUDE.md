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
- Follow the Roadmap defined in the project README.

## Build / Test Commands

## Documentation Convention

Whenever work in this project changes or adds functionality, features, module responsibilities, architecture, or roadmap status, `README.md` must be updated in the same change to stay in sync.

This applies to (non-exhaustive):

- New or changed features → update the **Features** table.
- Module additions, removals, or responsibility changes → update **Project Structure** and **Architecture**.
- Changes to the compile-time flow (KSP processing steps, codegen pipeline) → update **How It Works**, including the Mermaid diagram if the flow changed.
- Progress on a roadmap item → check it off (or add it) in **Roadmap**.
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

