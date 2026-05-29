# Flux de données via un paquet anonymisé portable

Dataclone ne fait pas de tuyau direct prod → base locale, bien que les deux Oracle soient
joignables depuis la même machine (cf. ADR 0008, tout est self-hosted sur le PC du dev).
L'extraction produit un **paquet d'extraction** : un artefact portable, au **format neutre
structuré (JSON : lignes retenues + métadonnées d'ordre et de typage)**, **déjà anonymisé**, que
le loader **rejoue dans l'Oracle local**. Source et destination sont toutes deux Oracle.

## Pourquoi

- **Invariant de confidentialité : aucune PII en clair au repos.** La vraie PII ne fait que
  transiter **en mémoire** pendant l'extraction (le dev a déjà l'accès lecture-prod) ; elle
  n'atterrit jamais en clair sur disque, dans l'Oracle local ou dans le métastore. L'anonymisation
  a lieu **avant** toute écriture. Un tuyau direct écrirait potentiellement de la donnée non
  anonymisée et brouillerait cette garantie.
- **Format neutre rejoué par Dataclone** (plutôt que script SQL ou datapump) : le loader maîtrise
  l'ordre topologique, les cycles, les séquences et les contraintes différées (ADR 0006) ; le
  paquet reste **inspectable**, **archivable** avec le ticket d'incident, et **rejouable/partageable**.
- **Découple extraction et rejeu** : on peut rejouer ou partager un paquet sans refaire l'extraction.

## Conséquences

- Le paquet est un livrable manipulable (anonymisé) ; le partage se fait par fichier (pas de dépôt
  central — ADR 0008).
- Révision : une version antérieure justifiait le paquet par une exécution « côté serveur près de
  la prod » avec un dev sans accès prod. C'est faux : le dev a l'accès prod et tout est self-hosted
  (ADR 0008). La décision (paquet anonymisé portable) tient, mais pour les raisons ci-dessus.
