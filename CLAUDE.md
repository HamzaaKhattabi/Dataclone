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
