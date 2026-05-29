# Issue tracker: GitHub

Issues and PRDs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --comments`, filtering comments by `jq` and also fetching labels.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

Infer the repo from `git remote -v` — `gh` does this automatically when run inside a clone.

## `gh` PATH caveat (Windows)

`gh` est installé dans `C:\Program Files\GitHub CLI\gh.exe`. Selon le shell, ce dossier
n'est **pas toujours dans le `PATH`** (notamment les shells non interactifs). Si `gh ...`
échoue avec « command not found / introuvable », appelle le binaire par son chemin complet :
`& "C:\Program Files\GitHub CLI\gh.exe" ...` (PowerShell) ou
`"/c/Program Files/GitHub CLI/gh.exe" ...` (bash).

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Run `gh issue view <number> --comments`.
