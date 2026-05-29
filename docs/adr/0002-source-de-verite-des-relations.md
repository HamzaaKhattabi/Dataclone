# Source de vérité des relations : le modèle JPA prime sur la base

Dataclone considère le **modèle JPA** (annotations lues dans les JARs) comme la source de
vérité primaire du graphe de relations, et les **métadonnées Oracle** comme source
complémentaire. Combinaison : si un JAR est fourni, on lit le modèle JPA puis on enrichit avec
la base si elle est accessible (union) ; si aucun JAR n'est fourni, on s'appuie uniquement sur
la base ; si ni JAR ni base ne sont disponibles, l'extraction est bloquée et l'utilisateur est
invité à fournir plus d'informations.

## Pourquoi

Les applications legacy cibles ne déclarent pas toujours leurs clés étrangères en base : des
relations réelles n'existent que dans le mapping ORM (catégorie « `@JoinColumn` sans contrainte
FK »). Faire confiance à la base seule raterait ces relations. À l'inverse, la base reste
indispensable pour les types, la nullabilité, les séquences et l'ordre d'insertion, et peut
révéler des FK qu'aucun mapping JPA ne déclare (signalées comme avertissements).

## Conséquences

- Au moins une source est requise ; aucune des deux → arrêt explicite, jamais d'extraction « à l'aveugle ».
- Contre-intuitif à maintenir : ici l'**ORM fait autorité sur la base**, pas l'inverse. C'est délibéré.
- La catégorie « relation connue ni de la base ni de JPA, résolue uniquement en code » est
  considérée **hors périmètre** (confirmée absente des applis cibles actuelles).
