# Anonymisation : IA stratège + générateur déterministe exécutant, LLM on-prem

L'anonymisation s'appuie sur un **petit LLM on-prem** qui **détecte automatiquement** les
colonnes contenant de la PII et choisit, **par colonne**, une stratégie d'anonymisation. La
substitution en masse est ensuite appliquée par un **générateur déterministe** (type faker)
via un **dictionnaire de substitution stable** : une même valeur source devient toujours la même
valeur fausse (cohérence référentielle), conforme aux contraintes de la colonne. L'IA n'est donc
sollicitée qu'~une fois par colonne, pas par valeur. Un **rapport d'anonymisation** est joint au
paquet.

## Pourquoi

- **LLM on-prem obligatoire** : envoyer la vraie PII à une API externe contredirait l'objectif de
  confidentialité (le canal d'anonymisation deviendrait un canal d'exfiltration).
- **IA stratège, pas moulinette** : appeler un LLM par valeur serait lent, coûteux, et
  multiplierait l'exposition des données ; classer par colonne puis générer en masse est rapide
  et cohérent. Un petit modèle on-prem est de toute façon trop faible pour générer fidèlement des
  milliers de valeurs.
- **Dictionnaire stable** : sans lui, une même personne porterait des noms différents selon les
  tables/lignes, cassant jointures et lisibilité de l'incident reproduit.

## Conséquences

- ⚠ La détection auto sans validation humaine peut **rater une colonne PII** (faux négatif) →
  fuite. Mitigation actuelle : le **rapport d'anonymisation** rend le résultat auditable avant
  partage. Une étape « l'IA propose, l'humain valide une fois par appli » reste une évolution
  prévue (« détection auto pour l'instant »).
