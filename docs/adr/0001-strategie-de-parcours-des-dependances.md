# Stratégie de parcours des dépendances

À partir des lignes graines, Dataclone remonte **toujours et transitivement** les parents
(FK sortantes / `@ManyToOne`) car ils sont obligatoires pour l'intégrité référentielle, mais ne
descend dans les **enfants** (`@OneToMany`) que pour les types explicitement inclus dans le
périmètre d'extraction. Surtout, un parent remonté est traité comme une **feuille** : on ne
redescend jamais dans ses *autres* enfants (règle « anti-remontée »).

## Pourquoi

Sans la règle anti-remontée, une seule ligne graine aspire toute la base en quelques sauts :
graine → parent `CLIENT` → toutes les autres commandes du client → leurs voisins → etc. Le
modèle de données cible est dense et plein de cycles ; seul un parcours dirigé et borné le rend
exploitable.

Le périmètre des enfants s'exprime **par relation (arête du graphe)**, pas par type d'entité :
sinon on ne pourrait pas inclure un même type enfant (ex. `ADRESSE`) dans un contexte parent
(`CLIENT`) et pas dans un autre (`FOURNISSEUR`).

## Conséquences

- Une extraction est garantie **insérable** (tous les parents obligatoires sont présents) mais
  **partielle** côté enfants — c'est voulu.
- En cas de dépassement d'un garde-fou (plafond de lignes, profondeur, cycle), l'extraction
  **s'arrête et produit un rapport** de ce qui manque, plutôt que de tronquer silencieusement.
- ⚠ Piège pour la maintenance : « ne pas ramener les autres enfants d'un parent remonté » est
  **délibéré**, pas un bug. Le « corriger » rouvrirait l'explosion combinatoire.
