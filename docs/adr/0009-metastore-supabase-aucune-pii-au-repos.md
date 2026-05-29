# Métastore Supabase, aucune PII au repos

Dataclone tient un **métastore** propre (Supabase/Postgres, embarqué dans le déploiement Docker
local), distinct des Oracle source et destination. Il contient uniquement des données **non
sensibles** liées à l'outil : configs par application (colonnes PII détectées/validées, presets
de périmètre), historique des extractions, registre des artefacts. Invariant strict : **aucune
donnée de prod n'est jamais persistée** — en particulier le **dictionnaire de substitution**
(valeur réelle → valeur fausse) reste **éphémère**, en mémoire le temps de l'anonymisation.

## Pourquoi

- Persister le dictionnaire de substitution stockerait de la vraie PII au repos et recréerait
  exactement la fuite que l'anonymisation vise à empêcher.
- Un métastore dédié sépare proprement l'état de l'outil des bases métier (source/destination).

## Conséquences

- ⚠ Le dictionnaire étant éphémère, une anonymisation **n'est pas reproductible à l'identique**
  d'une extraction à l'autre (une même personne peut recevoir un faux nom différent dans deux
  paquets distincts). C'est un compromis assumé en faveur de la confidentialité ; la cohérence est
  garantie **à l'intérieur** d'un paquet, pas entre paquets.
- Choix de Supabase comme métastore (Postgres) : assumé pour l'outil, sans lock-in métier (les
  données métier restent dans Oracle).
