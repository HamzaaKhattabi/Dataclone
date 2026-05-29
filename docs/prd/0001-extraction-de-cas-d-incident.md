# PRD — Dataclone : extraction de cas d'incident pour reproduction locale

> Label visé : `ready-for-agent`. À publier sur le tracker une fois celui-ci configuré
> (`/setup-matt-pocock-skills`). Vocabulaire : voir `CONTEXT.md`. Décisions : voir `docs/adr/`.

## Problem Statement

En tant que développeur, quand un incident survient en production sur une application Oracle/JPA
legacy, je dois reproduire le scénario sur ma machine pour le déboguer. Mais les données
impliquées sont disséminées dans un schéma dense, plein de cycles, dont les relations ne sont
**pas toujours déclarées comme clés étrangères** en base (elles ne vivent parfois que dans le
mapping JPA). Rassembler à la main la *ligne graine* fautive **et toutes ses dépendances**,
anonymiser les données personnelles, puis recharger le tout dans une base locale cohérente est
long, fastidieux et source d'erreurs — et expose des données de prod sensibles.

## Solution

En tant que développeur, j'utilise **Dataclone**, une application self-hostée sur mon poste. Je
désigne une *ligne graine* (une entité connue + sa clé primaire), je choisis un *périmètre*
d'extraction (les relations enfants que je veux suivre), et Dataclone :

1. construit le *graphe de relations* à partir du *modèle JPA* (lu dans le WAR) enrichi des
   *métadonnées de base* Oracle ;
2. parcourt les dépendances (parents toujours, enfants selon le périmètre, sans remontée) et
   s'arrête avec un *rapport* si un garde-fou est dépassé ;
3. **anonymise** les données personnelles via un LLM on-prem ;
4. produit un *paquet d'extraction* portable et déjà anonymisé ;
5. me permet de **provisionner** une base Oracle locale vide (depuis le DDL réel de la prod) et d'y
   **rejouer** le paquet, clés primaires préservées.

Je reproduis ainsi l'incident en local, sans jamais écrire de donnée sensible en clair au repos.

## User Stories

### Provisionnement de structure
1. En tant que dev, je veux extraire le *DDL réel* du schéma complet de l'application source, afin
   d'obtenir une *image de structure* fidèle à la prod.
2. En tant que dev, je veux que les clés étrangères de l'image de structure soient transformées en
   `DEFERRABLE INITIALLY DEFERRED`, afin de pouvoir rejouer des données cycliques.
3. En tant que dev, je veux provisionner ma base Oracle locale vide à partir de l'image de
   structure, afin d'avoir des tables prêtes à recevoir des extractions.
4. En tant que dev, je veux régénérer l'image de structure quand la structure de prod évolue, afin
   de rester aligné avec la source.
5. En tant que dev, je veux réutiliser une même image de structure pour rejouer plusieurs paquets,
   afin de ne pas reprovisionner à chaque incident.

### Lecture du modèle et des métadonnées
6. En tant que dev, je veux que Dataclone lise le *modèle JPA* directement depuis le WAR de
   l'application cible, afin de connaître les relations même non déclarées en base.
7. En tant que dev, je veux que la lecture du modèle JPA s'appuie sur le Hibernate de la cible
   (extracteur isolé), afin que `mappedBy`, l'héritage, les clés composites et les noms de colonnes
   implicites soient résolus correctement.
8. En tant que dev, je veux que Dataclone lise aussi les *métadonnées de base* Oracle (contraintes,
   types, nullabilité, séquences, unicité), afin de compléter le modèle JPA et d'insérer dans le
   bon ordre.
9. En tant que dev, je veux que le modèle JPA prime sur la base en cas de divergence, afin de
   refléter l'intention applicative.
10. En tant que dev, je veux être averti des clés étrangères présentes en base mais absentes du
    modèle JPA (catégorie 3), afin de ne pas rater de dépendance.
11. En tant que dev, si aucune source de relations (ni WAR ni base) n'est disponible, je veux que
    Dataclone bloque et me réclame plus d'informations, afin de ne jamais extraire à l'aveugle.

### Désignation de la graine et périmètre
12. En tant que dev, je veux désigner une *ligne graine* par son entité et sa clé primaire, afin de
    partir du point exact de l'incident.
