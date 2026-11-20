# TODO — Confee 1.20.1

Ce qui reste à faire avant et après la première publication. Cocher au fur et à mesure ; quand une décision est
prise, la reporter aussi dans `CONFEE_SPEC.md` (point en ✅ DÉCIDÉ, retiré du §18).

## 1. Tests à faire

- [x] Serveurs Forge et NeoForge pour de vrai (`eula=true` mis le 2026-11-19) : les deux démarrent (`Done`), Confee
      chargé côté serveur, aucune classe client.
- [x] Suggestions dans un monde chargé : le script de captures crée un monde plat de test ; biomes et tags lus dans
      le monde (64 biomes, 119 tags, identiques aux fichiers en vanilla). Reste : un datapack qui ajoute un biome.
- [x] Jar Forge de release sur une vraie installation NeoForge 1.20.1 (2026-11-19) : installateur officiel NeoForge
      47.1.106 (serveur `--installServer` + client `--installClient` isolé), seul `confee-0.1.0+1.20.1-forge.jar` dans
      `mods`. Serveur : `Done`, Confee chargé côté serveur. Client : écran Confee ouvert depuis la liste des mods,
      option modifiée + Enregistrer (une seule ligne change), bouton Config ajouté à NeoForge, `forge-client.toml`
      modifié en jeu (une seule ligne change), aucune erreur `[Confee]`.
- [x] Fabric sans Mod Menu (`-PnoModMenu`) : tous les écrans s'ouvrent via l'API, aucune erreur.
- [x] Cohabitation avec Configured 2.2.3 (2026-11-19) : Fabric (jar dans `fabric/run-shots/mods`) et NeoForge
      (`-PwithConfigured` dans `testenv/neoforge`, le jar de release doit être désobfusqué en dev ; Forge dev 47.1.3 <
      47.1.47 requis par Configured). Pas de crash, pas de bouton en double, aucun écran écrasé. Décision : Configured
      garde les mods à config Forge (Confee enregistre ses écrans en dernier et l'enveloppe seulement).
- [x] Vrais fichiers de config de `fabric/run/config` (dont ZombiesBreakBuild, Biome Replacer) : 19 fichiers, 7 formats,
      lus et réécrits à l'identique (`FormatCorpusTest`). Reste : édition en jeu à la main.
- [x] Tests unitaires du détecteur d'IDs (`RegistryDetectorTest`, registres simulés).

## 2. Décisions à prendre

Toutes tranchées par Lucas le 2026-11-19 et reportées dans `CONFEE_SPEC.md` (§18 vidé).

- [x] Commentaires écrits à la main dans les fichiers Confee : **conservés** (édition en place, commentaires générés mis à jour).
- [x] §18 : valeurs d'origine via la sauvegarde, configs SERVER en lecture seule, sauvegarde sur « Enregistrer »,
      widgets par code, Forge Config API Port sur Fabric, couleurs du thème : **validés tels quels**.
- [x] Clé inconnue dans un fichier Confee : **conservée**.
- [x] Éditeur universel : `config/<modid>/` **récursif sans limite**.
- [x] `Confee.registerExternalFile(modId, path)` : **validée**.
- [x] `fileMappings` en liste de chaînes `"modid=chemin"` : **validé**.
- [x] Écriture synchrone en V1 : **validée**.
- [x] Liste des annotations V1 et sous-sections `@Section` : **figées**.
- [x] Nouveau : `forceConfeeEditor` (remplacer l'écran de mods choisis par l'éditeur Confee, à chaud) — §8.1, §12.

## À coder suite aux décisions

- [x] `forceConfeeEditor` : Fabric (mixin Mod Menu) + Forge/NeoForge (factory enveloppée).
- [x] Recherche récursive dans `config/<modid>/` (dossiers des mods seulement, test unitaire).
- [x] Commentaires à la main conservés dans les fichiers Confee : marqueur `#:` / `//:` sur les commentaires générés,
      édition en place (`ConfeeFileMerger`, tests sur les 5 formats). Supprimer les anciens `confee_testmod*` de
      `run/config` (écrits avec `# `, ils seraient vus comme écrits à la main).

## 3. Publication

- [x] Lien du dépôt dans les métadonnées (`mod_source_url` dans `gradle.properties`).
- [x] Captures de référence Bedrock (`docs/ui-reference/`) : **retirées du dépôt** (2026-11-19), gardées en local et
      ignorées par Git ; historique réécrit pour les en retirer (force push le 2026-11-19).
- [x] GitHub (2026-11-19) : dépôt **public**, description, site (page CurseForge), topics, `CONTRIBUTING.md`,
      modèles d'issue et de PR, CI GitHub Actions (`./gradlew build`), protection de `1.20.1` (CI `build` + 1
      approbation pour les PR, pas de force push ; l'admin n'est pas bloqué).
