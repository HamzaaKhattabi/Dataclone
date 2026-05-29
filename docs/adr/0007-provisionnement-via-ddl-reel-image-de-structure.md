# Provisionnement via DDL réel Oracle, image de structure séparée

La base de destination est provisionnée à partir du **DDL réel extrait de la prod**
(`DBMS_METADATA.GET_DDL`), pas d'un DDL généré par Hibernate (`hbm2ddl`). Ce DDL complet est
matérialisé en une **image de structure** réutilisable (FK transformées en `DEFERRABLE`,
cf. ADR 0006), produite une fois et régénérée quand la prod évolue. Le provisionnement est
**séparé** du rejeu : on provisionne la base locale une fois, puis on y rejoue autant de paquets
(données seules) que nécessaire.

## Pourquoi

- La prémisse du projet est que la base legacy **dévie** du modèle JPA ; un DDL généré par
  Hibernate produirait un schéma « propre » mais **différent** de la prod, donc inutilisable pour
  reproduire fidèlement un incident. Hibernate sert à lire les **relations**, pas à générer la
  **structure**.
- Le schéma complet est volumineux et stable : le réembarquer dans chaque paquet serait du
  gaspillage. D'où l'image de structure séparée et des paquets légers.

## Conséquences

- La source Oracle doit être accessible pour produire l'image de structure (elle l'est toujours à
  l'extraction). Le cas « JARs seuls sans DB » de l'ADR 0002 ne concerne que la lecture des
  relations, jamais le provisionnement.
- Deux artefacts distincts à gérer : l'**image de structure** (rare, volumineuse) et les
  **paquets d'extraction** (fréquents, légers).
