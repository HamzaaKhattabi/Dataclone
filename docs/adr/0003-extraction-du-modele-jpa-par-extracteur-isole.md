# Lecture du modèle JPA via un extracteur isolé

Dataclone tourne en Java 26 / Spring Boot (Hibernate 6 / `jakarta`), mais les applications
cibles sont en Hibernate 5.2.9 / `javax` avec du bytecode ancien et des `persistence.xml`. Pour
lire leur modèle JPA, on n'utilise **ni reflection dans la JVM de Dataclone, ni analyse statique
du bytecode** : on délègue à un **extracteur isolé** qui s'exécute avec le classpath legacy
(reconstruit depuis le WAR cible : `WEB-INF/classes` + `WEB-INF/lib/*.jar`), bootstrappe le
`Metadata` Hibernate de la cible, et **sérialise le graphe de relations normalisé en JSON**.
Dataclone ne consomme que ce JSON.

## Pourquoi

- Charger les classes legacy dans la JVM de Dataclone est condamné par le conflit
  `javax`/`jakarta` et Hibernate 5 vs 6.
- L'analyse statique (ASM) obligerait à réimplémenter la sémantique JPA — `mappedBy`, héritage,
  clés composites, et surtout les **noms de colonnes implicites** (naming strategy) — que seul
  Hibernate calcule correctement. La présence de `persistence.xml` (et possiblement de mappings
  XML) rend cette réimplémentation encore moins viable.
- Le WAR étant auto-contenu, il fournit lui-même le classpath nécessaire au bootstrap Hibernate.

## Conséquences

- Une brique supplémentaire (process/classloader isolé) à orchestrer, avec un contrat JSON
  stable entre l'extracteur et Dataclone.
- ⚠ Risque à vérifier sur l'appli réelle : si Hibernate est **fourni par le serveur**
  d'application (WebLogic/WebSphere) et **absent de `WEB-INF/lib`**, l'extracteur devra se voir
  fournir Hibernate 5.2.9 séparément.
