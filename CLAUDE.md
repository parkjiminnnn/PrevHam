# CLAUDE.md

## Project Overview

## Build / Test Commands

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