13. En tant que dev, je veux désigner plusieurs PK de la même entité graine, afin de traiter un
    petit groupe de lignes de départ.
14. En tant que dev, je veux choisir le *périmètre* d'extraction **par relation (arête)**, afin
    d'inclure par exemple `CLIENT → ADRESSE` sans inclure `FOURNISSEUR → ADRESSE`.
15. En tant que dev, je veux un raccourci « tout » qui inclut toutes les relations enfants en
    cascade depuis la graine, afin d'extraire largement quand je ne sais pas encore ce qui compte.
16. En tant que dev, je veux enregistrer un *preset* de périmètre par application, afin de réutiliser
    mes choix d'une extraction à l'autre.

### Parcours des dépendances
17. En tant que dev, je veux que tous les *parents* obligatoires soient rapatriés transitivement,
    afin que les insertions ne violent jamais l'intégrité référentielle.
18. En tant que dev, je veux que seuls les *enfants* du périmètre soient rapatriés, afin de ne pas
    aspirer toute la base.
19. En tant que dev, je veux qu'un parent remonté soit traité comme une feuille (anti-remontée),
    afin qu'extraire une commande ne ramène pas tout l'historique de son client.
20. En tant que dev, je veux que les cycles du graphe soient détectés et gérés, afin que le parcours
    se termine toujours.
21. En tant que dev, je veux des garde-fous (plafond de lignes par table, plafond global,
    profondeur max), afin d'éviter une explosion combinatoire.
22. En tant que dev, quand un garde-fou est atteint, je veux un *rapport d'arrêt* listant ce qui
    manque, afin de décider quoi faire — plutôt qu'une troncature silencieuse.

### Anonymisation
23. En tant que dev, je veux que les colonnes contenant de la PII soient détectées automatiquement
    par le LLM on-prem, afin de ne pas les déclarer manuellement.
24. En tant que dev, je veux que chaque colonne PII reçoive une stratégie d'anonymisation adaptée
    (nom, email, téléphone, adresse, date de naissance, identifiant…), afin d'obtenir des valeurs
    fausses réalistes.
25. En tant que dev, je veux qu'une même valeur source soit toujours remplacée par la même valeur
    fausse dans tout le paquet (*dictionnaire de substitution* stable), afin que les jointures et
    copies dénormalisées restent cohérentes.
26. En tant que dev, je veux que les valeurs anonymisées respectent les contraintes de colonne
    (longueur, type, unicité, format), afin que le rejeu n'échoue pas.
27. En tant que dev, je veux que le LLM ne soit sollicité qu'~une fois par colonne (et non par
    valeur), afin que l'anonymisation reste rapide et limite l'exposition des données.
28. En tant que dev, je veux un *rapport d'anonymisation* joint au paquet (colonnes jugées
    sensibles + stratégie), afin de l'auditer avant tout partage.
29. En tant que responsable, je veux la garantie qu'aucune PII n'est jamais persistée en clair
    (dictionnaire éphémère, métastore sans donnée de prod), afin de respecter la confidentialité.

### Paquet et rejeu
30. En tant que dev, je veux que l'extraction produise un *paquet* au format neutre (JSON : lignes
    + métadonnées d'ordre/typage), afin qu'il soit portable, inspectable et indépendant de la
    version d'Oracle locale.
31. En tant que dev, je veux télécharger le paquet, afin de l'archiver avec le ticket d'incident ou
    de le partager par fichier.
32. En tant que dev, je veux rejouer un paquet dans ma base locale, afin de recréer le cas
    d'incident.
33. En tant que dev, je veux que le rejeu préserve les clés primaires d'origine, afin que les FK
    internes du sous-graphe restent valides.
34. En tant que dev, je veux que le rejeu insère dans l'ordre topologique avec contraintes
    différées, afin que les cycles ne bloquent pas le chargement.
35. En tant que dev, je veux que les séquences soient recalées après le rejeu, afin que
    l'application locale puisse continuer à créer des lignes.

### Métastore et historique
36. En tant que dev, je veux que mes configs par application (colonnes PII, presets de périmètre)
    soient persistées dans le *métastore*, afin de ne pas les ressaisir.
37. En tant que dev, je veux consulter l'historique de mes extractions, afin de retrouver et rejouer
    un cas passé.
38. En tant que dev, je veux un registre des artefacts (images de structure, paquets) avec leurs
    versions, afin de savoir lequel correspond à quel schéma.

