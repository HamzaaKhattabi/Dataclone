# Dataclone

Outil interne d'entreprise qui, lors d'un incident en production, reconstitue en local le jeu de
données impliqué : à partir de lignes graines, il rassemble les dépendances, anonymise la PII et
rejoue le tout dans une base Oracle locale. Cible : applications Oracle / Hibernate (JPA).

Vocabulaire du projet : voir `CONTEXT.md`. Décisions structurantes : voir `docs/adr/`.

## Agent skills

### Issue tracker

Les issues vivent dans **GitLab Issues** (CLI `glab`). See `docs/agents/issue-tracker.md`.

### Triage labels

Vocabulaire de labels **par défaut** (`needs-triage`, `needs-info`, `ready-for-agent`,
`ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Dépôt **mono-contexte** (`CONTEXT.md` + `docs/adr/` à la racine). See `docs/agents/domain.md`.

<!-- gitbook-agent-instructions:start -->

## GitBook Documentation Editing

This repository contains documentation synced with GitBook via Git Sync.

Before editing GitBook-synced Markdown, YAML, or asset files, make sure the GitBook skill is available and up to date in your local agent environment. Prefer installing or updating it with:

```bash
npx skills add gitbookio/gitbook-skills
```

This command may add or update local agent skill files. Use them only as local agent instructions; do not commit those installed skill files or any tool-generated agent configuration unless the user explicitly asks for it.

If `npx` is unavailable, load the skill from:

https://gitbook.com/docs/skill.md

When making changes, preserve GitBook sync metadata such as frontmatter, `SUMMARY.md`, `docs.yaml`, `.gitbook/`, and asset links unless the requested edit explicitly requires changing them.

<!-- gitbook-agent-instructions:end -->
