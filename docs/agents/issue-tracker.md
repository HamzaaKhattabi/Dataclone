# Issue tracker: GitLab

Issues and PRDs for this repo live as GitLab issues. Use the `glab` CLI for all operations.

## Conventions

- **Create an issue**: `glab issue create --title "..." --description "..."`. Use a file (`--description-file`) for multi-line bodies.
- **Read an issue**: `glab issue view <number> --comments`.
- **List issues**: `glab issue list --opened --output json` (filter further with `jq`); add `--label "..."` to scope.
- **Comment on an issue**: `glab issue note <number> --message "..."`
- **Apply / remove labels**: `glab issue update <number> --label "..."` / `--unlabel "..."`
- **Close**: `glab issue close <number>` (commenter au préalable avec `glab issue note`).

Infer the project from `git remote -v` — `glab` does this automatically when run inside a clone.

## `glab` PATH caveat (Windows)

`glab` est installé dans `C:\Users\<user>\AppData\Local\Programs\glab\glab.exe`. Selon le shell,
ce dossier n'est **pas toujours dans le `PATH`** (notamment les shells non interactifs lancés
avant l'install). Si `glab ...` échoue avec « command not found / introuvable », appelle le binaire
par son chemin complet ou préfixe `$env:Path` :
`$env:Path = "C:\Users\<user>\AppData\Local\Programs\glab;$env:Path"` (PowerShell).

## When a skill says "publish to the issue tracker"

Create a GitLab issue.

## When a skill says "fetch the relevant ticket"

Run `glab issue view <number> --comments`.

