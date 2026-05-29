# Dataclone

Outil interne d'entreprise qui, lors d'un incident en production, reconstitue en local
le jeu de données impliqué : à partir d'une ou plusieurs lignes « graines », il rassemble
les données qui en dépendent et les rejoue dans une base de destination vide, en anonymisant
les données sensibles. Cible initiale : applications **Oracle** dont les relations sont
décrites par **JPA**.

## Langage

**Base source** :
La base de production (Oracle) d'où proviennent la structure et les données à reproduire.
On la lit, on ne la modifie jamais.

**Base de destination** :
La base locale (Oracle, ex. XE/conteneur), **vide au départ**, que Dataclone provisionne puis
remplit. Reçoit la **structure complète** de l'application source, mais seulement un
**sous-ensemble** de ses données. Source et destination sont toutes deux Oracle.

**Paquet d'extraction** :
L'artefact portable et **déjà anonymisé** produit par une extraction : les lignes retenues plus
les métadonnées nécessaires à leur réinsertion (ordre, typage). Ne contient aucune donnée
sensible en clair. Se télécharge, se rejoue en local, s'archive avec le ticket d'incident.

**Rejeu** :
L'opération de chargement d'un paquet d'extraction dans la base de destination. Réinsère les
lignes **avec leur clé primaire d'origine**, dans le bon ordre, contraintes différées (voir ADR 0006).

**Schéma** :
La structure complète d'**une** application : l'ensemble des objets (tables, contraintes,
index) d'un *user* Oracle. Quand on parle de « schéma », on parle de toute la structure de
l'application touchée, pas seulement des tables impliquées dans l'incident.

**Provisionnement de structure** (verbe : *cloner*) :
Opération de *setup* qui recrée le **schéma complet** de la base source dans la base de
destination vide, à partir du **DDL réel extrait d'Oracle** (pas du DDL généré par Hibernate).
Faite une fois (ou quand la structure de prod évolue). Ne copie aucune donnée.
_Le mot « cloner » est réservé à la **structure**. Ne jamais dire « cloner une ligne »._

**Image de structure** :
L'artefact contenant le **DDL complet** de l'application (FK rendues `DEFERRABLE`), produit une
fois et réutilisé pour provisionner la base locale avant d'y rejouer des paquets. Régénéré quand
la structure de prod évolue.

**Extraction** (verbe : *extraire ; extraire un cas*) :
Opération **principale et répétée** : à partir des lignes graines, rassembler les lignes qui
en dépendent et les insérer dans la base de destination (avec anonymisation). C'est le cœur du produit.
_Avoid_: cloner les données, copier (pour cette opération on dit *extraire*).

**Ligne graine** (*seed*) :
La ou les lignes de départ d'une extraction — les données directement identifiées comme
impliquées dans l'incident. Désignées par **clé primaire sur une seule entité connue** (une ou
plusieurs PK de cette table). Point de départ du parcours des dépendances.

**Dépendance** :
Relation entre deux lignes qui oblige l'une à accompagner l'autre. Orientée : une dépendance
**sortante** mène à un **parent**, une dépendance **entrante** mène à un **enfant**.

**Parent** :
Ligne *référencée* par une autre (FK sortante, `@ManyToOne`, côté propriétaire de `@OneToOne`).
**Toujours rapatriée**, transitivement : sans elle l'insertion violerait l'intégrité référentielle.

**Enfant** :
Ligne qui *référence* la graine (FK entrante, `@OneToMany`). Rapatriée **uniquement si son type
est inclus dans le périmètre d'extraction**. Un parent qu'on a remonté n'est jamais traité comme
point de départ pour ramener ses enfants (règle anti-remontée — voir ADR 0001).

**Périmètre d'extraction** :
L'ensemble des **relations enfants (arêtes)** que l'utilisateur choisit d'inclure pour une
extraction — exprimé **par relation**, pas par type d'entité (pour distinguer `CLIENT → ADRESSE`
de `FOURNISSEUR → ADRESSE`). « Tout » = toutes les relations enfants en cascade depuis la graine
(bornées par les garde-fous de l'ADR 0001). Mémorisé en **preset par application** dans le métastore.

### Sources des relations

**Modèle JPA** :
Les relations et le mapping déclarés par les annotations Hibernate/JPA dans les **JARs** de
l'application cible (`@ManyToOne`, `@OneToMany`, `@JoinColumn`…). **Source primaire** du graphe
de relations, car il reflète l'intention applicative même quand la base ne déclare pas la contrainte.

**Métadonnées de base** :
Les informations lues dans le dictionnaire Oracle (contraintes FK, types de colonnes, nullabilité,
séquences, unicité). **Source complémentaire** : complète ce que le modèle JPA ne porte pas et
permet d'insérer dans le bon ordre. Peut révéler des FK absentes du modèle JPA (signalées à l'utilisateur).

_Règle de combinaison : voir ADR 0002._

### Anonymisation

**Donnée sensible** (PII) :
Toute donnée permettant d'identifier une personne (nom, email, téléphone, adresse, date de
naissance, identifiant…). Les colonnes sensibles sont **détectées automatiquement** par un petit
LLM on-prem.

**Anonymisation** :
Le remplacement des données sensibles par des valeurs fausses mais réalistes et conformes aux
contraintes de colonne, effectué **à l'export, côté prod**, avant que le paquet ne sorte. Le LLM
joue le **stratège** (classer les colonnes, choisir la stratégie) ; un générateur déterministe
applique la substitution en masse.

**Dictionnaire de substitution** :
La correspondance « valeur réelle → valeur fausse » d'une extraction, **stable sur tout le
paquet** : une même valeur source devient toujours la même valeur anonymisée, ce qui préserve la
cohérence référentielle (jointures, copies dénormalisées).

**Rapport d'anonymisation** :
Récapitulatif joint au paquet : quelles colonnes ont été jugées sensibles et avec quelle
stratégie. Auditable avant partage (filet contre les faux négatifs de détection).

### Outil lui-même

**Métastore** :
La base propre à Dataclone (Supabase/Postgres, embarquée dans le déploiement), distincte des
Oracle source et destination. Contient les **configs par application** (colonnes PII, presets de
périmètre), l'**historique des extractions** et le **registre des artefacts**. Ne contient
**jamais** de donnée de prod ; en particulier le **dictionnaire de substitution reste éphémère**
(en mémoire le temps de l'anonymisation, jamais écrit).
