# TODO — Confee 1.20.1

Ce qui reste à faire avant et après la première publication. Cocher au fur et à mesure ; quand une décision est
prise, la reporter aussi dans `CONFEE_SPEC.md` (point en ✅ DÉCIDÉ, retiré du §18).

## 1. Tests à faire

- [ ] Serveurs Forge et NeoForge pour de vrai : mettre `eula=true` dans `forge/run-server/eula.txt` et
      `testenv/neoforge/run-server/eula.txt` (sans ça, ils s'arrêtent avant de charger les mods).
- [ ] Suggestions dans un monde chargé : biomes, structures et tags venant du monde et des datapacks (seul le cas
      hors partie est testé).
- [ ] Jar Forge de release sur une vraie installation NeoForge 1.20.1 (le test actuel recompile les sources contre
      NeoForge, ce n'est pas le jar de release).
- [ ] Fabric sans Mod Menu : les écrans restent ouvrables via l'API, sans erreur.
- [ ] Cohabitation avec Configured (Mod Menu, Cloth Config et YACL déjà vérifiés).
- [ ] Vrais mods tiers dans `fabric/run/mods/` : l'éditeur universel lit bien leurs fichiers.
- [ ] Tests unitaires du détecteur d'IDs (`RegistryDetector`), vérifié seulement par les captures.

## 2. Décisions à prendre

- [ ] Commentaires écrits à la main dans les fichiers Confee : perdus à l'enregistrement. Les conserver ?
- [ ] §18 de la spec, implémentés selon la proposition par défaut, à confirmer puis passer en ✅ :
  - [ ] valeurs « d'origine » via la sauvegarde pour les fichiers bruts ;
  - [ ] configs SERVER Forge en lecture seule ;
  - [ ] sauvegarde en attente du bouton « Enregistrer » ;
  - [ ] widgets dessinés par code ;
  - [ ] Forge Config API Port sur Fabric ;
  - [ ] valeurs des couleurs du thème (relevées à l'œil).
- [ ] Clé inconnue dans un fichier Confee : conservée telle quelle, ou supprimée ?
- [ ] Éditeur universel : chercher dans `config/<modid>/` sur un seul niveau, ou de façon récursive ?
- [ ] API `Confee.registerExternalFile(modId, path)` pour les devs tiers : on la fait ?
- [ ] Format de `fileMappings` : liste de chaînes `"modid=chemin"`.
- [ ] Écriture des fichiers synchrone en V1 : OK ?
- [ ] Liste finale des annotations V1 et des sous-sections `@Section`.

## 3. Publication

- [ ] Créer le dépôt GitHub (public ou privé) et pousser `1.20.1`.
- [ ] Lien du dépôt dans les métadonnées : `fabric.mod.json` (contact/sources) et `mods.toml` (displayURL).
- [ ] Captures de référence Bedrock (`docs/ui-reference/`) : les garder si le dépôt devient public ?
- [ ] Modrinth / CurseForge : créer les projets, vérifier les slugs, fournir les tokens (variables d'environnement).
- [ ] Type de release : `beta` par défaut, à confirmer.
- [ ] Maven pour les devs : Modrinth Maven, ou un Maven dédié (❓) ?
- [ ] Pages des mods : icône, captures (les `run-shots` peuvent servir), description.
- [ ] Publication réelle : `.\scripts\confee.ps1 publish-dry` d'abord, puis `CONFEE_PUBLISH=true`.

## 4. Limites connues (V1.1 / V2)

- [ ] Widgets custom sur les fichiers bruts des autres mods.
- [ ] Listes d'objets dans un YAML d'un autre mod (lecture seule aujourd'hui).
- [ ] Structures imbriquées : liste de listes, objet dans un objet, map dont les valeurs sont des objets.
- [ ] Couleurs stockées en entier (int) dans les fichiers bruts : pas de pastille ni de color picker.
- [ ] HOCON (prévu en V1.1).
- [ ] Autres versions de Minecraft (une branche par version) et NeoForge 1.20.2 et plus.
- [ ] V2 : synchro serveur → client, édition des configs serveur avec permissions, presets, migrations de version
      de config.
