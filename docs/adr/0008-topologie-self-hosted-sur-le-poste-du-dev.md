# Topologie : self-hosted sur le poste du dev, pas de serveur central

Dataclone est une **application unique self-hostée sur le PC du dev** (à terme une image
déployée dans un Docker local), composée d'une UI **TanStack Start**, d'un backend **Spring Boot
(Java 26)**, de l'**extracteur isolé** (modèle JPA depuis le WAR, ADR 0003), du **LLM
d'anonymisation** local et d'un **métastore Supabase** embarqué (ADR 0009). Depuis cette même
machine, l'outil **lit l'Oracle de prod** (le dev y a accès) et **écrit l'Oracle local**. Il n'y
a **aucun serveur central** ni dépôt d'artefacts partagé : chaque dev héberge tout, et le partage
d'un paquet se fait en joignant le fichier (p. ex. à un ticket).

## Pourquoi

- Les devs disposent déjà d'un accès lecture-prod ; faire tourner l'extraction localement évite
  toute infrastructure partagée à provisionner et maintenir.
- La confidentialité est assurée non pas par l'isolement réseau mais par l'invariant « aucune PII
  en clair au repos » (ADR 0004, ADR 0009).

## Conséquences

- Une seule application, mais qui orchestre plusieurs briques (UI, backend, extracteur isolé,
  LLM, Supabase) → packaging Docker Compose local visé.
- Pas de centralisation : pas de catalogue partagé d'images de structure / paquets, pas d'auth
  (hors périmètre). Le partage est manuel, par fichier.
- L'extracteur isolé (ADR 0003) reste nécessaire malgré l'exécution locale : le conflit
  `javax`/`jakarta` + Hibernate 5 vs 6 existe au sein même de la JVM locale de Dataclone.
