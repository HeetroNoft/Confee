# CLAUDE.md — Confee

## Le projet

Confee est un mod library Minecraft (Java) de gestion des configs de mods :
- **pour les devs** : déclarer une config (annotations ou builder) → fichier TOML + écran de config généré automatiquement (style Bedrock Edition, couleur d'accent personnalisable) ;
- **pour les joueurs** : éditeur universel qui ajoute un bouton Config aux mods qui n'en ont pas et permet de modifier leurs fichiers de config en jeu.

Loaders : Fabric, Quilt, Forge, NeoForge. Cible actuelle : **Minecraft 1.20.1**.

## Source de vérité

**`CONFEE_SPEC.md` est la référence.** Le lire avant toute tâche, relire la section concernée avant de coder une fonctionnalité.

Statuts dans la spec :
- ✅ **DÉCIDÉ** → appliquer tel quel, ne pas remettre en question.
- 🟡 **À VALIDER** → appliquer la proposition par défaut, sauf indication contraire de Lucas.
- ❓ **QUESTION OUVERTE** → **ne pas coder cette partie** avant d'avoir posé la question à Lucas.

Quand Lucas tranche un point, mettre à jour `CONFEE_SPEC.md` (passer le point en ✅ DÉCIDÉ et le retirer du §18).

## Règle n°1 : ne rien déduire

- Si quelque chose n'est pas dans la spec ou est ambigu : **poser la question**, ne pas inventer.
- Ne pas ajouter de fonctionnalité hors périmètre V1 (pas de synchro serveur → client, pas de presets, pas d'édition des configs serveur).
- Proposer plutôt qu'imposer : si une meilleure approche existe, l'expliquer et attendre la validation.

## Communication

- Parler à Lucas **en français**, ton direct et informel, réponses concises.
- Code, noms de classes, méthodes, variables, commits et Javadoc : **en anglais**.

## Contraintes techniques non négociables

- **Java uniquement** (pas de Kotlin). **Java 17** pour 1.20.1.
- **Forge 1.20.1 : compiler contre Forge 47.1.x** et ne pas utiliser d'API ajoutée dans Forge 47.3+ → le même jar doit tourner sur NeoForge 1.20.1 (fork de Forge 47.1).
- Le jar Fabric doit aussi tourner sur **Quilt**.
- **Aucune classe client côté serveur dédié** : jamais de référence à `Screen`, `Minecraft`, rendu, etc. dans du code chargé côté serveur.
- Le package `api/` est **public et stable**. Tout le reste est interne. Ne pas casser l'API sans le signaler.
- Aucune dépendance runtime obligatoire hors loader (+ Fabric API). Mod Menu est **optionnel**. Toute lib embarquée doit être **relocalisée**.
- **Ne jamais faire crasher le jeu** à cause d'un fichier de config : attraper, logger avec le préfixe `[Confee]`, dégrader proprement.
- Fichiers des **autres mods** : **édition chirurgicale uniquement** (on remplace seulement le texte des valeurs modifiées, tout le reste est recopié octet pour octet). Écriture atomique (tmp + rename) partout.
- Ne jamais écraser un écran de config qu'un mod fournit déjà.

## Dossier `reference/`

- Contient le code source d'autres mods (Configured, Mod Menu, YACL…) **pour comprendre leur fonctionnement uniquement**.
- **Interdiction de copier ou de paraphraser leur code.** Lire, comprendre, puis réécrire à partir de la spec.
- Ne jamais importer, compiler ou référencer `reference/` depuis le projet. Il est dans `.gitignore`.

## UI

- Style **Bedrock Edition** : captures de référence dans `docs/ui-reference/`, tokens et widgets décrits dans la spec §10.
- Widgets dessinés **par code** (pas de textures) pour que la couleur d'accent soit recolorable.
- Toujours respecter le GUI scale, la navigation clavier et le focus visible.
- Tout texte affiché passe par les fichiers de langue (`en_us.json` + `fr_fr.json`), jamais de texte en dur.

## Tests

- Chaque adaptateur de format doit avoir des tests unitaires :
  - ouvrir + enregistrer sans modification = **fichier identique octet pour octet** ;
  - modifier une valeur = **une seule valeur change** dans le diff.
- Toute nouvelle option/annotation doit être ajoutée au **mod de test** (`testmod`).
- Ne pas dire qu'une fonctionnalité marche sans l'avoir testée (build + test, ou lancement du jeu). Si ce n'est pas testable par toi, le dire clairement.

## Commandes

Le plus simple : `.\scripts\confee.ps1 <tâche>` (PowerShell) choisit le bon JDK et lance la tâche. `.\scripts\confee.ps1` sans argument liste les tâches (`fabric`, `forge`, `quilt`, `neoforge`, `shots-fabric`, `shots-forge`, `shots-quilt`, `shots-neoforge`, `shots-all`, `test`, `build`, `jars`…). Si PowerShell bloque les scripts : `powershell -ExecutionPolicy Bypass -File .\scripts\confee.ps1 <tâche>`.

Gradle 8.8 doit tourner sur un JDK 17 ou 21. Le `~/.gradle/gradle.properties` de Lucas force un JDK 25 (non supporté par Gradle 8.8), donc ajouter à chaque commande :
`-Dorg.gradle.java.home=C:/Users/lucas/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`

- Build complet + tests : `./gradlew build`
- Tests unitaires seuls : `./gradlew :common:test`
- Test sur de vrais fichiers de tous les formats (lecture seule) : `CONFEE_CORPUS=<dossier> [CONFEE_CORPUS_EXT=json,yaml] ./gradlew :common:cleanTest :common:test --tests "*FormatCorpusTest"` (résultat dans `common/build/test-results/test/TEST-*FormatCorpusTest.xml`)
- Client : `./gradlew :fabric:runClient` / `./gradlew :forge:runClient` (dossier `run/`)
- Serveur dédié : `./gradlew :fabric:runServer` / `./gradlew :forge:runServer` (dossier `run-server/`)
- Avec les mods de test (`src/testmod` = utilise Confee, `src/rawmod` = sans Confee ni écran pour l'éditeur universel ; jamais publiés) : `:fabric:runTestmodClient`, `:fabric:runTestmodServer`, `:forge:runTestmodClient`, `:forge:runTestmodServer`. Les fichiers d'exemple de `common/src/testmod/fixtures/config/` sont copiés dans `run/config/` sans écraser l'existant (supprimer le fichier pour repartir de l'original).
- Captures automatiques de l'UI (Fabric, 1280×720, en français) : `:fabric:runTestmodScreenshots` → `fabric/run-shots/screenshots/confee-*.png`, le jeu se ferme tout seul. Le script est dans `common/src/testmod/.../ScreenshotScript.java`.
- Quilt et NeoForge (dev uniquement) : builds séparés dans `testenv/quilt` (Quilt Loom, lance le jar Fabric de release) et `testenv/neoforge` (NeoGradle 6, propre wrapper Gradle 8.1) ; passer par `confee.ps1` (`quilt`, `neoforge`, `shots-quilt`, `shots-neoforge`, `*-server`). `shots-all` = captures sur les 4 loaders.
- Jars : `fabric/build/libs/confee-<version>+1.20.1-fabric.jar`, `forge/build/libs/confee-<version>+1.20.1-forge.jar`

## Workflow

1. Lire la section de la spec concernée.
2. Si un point est ❓ ou ambigu → demander avant de coder.
3. Annoncer brièvement le plan pour les tâches de plus de quelques fichiers.
4. Coder petit, compiler souvent, tester.
5. Résumer à la fin : ce qui a été fait, ce qui a été testé, ce qui reste ouvert.
6. Ne commit / push que si Lucas le demande.

## Infos projet

- Package : `fr.heetstudio.confee`. Licence : LGPL-3.0. Auteur affiché : Heet.
- Git : **une branche par version MC** (`1.20.1`, …), pas de `main`. Pas de Stonecutter.
- Commits : **Conventional Commits** en anglais (`feat(ui): ...`, `fix(fabric): ...`, `docs: ...`, `build: ...`, `test: ...`), découpés par domaine (scope = module ou paquet : `core`, `format`, `api`, `ui`, `external`, `fabric`, `forge`…).
- Pas de remote GitHub configuré pour l'instant : ne pas pousser sans que Lucas donne le dépôt.
