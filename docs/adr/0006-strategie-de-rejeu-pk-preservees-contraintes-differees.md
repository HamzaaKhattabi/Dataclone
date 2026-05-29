# Stratégie de rejeu : PK d'origine préservées, contraintes différées

Le rejeu d'un paquet réinsère chaque ligne avec sa **clé primaire d'origine** (les PK sont des
séquences surrogates, donc préservables sans risque de collision dans une destination vide), dans
un **ordre topologique** parents → enfants. Pour absorber les **cycles** du schéma legacy, les FK
sont provisionnées en **`DEFERRABLE INITIALLY DEFERRED`** et tout le rejeu se fait dans une
**transaction unique à contraintes différées**. Après le load, les **séquences sont recalées**
au-delà du max inséré.

## Pourquoi

- Régénérer les PK casserait tous les FK internes du sous-graphe extrait.
- Un tri topologique seul échoue sur les cycles (auto-références, FK mutuelles), fréquents dans
  les schémas legacy ; les contraintes différées les résolvent proprement sans réécriture en deux
  passes.
- Recaler les séquences permet à l'appli locale de continuer à créer des lignes après le rejeu.

## Conséquences

- ⚠ Écart **délibéré** avec le DDL de prod : les FK de la destination sont `DEFERRABLE`. C'est un
  choix au service du rejeu, pas un oubli.
- Pas de gestion de triggers d'insert nécessaire sur l'appli cible actuelle (aucun trigger
  d'insert) ; à reconsidérer si une future cible en a.