## Implementation Decisions

Tous les choix structurants sont consignés dans `docs/adr/0001` à `0009`. Synthèse côté modules.

**Cœurs profonds (logique pure, port pour les I/O) :**

- **Graphe de relations** (ADR 0002) — `construire(modèleJpa, métadonnéesBase) → GrapheRelations`,
  émet les avertissements catégorie 3. Arêtes typées parent/enfant, nullabilité, ordre.
- **Moteur de parcours / subsetting** (ADR 0001) —
  `planifier(graine, périmètre, graphe, accèsLignes: Port) → PlanExtraction | RapportArrêt`.
  Implémente : parents transitifs obligatoires (R1), enfants par arête du périmètre, anti-remontée
  (R2), détection de cycles, garde-fous (plafonds + profondeur), rapport d'arrêt. L'accès aux
  lignes est un **port** injecté (récupération par critère/FK), jamais un appel DB direct.
- **Moteur de substitution** (ADR 0005) —
  `anonymiser(lignes, stratégiesParColonne, contraintes) → (lignesAnonymisées, rapport)`.
  Déterministe, dictionnaire de substitution **en mémoire et éphémère**, valeurs conformes aux
  contraintes.
- **Ordonnanceur d'insertion** (ADR 0006) — `ordonner(graphe, lignes) → PlanInsertion`.
  Tri topologique parents → enfants, cycles résolus via contraintes différées.
- **Transformateur DDL** (ADR 0007) — `versImageDeStructure(ddl) → ImageStructure` (FK →
  `DEFERRABLE INITIALLY DEFERRED`).
- **Sérialiseur de paquet** (ADR 0004) — `écrire(extraction) → Paquet`, `lire(Paquet) → extraction`
  (round-trip stable, format neutre JSON).

**Adaptateurs (interface simple, I/O caché) :**

- **Extracteur de modèle JPA** (ADR 0003) — `extraire(cheminWar) → ModèleJpaJson`. Classloader
  isolé reconstruit depuis `WEB-INF/classes` + `WEB-INF/lib/*.jar`, bootstrap du `Metadata`
  Hibernate de la cible, sérialisation du graphe normalisé en JSON. Contrat JSON stable entre
  l'extracteur et Dataclone.
- **Lecteur de métadonnées Oracle** — `lireMétadonnées(conn, schéma) → MétadonnéesBase`,
  `extraireDdl(conn, schéma) → Ddl` (`DBMS_METADATA.GET_DDL`).
- **Classifieur PII** (stratège LLM, ADR 0005) — `classer(colonnes) → stratégiesParColonne`. Port
  LLM on-prem ; n'envoie que des métadonnées de colonnes (pas le jeu de données en masse).
- **Exécuteur de rejeu** (ADR 0006) — applique le `PlanInsertion` : transaction unique, contraintes
  différées, PK préservées, recalage des séquences.
- **Provisionneur** (ADR 0007) — applique l'`ImageStructure` à l'Oracle local.
- **Métastore** (ADR 0009) — repositories sur Supabase/Postgres. Invariant : aucune donnée de prod.

**Glue / présentation :** backend Spring Boot (Java 26) orchestrant les modules ; UI TanStack Start
pour configurer/déclencher l'export et piloter provisionnement + rejeu.

**Topologie** (ADR 0008) : application unique self-hostée sur le PC du dev (image Docker locale),
lisant l'Oracle de prod (accès dev existant) et écrivant l'Oracle local ; pas de serveur central.

