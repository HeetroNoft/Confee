# TODO — Confee 1.20.1

Ce qui reste à faire avant et après la première publication. Cocher au fur et à mesure ; quand une décision est
prise, la reporter aussi dans `CONFEE_SPEC.md` (point en ✅ DÉCIDÉ, retiré du §18).

## 1. Tests à faire

- [x] Serveurs Forge et NeoForge pour de vrai (`eula=true` mis le 2026-11-19) : les deux démarrent (`Done`), Confee
      chargé côté serveur, aucune classe client.
- [x] Suggestions dans un monde chargé : le script de captures crée un monde plat de test ; biomes et tags lus dans
      le monde (64 biomes, 119 tags, identiques aux fichiers en vanilla). Reste : un datapack qui ajoute un biome.
- [ ] Jar Forge de release sur une vraie installation NeoForge 1.20.1 (le test actuel recompile les sources contre
      NeoForge, ce n'est pas le jar de release).
- [x] Fabric sans Mod Menu (`-PnoModMenu`) : tous les écrans s'ouvrent via l'API, aucune erreur.
- [ ] Cohabitation avec Configured (Mod Menu, Cloth Config et YACL déjà vérifiés). Configured (MrCrayfish) n'est que
      sur CurseForge : déposer son jar dans `fabric/run/mods` / `forge/run/mods` puis lancer le jeu.
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

- [x] Lien du dépôt dans les métadonnées (`mod_source_url` dans `gradle.properties`). Le dépôt est privé : les liens
      ne marcheront pour les joueurs qu'une fois le dépôt public.
- [ ] Captures de référence Bedrock (`docs/ui-reference/`) : les garder si le dépôt devient public ?
- [ ] Modrinth / CurseForge : créer les projets, vérifier les slugs, fournir les tokens (variables d'environnement).
- [ ] Type de release : `beta` par défaut, à confirmer.
- [ ] Maven pour les devs : Modrinth Maven, ou un Maven dédié (❓) ?
- [ ] Pages des mods : brouillon dans `docs/publishing/mod-page.md` relu le 2026-11-19 (ligne sur l'écran des mods
      mise à jour pour `forceConfeeEditor`) ; reste la relecture de Lucas.
- [ ] Publication réelle (`CONFEE_PUBLISH=true`). La simulation `publish-dry` passe pour les 4 envois.

## 4. Limites connues (V1.1 / V2)

- [ ] Widgets custom sur les fichiers bruts des autres mods.
- [ ] Listes d'objets dans un YAML d'un autre mod (lecture seule aujourd'hui).
- [ ] Structures imbriquées : liste de listes, objet dans un objet, map dont les valeurs sont des objets.
- [x] Couleurs stockées en entier (int) dans les fichiers bruts : pastille + color picker (nom de clé + plage).
- [ ] HOCON (prévu en V1.1).
- [ ] Autres versions de Minecraft (une branche par version) et NeoForge 1.20.2 et plus.
- [ ] V2 : synchro serveur → client, édition des configs serveur avec permissions, presets, migrations de version
      de config.