- [x] Modrinth / CurseForge : projets créés (Modrinth `itTlrYl0`, CurseForge `1817213`, IDs dans `gradle.properties`).
      Tokens dans `.env` (lu par `scripts/confee.ps1`, voir `.env.example`).
- [x] Type de release : **`beta`** (validé le 2026-11-19).
- [x] Maven pour les devs : **plus tard** (V1.1, décidé le 2026-11-19). Modrinth Maven marche dès la publication :
      `maven.modrinth:confee:<version>`.
- [x] Pages des mods : remplies par Lucas sur Modrinth et CurseForge (2026-11-19), à partir de
      `docs/publishing/mod-page.md`.
- [x] Publication réelle de la **0.1.0 beta** (2026-11-19) : Modrinth (versions `hFtdOOSX` Fabric, `WjjCG70R`
      Forge), CurseForge (fichiers `9704565` Fabric, `9704564` Forge), release GitHub `0.1.0+1.20.1` (pre-release,
      les 2 jars). Reste : validation des projets par la modération Modrinth et CurseForge.
- [ ] ⚠️ Le jar **Forge 0.1.0** envoyé sur Modrinth (et sans doute CurseForge) n'est pas réobfusqué (upload lancé
      avant `reobfJar`) : il ne tourne pas hors environnement de dev. Corrigé pour la 0.2.0. À décider par Lucas :
      retirer ou archiver ce fichier 0.1.0.
- [x] Publication réelle de la **0.2.0 beta** (2026-11-20) : Modrinth (versions `2WUKWOYe` Fabric, `oYhWJGb1`
      Forge, jars vérifiés par hash), CurseForge (Fabric + Forge), release GitHub `0.2.0+1.20.1` (pre-release, les
      2 jars).
- [x] Port **1.20.2** publié en 0.2.0 beta (2026-11-20) : Modrinth (`2mUPUo4W` Fabric, `FfisYDpr` NeoForge,
      `B3CdSsU9` Forge, vérifiés par hash), CurseForge (3 jars), release GitHub `0.2.0+1.20.2`. Testé : build + tests,
      captures complètes sur Fabric, Quilt, Forge, NeoForge, et serveurs dédiés de production NeoForge 20.2.93 et
      Forge 48.1.0 avec les jars de release.
- [ ] Ports suivants (spec 4.1), dans l'ordre : `1.20.3-1.20.4`, `1.20.5-1.20.6`, `1.21-1.21.1`, `1.21.2-1.21.3`,
      `1.21.4`, `1.21.5`, `1.21.6-1.21.8`, `1.21.9-1.21.10`, `1.21.11`, `26.1-26.1.2`, `26.2`, `26.3`.

## 4. Limites connues (V1.1 / V2)

- [x] Widgets custom sur les fichiers bruts des autres mods (2026-11-19) : `ConfeeWidgets.fileId(modid, chemin)`
      (ajout à l'API, rien de cassé), testé en jeu (capture Fabric 69, `WeightWidget` du mod de test).
- [x] Listes d'objets dans un YAML d'un autre mod (2026-11-19) : éditables élément par élément.
- [x] Structures imbriquées (2026-11-19) : listes d'objets, listes de listes, listes mixtes, objets dans les éléments,
      à toute profondeur, en JSON/JSON5, YAML et TOML (tableaux en ligne et `[[x]]`). Un sous-écran par élément
      (fil d'Ariane, Retour), ▲ ▼ ✕ et « + Ajouter » (copie du dernier élément), en mémoire jusqu'à « Enregistrer ».
      Testé : unitaires (`NestedStructuresTest`, `ExternalConfigTest`), corpus réel, captures Fabric 60 à 68.
      Reste : ajouter un élément à une liste vide (pas de modèle à copier).
- [x] Couleurs stockées en entier (int) dans les fichiers bruts : pastille + color picker (nom de clé + plage).
- [x] HOCON (2026-11-19) : spec complète (chemins, concaténation, substitutions affichées en lecture seule avec leur
      valeur, `include` édité dans le fichier inclus, clés en double fusionnées), `HoconTest`, `ExternalConfigTest`,
      2 vrais fichiers (Croptopia, Server Browser), captures Fabric 70 à 73.
- [ ] V2 : synchro serveur → client, édition des configs serveur avec permissions, presets, migrations de version
      de config.

- [ ] Autres versions de Minecraft (une branche par version) et NeoForge 1.20.2 et plus.