**Schéma du métastore (Supabase/Postgres)** — données non sensibles uniquement :
- `application` : applications cibles connues.
- `config_pii` : colonnes détectées/validées comme PII et leur stratégie, par application.
- `preset_perimetre` : ensembles d'arêtes enfants mémorisés, par application.
- `extraction` : historique (graine, périmètre, statut, rapport d'arrêt éventuel, horodatage).
- `artefact` : registre des images de structure et des paquets, avec versions et liens vers les
  fichiers. (Aucune ligne de donnée métier, aucun dictionnaire de substitution.)

**Contrat JSON extracteur → Dataclone** : liste d'entités avec, par entité, la (les) table(s)
mappée(s) (héritage inclus), la clé primaire, et la liste des associations orientées (type
`@ManyToOne`/`@OneToMany`/`@ManyToMany`, colonnes de jointure, côté propriétaire, nullabilité).

## Testing Decisions

**Ce qu'est un bon test ici** : il vérifie le **comportement externe** d'un module via son
interface publique, jamais ses détails internes. Les cœurs profonds étant **purs** (entrées →
sorties, dépendances I/O derrière des ports), on les teste sans base ni LLM réels, avec des
**fakes** pour les ports (faux `accèsLignes`, faux LLM). Un test décrit un scénario métier (« une
commande remonte son client mais pas les autres commandes du client ») et asserte sur le résultat,
pas sur la façon dont il est calculé.

**Modules couverts (choix : tous les cœurs purs) :**

- **Moteur de parcours** — cas : parents transitifs rapatriés ; enfant hors périmètre ignoré ;
  anti-remontée (un parent remonté ne ramène pas ses autres enfants) ; cycle terminé proprement ;
  garde-fou dépassé → rapport d'arrêt avec ce qui manque ; arête M2M dans le périmètre.
- **Moteur de substitution** — cohérence (même source → même valeur fausse partout) ; respect des
  contraintes (longueur/type/unicité/format) ; rapport d'anonymisation correct ; déterminisme
  intra-paquet.
- **Graphe de relations** — fusion JPA + base ; primauté du modèle JPA ; émission d'avertissement
  catégorie 3 ; nullabilité/types correctement portés.
- **Ordonnanceur d'insertion** — ordre topologique parents → enfants ; gestion des cycles ;
  auto-références.
- **Transformateur DDL** — FK transformées en `DEFERRABLE`, reste du DDL inchangé.
- **Sérialiseur de paquet** — round-trip `écrire` puis `lire` préserve lignes + métadonnées.

**Prior art** : projet greenfield, aucun test existant. À l'établissement de la base de tests,
fixer le patron de référence (tests de comportement par module pur + fakes de ports) que les
adaptateurs réutiliseront en intégration ultérieurement.

## Out of Scope

- **Serveur central / dépôt d'artefacts partagé** et **authentification / contrôle d'accès** (ADR
  0008 : self-hosted, partage par fichier).
- **Tuyau direct prod → local** (ADR 0004 : on passe par le paquet anonymisé).
- **Bases non-Oracle** en source comme en destination (les deux sont Oracle).
- **Graine sur plusieurs entités différentes** simultanément (une seule entité graine).
- **Recherche full-base** de la graine (le dev part d'une entité connue).
- **Validation humaine** de la détection PII (aujourd'hui automatique — évolution prévue, ADR 0005).
- **Stabilité inter-paquets** de l'anonymisation (dictionnaire éphémère, cohérence intra-paquet
  seulement — ADR 0009).
- **Détails fins de parcours** non encore tranchés : sémantique complète des `@ManyToMany` / tables
  d'association, et spécificités du rejeu pour l'héritage JPA (`JOINED` / `SINGLE_TABLE`).
- **Génération de DDL par Hibernate** (`hbm2ddl`) : on utilise le DDL réel (ADR 0007).
- **Tests des adaptateurs** (Extracteur JPA, Métastore, Exécuteur de rejeu, etc.) : hors de ce lot,
  à couvrir en intégration plus tard.

## Further Notes

- **Risque à lever (ADR 0003)** : vérifier que Hibernate 5.2.9 est bien présent dans
  `WEB-INF/lib` du WAR cible. Si Hibernate est fourni par un serveur d'application
  (WebLogic/WebSphere), l'extracteur isolé devra se voir fournir Hibernate séparément. À confirmer
  en inspectant le WAR réel.
- **Contraintes legacy** : cible actuelle = une application connue, Oracle, Hibernate 5.2.9.Final
  (`javax.persistence`), PK = séquences surrogates, pas de trigger d'insert, `persistence.xml`
  présents.
- **Conflit de versions** : Dataclone tourne en Java 26 / Hibernate 6 / `jakarta` ; la cible est en
  Hibernate 5 / `javax`. D'où l'extracteur isolé (ADR 0003), nécessaire même en exécution locale.
- **Points reportés à une prochaine session de cadrage** : seuils concrets des garde-fous, choix du
  runtime LLM (p. ex. Ollama), traitement des colonnes LOB/binaires dans les paquets.
