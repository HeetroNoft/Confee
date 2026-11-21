# Confee — Spécification fonctionnelle et technique

> Document de référence destiné à l'IA (ou au dev) qui va développer Confee.
> Il décrit **ce que fait le mod, comment il doit se comporter et comment il doit être construit**.
>
> **Convention de lecture**
>
> - ✅ **DÉCIDÉ** : choix validé par Lucas (l'auteur). Ne pas remettre en question.
> - 🟡 **À VALIDER** : proposition par défaut. L'implémenter ainsi **sauf** si Lucas dit autrement. Ne pas inventer d'alternative sans lui demander.
> - ❓ **QUESTION OUVERTE** : ne pas trancher seul, demander à Lucas avant d'implémenter la partie concernée.
>
> Règle générale pour l'IA de dev : **ne rien déduire**. Si un point n'est pas couvert par ce document, poser la question.

---

## 0. Résumé en 10 lignes

Confee est un **mod library Minecraft (Java)** qui a deux rôles :

1. **Pour les développeurs de mods** : une API simple (annotations **et** builder) pour déclarer des configs. Confee gère le fichier (TOML par défaut), le chargement, la sauvegarde, la validation, et **génère automatiquement un écran de config** au style Bedrock Edition, dont la couleur d'accent est personnalisable par le dev.
2. **Pour les joueurs** : un **éditeur universel** qui permet de modifier en jeu les configs des mods **qui n'utilisent pas Confee**, en lisant directement leur fichier de config (TOML, JSON, YAML, properties, etc.) et, quand c'est possible, en s'appuyant sur le système de config Forge/NeoForge pour obtenir types, bornes et commentaires.

Loaders : Fabric, Quilt, Forge, NeoForge. Première cible : **Minecraft 1.20.1**. À terme : toutes les versions.

---

## 1. Identité du projet

| Élément                      | Valeur                                            | Statut                                                           |
| ---------------------------- | ------------------------------------------------- | ---------------------------------------------------------------- |
| Nom affiché                  | **Confee**                                        | ✅ DÉCIDÉ                                                        |
| Mod ID                       | `confee`                                          | ✅ DÉCIDÉ (vérifier dispo Modrinth/CurseForge avant publication) |
| Langage                      | **Java uniquement** (pas de Kotlin)               | ✅ DÉCIDÉ                                                        |
| Loaders                      | Fabric, Quilt, Forge, NeoForge                    | ✅ DÉCIDÉ                                                        |
| Version MC de départ         | **1.20.1**                                        | ✅ DÉCIDÉ                                                        |
| Versions MC à terme          | Toutes (architecture multi-version dès le départ) | ✅ DÉCIDÉ                                                        |
| Distribution                 | Modrinth + CurseForge                             | ✅ DÉCIDÉ                                                        |
| Format de fichier par défaut | TOML                                              | ✅ DÉCIDÉ                                                        |
| Langue des noms (code, API)  | Anglais                                           | ✅ DÉCIDÉ                                                        |
| Logo                         | Fait par Lucas, hors scope de ce document         | ✅ DÉCIDÉ                                                        |
| Group / package Java         | `fr.heetstudio.confee`                            | ✅ DÉCIDÉ                                                        |
| Licence                      | LGPL-3.0                                          | ✅ DÉCIDÉ                                                        |
| Dépôt Git                    | Une branche par version MC (`1.20.1`, …), pas de branche `main` | ✅ DÉCIDÉ                                          |
| Auteur affiché               | Heet                                              | ✅ DÉCIDÉ                                                        |

---

## 2. Périmètre par version

### V1 (ce qui doit être développé maintenant)

- API dev par **annotations** + API dev par **builder**, qui produisent le même modèle interne.
- Lecture / écriture / validation des configs Confee en **TOML** (format par défaut). Le dev peut choisir un autre format supporté (cf. §7).
- Écran de config **généré automatiquement**, style Bedrock (cf. §10), couleur d'accent personnalisable par le dev (cf. §11).
- **Bouton « Réinitialiser » par réglage** + **bouton « Réinitialiser tout » par mod**. ✅ DÉCIDÉ
- Intégration automatique dans **Mod Menu** (Fabric/Quilt) et dans la **liste des mods Forge/NeoForge** : le dev n'a rien à coder pour avoir son bouton Config. ✅ DÉCIDÉ
- **Éditeur universel, option A** : Confee ajoute automatiquement un bouton **Config** aux mods qui **n'en ont pas**, dans la liste des mods. Ce bouton ouvre l'éditeur Confee sur le(s) fichier(s) de config de ce mod. ✅ DÉCIDÉ (pas d'écran central « tous les mods » — option B écartée)
- Éditeur universel = **fichier + systèmes connus** : lecture brute du fichier pour tous les mods, et en plus intégration du système de config **Forge/NeoForge** (types, min/max, commentaires, application à chaud). ✅ DÉCIDÉ
- Lecture du **maximum de formats** de fichiers (cf. §7). ✅ DÉCIDÉ
- Traductions de l'interface Confee : `en_us` + `fr_fr` pour commencer puis d'autres langues. ✅ DÉCIDÉ

### Plus tard (NE PAS développer en V1)

- **Synchronisation serveur → client** : prévue plus tard. ✅ DÉCIDÉ. En V1, prévoir seulement le champ `scope` dans le modèle (cf. §5.4) pour ne pas casser l'API le jour où elle arrive.
- **Presets complets** (jeux de réglages nommés) : V2. ✅ DÉCIDÉ
- Édition des configs **serveur** avec permissions : avec la synchro.
- Migrations de version de config, éditeur de texte brut intégré : V2 🟡 À VALIDER. (Le color picker visuel est passé en V1 ✅, cf. §10.3.)

---

## 3. Analyse des mods similaires (ce qu'on reprend / ce qu'on évite)

| Mod                                                                          | Comment ça marche                                                                                                                                                                                              | À reprendre                                                                                                                                   | À éviter                                                                                                            |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| **YACL** (YetAnotherConfigLib)                                               | Builder + `ConfigClassHandler` avec champs annotés `@SerialEntry` ; sérialiseur séparé (Gson/JSON5). Ne fait que de la config et des écrans.                                                                   | Séparation propre _modèle / sérialiseur / écran_. Lib focalisée uniquement sur la config. Style qui s'intègre au jeu.                         | API en deux étapes (handler + écran à construire à la main pour un bel écran) : trop verbeux pour « hyper simple ». |
| **Cloth Config / AutoConfig**                                                | Annotations `@Config` + `ConfigData`, écran auto.                                                                                                                                                              | Simplicité des annotations sur une classe POJO.                                                                                               | Projet plus maintenu activement ; UI datée.                                                                         |
| **owo-lib (owo-config)**                                                     | Config intégrée à une lib beaucoup plus large (UI, réseau…).                                                                                                                                                   | —                                                                                                                                             | Lib trop lourde : Confee doit **rester focalisé** sur la config.                                                    |
| **Fzzy Config**                                                              | Classes qui étendent `Config`, POJO, validation forte, sync serveur-client, permissions, annotation `@RequiresAction` (RESTART / RELOG), `@ConvertFrom` pour migrer un ancien fichier.                         | Notion de **« action requise »** (redémarrage) affichée par une icône. Idée de migration depuis un ancien fichier (V2).                       | Complexité de l'API (types `ValidatedXxx`) : trop technique pour notre objectif.                                    |
| **Système Forge/NeoForge (`ForgeConfigSpec`)** + **Configured** (MrCrayfish) | Configured génère un écran pour **tout mod utilisant la config Forge**, sans code côté mod ; édite client/common ; config serveur seulement côté client. Permet au mod de définir un fond via ses métadonnées. | **Exactement notre option A** pour les mods Forge : récupérer la spec et générer l'écran. Ne jamais écraser un écran que le mod fournit déjà. | —                                                                                                                   |
| **Config Menus for Forge** (Fuzs, archivé)                                   | Écran de sélection de fichier quand un mod a plusieurs fichiers ; bouton reset par fichier ; bouton pour ouvrir le fichier dans un éditeur externe.                                                            | **Écran de sélection de fichier**, bouton « Ouvrir le fichier ».                                                                              | —                                                                                                                   |
| **Config Editor** (zhengzhengyiyi)                                           | Éditeur **texte** in-game de n'importe quel fichier de `config/` avec coloration syntaxique (JSON, TOML, YAML, properties, cfg, ini, txt).                                                                     | Liste de formats à couvrir.                                                                                                                   | Éditeur texte brut = pas « simple ». Confee affiche des **widgets** (toggle, slider…), pas du texte.                |
| **MidnightLib, Konfig, ConfigLib TXF, EasyConfig…**                          | Libs multi-loader, écrans générés, TOML commenté, parfois sync.                                                                                                                                                | Confirme qu'un TOML **commenté** et un écran auto sont le standard attendu.                                                                   | —                                                                                                                   |

**Positionnement de Confee** : l'API la plus simple possible (une classe annotée suffit) + la plus belle UI (style Bedrock, couleur de marque du mod) + un éditeur universel qui marche aussi pour les mods qui n'utilisent pas Confee.

---

## 4. Architecture du projet

### 4.1 Outillage Gradle ✅ DÉCIDÉ

- **Multi-version** : **une branche Git par version MC** (`1.20.1`, …). Pas de Stonecutter. Les correctifs se reportent entre branches (cherry-pick).
- ✅ DÉCIDÉ (Lucas, 2026-11-20) : après 1.20.1, **une branche par groupe de versions compatibles**, nommée par la plage ; le jar déclare toute la plage. Branches : `1.20.2`, `1.20.3-1.20.4`, `1.20.5-1.20.6`, `1.21-1.21.1`, `1.21.2-1.21.3`, `1.21.4`, `1.21.5`, `1.21.6-1.21.8`, `1.21.9-1.21.10`, `1.21.11`, `26.1-26.1.2`, `26.2`, `26.3`.
- ✅ DÉCIDÉ (Lucas, 2026-11-20) : chaque branche après 1.20.1 publie **3 jars : Fabric (+ Quilt), NeoForge, Forge**. Ports faits dans l'ordre chronologique, chaque branche partant de la précédente.
- ✅ DÉCIDÉ (Lucas, 2026-11-20) : un port n'est publié (GitHub, Modrinth, CurseForge) qu'une fois le build, les tests unitaires et le script de captures passés sur chacun de ses loaders.
- **Multi-loader** : structure **MultiLoader** (modules `common` / `fabric` / `forge`, Fabric Loom + ForgeGradle, VanillaGradle pour `common`).
  - Adaptations imposées par les versions récentes (2026-11-20) : module `neoforge` à partir de 1.20.2 (NeoGradle 7, puis ModDevGradle à partir de 1.21.11 car NeoGradle ne tourne pas sous Gradle 9) ; ForgeGradle 7 + Gradle 9 à partir de 1.21.11 (Forge 61+) ; à partir de 26.1 (Minecraft non obfusqué), `common` passe par ModDevGradle en mode vanilla (NeoForm), VanillaGradle ne sachant pas préparer ces versions, et Fabric par le Loom sans remapping (`net.fabricmc.fabric-loom`).
- **JDK** : toolchain Gradle automatique (foojay) → Java 17 téléchargé par Gradle, rien à installer.
- **Aucune dépendance runtime obligatoire** pour le joueur hormis le loader (et Fabric API côté Fabric). Mod Menu = dépendance **optionnelle**.

### 4.2 Modules

```
confee/
├── common/        # 95 % du code : API, modèle, formats, UI, compat générique
├── fabric/        # Entrée Fabric + intégration Mod Menu. Le jar Fabric est aussi publié pour Quilt.
├── forge/         # Entrée Forge. Sur 1.20.1, le MÊME jar est publié pour NeoForge (cf. §4.3).
└── neoforge/      # (à partir de 1.20.2+ uniquement) entrée NeoForge dédiée.
```

### 4.3 Particularités de la 1.20.1 ✅ DÉCIDÉ (cible 1.20.1)

- **Java 17**.
- **NeoForge 1.20.1 = fork de Forge 47.1** : même packages `net.minecraftforge.*`. Un jar Forge 1.20.1 tourne sur NeoForge 1.20.1. → **Un seul jar `forge`**, publié avec les tags _Forge_ **et** _NeoForge_.
  - ⚠️ Compiler contre **Forge 47.1.x** (pas 47.3+) et **ne pas utiliser** les API ajoutées dans Forge 47.3.10+ (ex. constructeur de classe principale recevant `FMLJavaModLoadingContext`), sinon le jar ne marchera pas sur NeoForge 47.1. Tester sur les deux loaders.
- **Quilt** charge les mods Fabric → le jar Fabric est publié avec les tags _Fabric_ **et** _Quilt_. Tester sur Quilt.

### 4.4 Packages (dans `common`)

```
<group>.confee
├── api/            # PUBLIC et stable. Seul package que les devs doivent utiliser.
│   ├── annotation/ # @Config, @Category, @Comment, @Range, @Slider, …
│   ├── builder/    # ConfigBuilder, CategoryBuilder, OptionBuilder
│   ├── Confee.java # Point d'entrée statique (register, builder, getConfig…)
│   ├── ConfeeConfig.java, ConfigValue.java, ConfigScope.java, ConfigFormat.java, Theme.java
├── core/           # Modèle interne (ConfigSchema, Section, Option<T>), registre, cycle de vie, validation
├── format/         # Un adaptateur par format (lecture/écriture, préservation des commentaires)
├── external/       # Éditeur universel : découverte des fichiers, inférence de types, intégration Forge config
├── ui/             # Écrans, widgets, thème, rendu
│   ├── screen/
│   ├── widget/
│   └── theme/
└── platform/       # Abstraction loader (ServiceLoader) : dossier config, mod list, env client/serveur…
```

Tout ce qui n'est pas dans `api/` est **interne** et peut changer sans prévenir (le documenter dans la Javadoc).

---

## 5. Le modèle interne (cœur de Confee)

Annotations et builder **produisent tous les deux un `ConfigSchema`**. L'UI, la sérialisation et la validation ne travaillent **que** sur ce modèle. L'éditeur universel produit aussi un `ConfigSchema` (déduit du fichier). **Une seule UI pour les trois sources.**

### 5.1 Objets

```
ConfigSchema
 ├─ modId          : String         (ex. "examplemod")
 ├─ name           : String         (ex. "client" → fichier examplemod-client.toml) — optionnel
 ├─ scope          : ConfigScope    (CLIENT | COMMON ; SERVER réservé pour la future synchro)
 ├─ format         : ConfigFormat   (TOML par défaut)
 ├─ filePath       : Path
 ├─ theme          : Theme          (couleur d'accent…)
 ├─ source         : CONFEE | FORGE_SPEC | RAW_FILE
 └─ root           : Section
       ├─ key, displayName, comment
       ├─ children : List<Section | Option<?>>   (ordre de déclaration conservé)

Option<T>
 ├─ key / path        : "dash.distance"
 ├─ type              : BOOLEAN | INT | LONG | FLOAT | DOUBLE | STRING | ENUM | LIST | COLOR | IDENTIFIER
 ├─ defaultValue : T  (null si inconnu — cas RAW_FILE)
 ├─ value        : T  (valeur courante en mémoire)
 ├─ min / max / step  (nombres)
 ├─ allowedValues     (enum / liste fermée)
 ├─ pattern / maxLength (strings)
 ├─ comment / tooltip
 ├─ requiresRestart   : boolean
 ├─ hidden            : boolean
 ├─ widgetHint        : AUTO | TOGGLE | SLIDER | FIELD | DROPDOWN | CYCLE
 └─ listeners         : List<ChangeListener<T>>
```

### 5.2 Cycle de vie d'une config Confee

1. **Enregistrement** (pendant l'init du mod) : `Confee.register(...)` ou `builder.build()` → création du schema, enregistrement dans le registre global `ConfeeRegistry` (clé = `modId` + `name`).
2. **Chargement immédiat** : si le fichier existe → lecture, validation, correction ; sinon → création avec les valeurs par défaut **et les commentaires**.
3. **Utilisation** : le mod lit les valeurs (champ de la classe annotée ou `ConfigValue#get()`).
4. **Modification via l'écran** → validation → mise à jour mémoire → écriture fichier → notification des listeners (cf. §9.6).

### 5.3 Validation et correction (ne JAMAIS crasher le jeu)

- Valeur hors bornes → ramenée à la borne la plus proche + warning dans le log.
- Mauvais type / valeur illisible → valeur par défaut + warning.
- Clé manquante → ajoutée avec la valeur par défaut au prochain enregistrement.
- ✅ DÉCIDÉ (Lucas) : clé inconnue (ancienne option) → **conservée telle quelle** dans le fichier.
- ✅ DÉCIDÉ (Lucas) : les **commentaires écrits à la main** dans un fichier Confee existant sont **conservés** : le fichier est édité en place (édition chirurgicale, comme pour les autres mods) ; seuls les commentaires générés par Confee (`@Comment`, Range/Allowed) sont mis à jour quand ils changent. ✅ DÉCIDÉ (Lucas) : Confee reconnaît ses commentaires à un **marqueur** (préfixe distinct de celui d'un commentaire normal) ; tout commentaire sans marqueur est considéré comme écrit à la main et gardé. ✅ DÉCIDÉ (Lucas) : marqueur **`#:`** (TOML, YAML, properties) et **`//:`** (JSON5) ; JSON n'a pas de commentaires. Fonctionnement : si toutes les options sont déjà dans le fichier, seules les valeurs changées et les commentaires générés modifiés sont remplacés (le reste est recopié tel quel, un enregistrement sans changement ne touche pas au fichier) ; sinon (option ajoutée par une mise à jour du mod, map ou liste d'objets modifiée) le fichier est réécrit et les lignes de commentaire écrites à la main juste au-dessus de chaque option et de chaque en-tête de section sont remises (commentaires en fin de ligne et commentaires isolés par une ligne vide perdus dans ce cas).
- ✅ DÉCIDÉ (Lucas) : une **liste** ne contient jamais deux fois la même valeur, sauf `@AllowDuplicates` / `.allowDuplicates()`. Fichier Confee : doublons retirés au chargement (1re occurrence gardée, warning `[Confee]`, fichier réécrit). Écran : les suggestions et « + Ajouter » ne proposent pas une valeur déjà présente ; un doublon tapé à la main est retiré sans message à l'enregistrement. Fichiers des autres mods (éditeur universel, specs Forge) : doublons conservés (c'est leur mod qui décide). Le sélecteur en grille cache aussi les valeurs déjà présentes. Raccourcis builder : `itemList`, `blockList`, `idList(key, registry, ...)`, `sortedList`. Une option de liste mal déclarée (mauvais type, bornes incohérentes, défaut avec doublons) donne une erreur qui dit comment corriger.
- ✅ DÉCIDÉ : String qui ne respecte pas `@Pattern` / `@MaxLength` → valeur par défaut + warning (pas de troncature).
- ✅ DÉCIDÉ : les `ChangeListener` sont appelés à **chaque** changement de valeur, y compris via `ConfigValue#set()` depuis le code.
- Fichier totalement illisible (syntaxe cassée) → copie de sauvegarde `fichier.toml.broken-<date>` puis régénération avec les valeurs par défaut + message dans le log. Jamais de crash.

### 5.4 Scope

- `CLIENT` : chargé seulement côté client.
- `COMMON` : chargé côté client et serveur (chacun son fichier local, **pas de synchro en V1**).
- `SERVER` : **réservé**, non utilisable en V1 (lever une exception claire « prévu dans une version future »). Sert à préparer la synchro.

---

## 6. API développeur

Objectif : **un dev doit avoir une config fonctionnelle avec écran en moins de 10 lignes**, sans écrire une ligne d'intégration Mod Menu / Forge.

### 6.1 API par annotations (cas simple, recommandé dans la doc)

```java
@Config(modId = "examplemod")                 // name, scope, format optionnels
@Theme(accent = 0xFF2D7DD2)                    // optionnel : couleur d'accent ARGB
public class ExampleConfig {

    @Comment("Affiche le HUD du mod")
    public boolean showHud = true;

    @Category("dash")
    @Comment("Distance du dash, en blocs")
    @Range(min = 1, max = 10)
    @Slider(step = 0.5)
    public double dashDistance = 4.0;

    @Category("dash")
    public Difficulty difficulty = Difficulty.NORMAL;   // enum → dropdown

    @RequiresRestart
    public int particleCount = 20;                       // pas de @Range → champ numérique

    public List<String> blacklist = new ArrayList<>();   // éditeur de liste
}

// Dans l'init du mod (commune aux loaders) :
public static final ConfeeConfig<ExampleConfig> CONFIG = Confee.register(ExampleConfig.class);

// Utilisation :
if (CONFIG.get().showHud) { ... }
```

**Annotations V1** ✅ DÉCIDÉ (Lucas) : la liste ci-dessous (37 annotations de `api/annotation`) est figée pour la V1.

| Annotation                                                 | Cible           | Rôle                                                                                                  |
| ---------------------------------------------------------- | --------------- | ----------------------------------------------------------------------------------------------------- |
| `@Config(modId, name = "", scope = COMMON, format = TOML)` | classe          | Déclare une config. `name` vide → fichier `config/<modId>.toml` ; sinon `config/<modId>-<name>.toml`. |
| `@Theme(accent, panelBg, …)` ✅                             | classe          | Un attribut par token du §10.2 (tous optionnels). Token non renseigné → valeur par défaut / dérivée.  |
| `@Category(key)`                                           | champ           | Range le champ dans une catégorie (onglet de la sidebar).                                             |
| `@Comment(value)`                                          | champ / classe  | Commentaire écrit dans le fichier + texte du tooltip si pas de traduction.                            |
| `@Name(value)`                                             | champ           | Nom affiché si pas de clé de traduction.                                                              |
| `@Range(min, max)`                                         | champ numérique | Bornes.                                                                                               |
| `@Slider(step)`                                            | champ numérique | Force un slider (nécessite `@Range`).                                                                 |
| `@RequiresRestart`                                         | champ           | Icône « ! » jaune + message « redémarrage requis ».                                                   |
| `@Hidden`                                                  | champ           | Sauvegardé dans le fichier mais absent de l'écran.                                                    |
| `@Pattern(regex)` / `@MaxLength(n)`                        | String          | Validation de texte.                                                                                  |
| `@Validator(Classe)` ✅ (Lucas, 0.3.0)                      | tout            | Vérification du mod (`ValueValidator<T>` : `null` = valide, sinon message = clé de traduction ou texte). Après les vérifications de Confee ; sur une liste de valeurs simples : **chaque valeur**. Fichier : valeur par défaut + warning ; écran : message du mod sous le champ, « Enregistrer » bloqué. Validateur qui lève une exception → valeur acceptée + warning. Tourne aussi côté serveur. Builder : `.validator(...)`. |
| `@Color(alpha = false)`                                    | int             | Afficher comme couleur.                                                                               |
| `@Key(value)`                                              | champ           | Nom de la clé dans le fichier si différent du nom Java.                                               |
| `@Preview(image, text, renderer)` ✅                        | champ           | Aperçu dans la 3e colonne (voir §10.7). Tout est optionnel.                                           |
| `@EnabledIf(cond)` / `@VisibleIf(cond)` ✅                  | champ           | Condition entre options : `"chemin"`, `"!chemin"` ou `"chemin=VALEUR"`. Grisé / masqué sinon.         |
| `@Unit(value)` ✅                                           | numérique       | Unité affichée (suffixe du champ, label du slider). Texte ou clé de traduction.                        |
| `@Multiline` ✅                                             | String          | Zone de texte multi-ligne.                                                                            |
| `@AllowDuplicates` ✅ (Lucas)                              | List            | Autorise la même valeur plusieurs fois dans la liste (builder : `.allowDuplicates()`).                |
| `@ModId` ✅ (Lucas)                                        | String, List<String> | ID d'un mod installé : suggestions avec les logos des mods, logo dans le champ, sélecteur en grille des mods (`.modId()`, `modId()`, `modList()`). Les sous-modules (Fabric API, mods embarqués, `modmenu.parent`) sont cachés mais restent valides. Pas sur ResourceLocation, pas de tags. |
| `only = ...` sur `@Entity`/`@Item`/`@Block`/`@Effect`/`@Enchantment`/`@Biome` ✅ (Lucas) | champ ID | Filtres cumulables (= l'un ou l'autre) tirés de ce que le jeu définit : catégorie de spawn (+ `LIVING`), onglets créatifs (+ `BLOCKS`), catégorie d'effet, catégorie d'enchantement (+ `TREASURE`, `CURSE`), tags de biome `is_overworld`/`is_nether`/`is_end`. Suggestions et sélecteur filtrés, erreur sur le champ dans l'écran. **Pas de vérification au chargement du fichier** (registres incomplets à l'init : items des mods, biomes du monde). Builder : `.entity(EntityFilter...)`, `.item(ItemFilter...)`… Erreur dev si le filtre ne correspond pas au registre. |
| `@Picker(styles, defaultStyle)` ✅ (Lucas)                 | champ ID avec sélecteur | Styles du sélecteur : `GRID` (icônes), `LIST` (icône + nom + ID), `DETAILED` (+ description du mod, sinon l'ID). `styles` vide = tous ; `defaultStyle = AUTO` = grille pour items/blocs/autres, liste pour les mods. Le joueur bascule avec les boutons de l'en-tête (ou Tab) ; dernier choix retenu par type d'ID dans `confee.toml` (`pickerStyles`, caché), ignoré s'il n'est pas autorisé. Builder : `.picker(style)`, `.pickerStyles(...)`. Erreur dev si pas de sélecteur, `AUTO` dans `styles`, ou défaut hors des styles autorisés. |
| `@Size(min, max)` ✅ (Lucas)                               | List            | Nombre de valeurs. Fichier : trop long → coupé, trop court → défaut. Écran : « + Ajouter » / ✕ grisés aux bornes (`.size()`, `.maxSize()`). |
| `@Sorted` ✅ (Lucas)                                        | List            | Liste triée au chargement et à l'enregistrement (ordre naturel, enums dans l'ordre de déclaration) ; flèches ↑↓ cachées ; défaut trié automatiquement (`.sorted()`). |
| `@Reorderable` ✅ (Lucas)                                  | List, liste d'objets | Affiche les flèches ▲▼ de déplacement ; **sans elle, pas de flèches** (par défaut). Interdit avec `@Sorted` (`.reorderable()`). |
| `@IgnoreCase` ✅ (Lucas)                                    | List<String>    | Doublons comparés sans la casse (`.ignoreCase()`). Interdit avec `@AllowDuplicates`. |
| `@Range` / `@Pattern` / `@MaxLength` sur une List ✅ (Lucas) | List            | S'appliquent à **chaque valeur** (pas d'annotation `@Element…` séparée). Fichier : nombre ramené à la borne, texte invalide → valeur par défaut. Écran : erreur sur le champ de la valeur. Le nombre de valeurs, c'est `@Size`. |
| `@Registry(id)` ✅                                          | ResourceLocation| Suggestions d'autocomplétion depuis un registre intégré (`minecraft:item`, `minecraft:block`…).       |
| `@Item` / `@Block` ✅                                       | ResourceLocation, String, List<String> | Raccourci : registre item/bloc + icône dans le champ et les suggestions + sélecteur en grille + aperçu automatique. |
| `@Entity` `@Biome` `@Dimension` `@Structure` `@Enchantment` `@Effect` `@Sound` `@Fluid` ✅ | ResourceLocation, String, List<String> | Raccourcis typés (= `@Registry(...)`). Attribut `tags = true` (aussi sur `@Item`, `@Block`, `@Registry`) : tags acceptés et suggérés (`#ns:chemin`), champ String / List<String> obligatoire. Builder : `.entity()`, `.biome()`… `.tags()`. |
| `@Description(text)` ✅                                     | champ           | Texte affiché **sous le nom** (pas écrit dans le fichier, contrairement à `@Comment`). Texte ou clé de traduction ; sans annotation, la clé `config.<modId>.<chemin>.description` est utilisée si elle existe. |
| `@Suggestions(values, provider)` ✅                         | String, ResourceLocation, List<String> | Suggestions fournies par le mod (valeurs fixes et/ou `SuggestionProvider` appelé à l'ouverture de l'écran). |
| `@Stepper(step)` ✅                                         | numérique       | Champ avec boutons − / + (Maj = ×10, flèches ↑↓).                                                     |
| `@Keybind` ✅                                               | String          | Touche (`key.keyboard.g`, `key.mouse.right`) : clic puis appui ; Suppr = aucune touche.               |
| `Set<Enum>` / `EnumSet` ✅                                   | type de champ   | Cases à cocher (plusieurs valeurs d'un enum, sans doublon, ordre de l'enum).                          |
| `@Widget(Classe)` ✅                                        | champ           | Widget du mod (`OptionWidget`, client uniquement) à la place de celui de Confee.                      |
| API widgets du mod ✅ (Lucas, 0.3.0)                         | `OptionWidget`  | `error()` (message rouge sous le widget, bloque « Enregistrer »), `capturesText()`, `focusBounds()` + `revealFocus()` (seule la partie focalisée est ramenée à l'écran), `renderOverlay` / `overlayBounds` / `overlayClicked` / `overlayScrolled` (contenu par-dessus les options suivantes tant que le widget a le focus), `viewportHeight()`. Hauteur max d'un widget : 4000. Aides publiques `ConfeeDraw` (bouton, bouton d'accent, texte sans ombre, anneau de focus) et `ConfeeIcons` (icône / nom d'une entrée de registre, aussi depuis l'écran titre). `SuggestionProvider#needsWorld()` → indice grisé « ouvrez un monde » dans un champ vide hors monde. |
| `@Category(value, icon)` / `@Icon` ✅                       | champ / classe `@Section` | Icône (ID d'item ou texture `.png`) de la catégorie / de la ligne de navigation.            |
| `@Layout(...)` ✅                                           | classe          | Mise en page : largeur du panneau et de l'aperçu, sidebar masquée, bannière, fond en texture.         |
| champ `Decoration` ✅                                       | champ public    | Élément d'écran non enregistré, à sa place parmi les options : titre, séparateur, texte (info/avertissement), image, bouton d'action. `visibleIf` et icône possibles. |

- Sections imbriquées : un champ dont le type est une classe statique annotée `@Section` devient une sous-section ✅ DÉCIDÉ (Lucas).
- Collections ✅ DÉCIDÉ : `List<String|Integer|Long|Float|Double|Boolean|Enum>`, `Map<String, scalaire|Enum>` (éditeur clé → valeur), `List<ClasseSection>` (liste d'objets : classe statique `@Section` avec constructeur public sans argument et champs scalaires/enum uniquement).
- Erreurs de déclaration ✅ DÉCIDÉ : en dev, **toutes** les erreurs d'une classe sont listées d'un coup, chacune avec la correction à faire (« Fix: … ») ; une condition vers une clé inconnue propose le bon chemin (« did you mean … »).
- Conditions ✅ DÉCIDÉ : évaluées sur les valeurs en cours d'édition (pas encore enregistrées) ; une cible inconnue fait échouer l'enregistrement en dev, est ignorée (log `[Confee]`) en prod.
- Les champs `static`, `transient` ou non publics sont ignorés.
- Valeurs par défaut = valeurs d'initialisation des champs (instance créée via constructeur sans argument).

### 6.2 API par builder (cas avancés)

```java
public static final ConfeeConfig<?> CONFIG;
public static final ConfigValue<Boolean> SHOW_HUD;
public static final ConfigValue<Double>  DASH_DISTANCE;

static {
    ConfigBuilder b = Confee.builder("examplemod")
        .name("client")
        .scope(ConfigScope.CLIENT)
        .accentColor(0xFF2D7DD2);

    SHOW_HUD = b.bool("showHud", true).comment("Affiche le HUD du mod").build();

    CategoryBuilder dash = b.category("dash").comment("Réglages du dash");
    DASH_DISTANCE = dash.doubleRange("distance", 4.0, 1.0, 10.0).slider(0.5).build();
    dash.enumValue("difficulty", Difficulty.NORMAL).build();

    CONFIG = b.build();   // enregistre + charge
}
```

Variante en bloc (pour les options dont on ne garde pas la référence) : `b.category("dash", c -> c.bool("enabled", true).build());`

✅ DÉCIDÉ (implémentation) :
- `CategoryBuilder.comment(...)` commente la table de la catégorie (parité avec `@Section` + `@Comment`).
- `@Theme` : la valeur « non renseigné » est `0` (une couleur 100 % transparente n'est pas utilisable comme token).
- `@Slider(step)` : `step` obligatoire.
- Listes : éléments String, Integer, Long, Float, Double, Boolean (pas d'enums).
- `@Category` uniquement dans la classe `@Config` (pas dans une `@Section`).
- Format autre que TOML → « pas encore disponible » tant que les adaptateurs (§17 étape 9) n'existent pas.

`ConfigValue<T>` expose : `get()`, `set(T)`, `getDefault()`, `reset()`, `addListener(ChangeListener<T>)`.

### 6.3 API commune

```java
ConfeeConfig<T>:
  T get()                      // instance (annotations) ; null pour le builder
  void save()
  void reload()                // relit le fichier
  void resetAll()
  void onChange(Runnable)      // appelé après chaque sauvegarde depuis l'écran
  Screen createScreen(Screen parent)  // pour un dev qui veut ouvrir l'écran depuis son propre bouton

Confee:
  static <T> ConfeeConfig<T> register(Class<T>)
  static ConfigBuilder builder(String modId)
  static Optional<ConfeeConfig<?>> get(String modId, String name)
```

### 6.4 Ce que le dev N'A PAS à faire

- Pas d'entrypoint Mod Menu à écrire (Confee fournit l'écran à Mod Menu pour lui).
- Pas d'extension point Forge à enregistrer (Confee le fait).
- Pas de code de sérialisation.

### 6.5 Dépendance pour le dev ✅ DÉCIDÉ

- Confee est une **dépendance que le joueur installe** (comme YACL / Cloth). Pas de Jar-in-Jar prévu.

---

## 7. Formats de fichiers

### 7.1 Formats et niveau de support

| Format                    | Extensions                                  | Lecture                                     | Écriture configs Confee | Écriture fichiers d'autres mods | Commentaires préservés |
| ------------------------- | ------------------------------------------- | ------------------------------------------- | ----------------------- | ------------------------------- | ---------------------- |
| **TOML** (défaut)         | `.toml`                                     | ✅                                          | ✅                      | ✅                              | ✅                     |
| JSON                      | `.json`                                     | ✅                                          | ✅                      | ✅                              | n/a                    |
| JSON5 / JSONC             | `.json5`, `.jsonc`                          | ✅                                          | ✅                      | ✅                              | ✅                     |
| YAML                      | `.yml`, `.yaml`                             | ✅                                          | ✅                      | ✅                              | ✅                     |
| Properties                | `.properties`                               | ✅                                          | ✅                      | ✅                              | ✅                     |
| Forge legacy cfg          | `.cfg` (format `S:key=value`, `B:`, `I:` …) | ✅                                          | ❌                      | ✅                              | ✅                     |
| INI                       | `.ini`                                      | ✅                                          | ❌                      | ✅                              | ✅                     |
| HOCON (V1.1)              | `.conf`, `.hocon`                           | ✅                                          | ❌                      | ✅                              | ✅                     |
| Fichier inconnu / binaire | autre                                       | ❌ → message + bouton « Ouvrir le fichier » | —                       | —                               | —                      |

✅ DÉCIDÉ : HOCON reporté en **V1.1** (2 fichiers `.conf` sur ~1 900 configs réelles observées).
✅ DÉCIDÉ (Lucas) : en V1.1, **spec HOCON complète**, substitutions `${}`, `include` et concaténation de valeurs compris, éditables.
✅ DÉCIDÉ (Lucas, 2026-11-19) et implémenté (`format/hocon`) :
- **Substitution** (`a = ${b}`, `${?ENV}`) : `a` en lecture seule, affiché « `${b} → valeur` » ; `b` reste éditable là où il est écrit. `+=` : lecture seule.
- **Concaténation** sans substitution (`foo bar`, `"a" b`, `[1] [2]`) : éditée comme une seule valeur, réécrite en un seul texte (sans guillemets si possible) ou une seule liste ; avec une substitution : lecture seule.
- **`include`** (`"x"`, `file()`, `required()`) : les valeurs du fichier inclus sont éditées **dans ce fichier**, avec sa propre copie d'origine ; fichier cherché à côté du fichier qui l'inclut (`x`, `x.conf`, `x.json`), jamais hors de `config/` ; `url()` et `classpath()` ignorés ; un `required()` introuvable rend le fichier illisible.
- **Clés en double** : les objets fusionnent, une autre valeur remplace la précédente ; seule la valeur qui compte est affichée et éditée.
- Listes d'objets : comme les autres formats (§8.3, sous-écran par élément).

✅ DÉCIDÉ (validé par Lucas) : SERVER Forge grisé hors d'un monde / lecture seule dans un monde ; ordre Client, Common, Server.

✅ Implémenté (étape 9) — parseurs maison avec positions, sans lib :
- JSON / JSONC / JSON5 : un seul parseur ; commentaires `//` `/* */` acceptés partout (beaucoup de mods en mettent dans des `.json`) ; JSON5 : clés non quotées, `'…'`, hexa, `Infinity`/`NaN`, `+`, virgules finales. `null`, listes mixtes, objets dans des listes, entiers > long → lecture seule.
- YAML (cas courants) : maps, scalaires simples/`'…'`/`"…"`, listes de scalaires (bloc `- a` ou `[a, b]`). Blocs `|` `>`, ancres, alias, tags, maps en ligne, listes d'objets, scalaires multi-lignes → lecture seule. Plusieurs documents `---` ou clés en double → fichier illisible. Inférence prudente : seuls `true`/`false` sont des booléens (`yes`/`no`/`y` restent du texte et sont écrits entre guillemets s'ils deviennent la valeur d'un texte), `007` reste du texte.
- Properties : échappements, continuations, `\uXXXX` en écriture (comme `Properties.store`). Clé en double : seule la dernière (celle que Java lit) est éditable.
- INI : sections, `=` ou `:`, commentaires `;`/`#` en début de ligne, valeur entre `"…"` conservée. Clé en double : seule la première est éditable. Fichier sans `=` → illisible (ex. listes de tri de JEI).
- `.cfg` : format legacy Forge (`B:`/`I:`/`D:`/`S:`, catégories `{}`, listes `<>`, bornes `# Min:`/`# Max:` et `[range: a ~ b]` → bornes du champ) ; sinon lu comme INI.
- Configs Confee : TOML, JSON, JSON5, YAML, properties (clés plates `cat.key`, listes `a,b` avec `\,`). Commentaires + indications Range/Allowed partout sauf JSON (pas de commentaires en JSON). Clés inconnues conservées.
- Validé sur 1 902 fichiers réels (CurseForge de Lucas) : aller-retour octet pour octet + chaque valeur modifiée relue, 0 échec ; 12 fichiers JEI non-INI signalés illisibles.

### 7.2 Règle d'or pour les fichiers des AUTRES mods : édition chirurgicale

Quand Confee modifie le fichier d'un mod qui ne l'utilise pas, il **ne doit jamais ré-écrire le fichier entier** à partir d'un modèle reparsé (ça casserait l'ordre, les commentaires, l'indentation, la mise en forme que le mod attend).

➡️ Implémentation demandée : chaque adaptateur de format, en lecture, garde pour chaque valeur sa **position exacte (offset début/fin) dans le texte d'origine**. À la sauvegarde, on **remplace uniquement les portions de texte des valeurs modifiées**, tout le reste est recopié octet pour octet.

- Test obligatoire : ouvrir puis enregistrer **sans rien changer** doit produire un fichier **strictement identique** (byte à byte).
- Test obligatoire : changer une valeur ne modifie **que** cette valeur dans le diff.
- Préserver l'encodage (UTF-8 par défaut, gérer un BOM éventuel) et les fins de ligne (`\n` / `\r\n`).
- Écriture **atomique** : écrire dans un fichier temporaire puis renommer.

### 7.3 Configs Confee (fichiers dont Confee est propriétaire)

Là, Confee peut ré-écrire le fichier entier, avec :

- l'ordre de déclaration des options,
- les commentaires `@Comment` au-dessus de chaque clé,
- un commentaire d'en-tête indiquant les bornes / valeurs possibles (ex. `# Range: 1.0 ~ 10.0`, `# Allowed: EASY, NORMAL, HARD`),
- ✅ DÉCIDÉ (Lucas, 0.3.0) : `@Config(writeDefaults = true)` / `.writeDefaults()` → ligne `#: Default: <valeur>` sous les bornes (pas pour les maps, listes d'objets, textes multi-lignes),
- ✅ DÉCIDÉ (Lucas, 0.3.0) : `@Config(legacyFiles = {...})` / `.legacyFiles(...)` (chemins relatifs à `config/`, même format) : si le fichier Confee n'existe pas, le 1er ancien fichier trouvé est lu, réécrit comme fichier Confee **sans fusion des commentaires** (commentaires Confee seulement, clés inconnues gardées) puis supprimé ; nom qui ne diffère que par la casse géré (déplacement via un nom temporaire) ; ancien fichier illisible → laissé tel quel, défauts. Remplace `fileName` et `migrateComments` (une seule option),
- une table TOML par catégorie (`[dash]`).

### 7.4 Bibliothèques 🟡 À VALIDER

- ✅ DÉCIDÉ : TOML = **parseur maison avec positions**, aucune lib (rien à relocaliser). Même approche prévue pour JSON/JSON5, properties, cfg, INI (YAML : parseur limité aux cas courants : maps, scalaires, listes simples).
- YAML : SnakeYAML (relocalisé) pour la lecture si besoin, mais l'écriture reste chirurgicale.
- Aucune lib ne doit fuiter dans le classpath des autres mods (toujours relocaliser).

---

## 8. Éditeur universel (mods qui n'utilisent pas Confee)

### 8.1 Principe (option A) ✅ DÉCIDÉ

Dans la liste des mods (Mod Menu sur Fabric/Quilt, liste des mods intégrée sur Forge/NeoForge), **chaque mod qui n'a pas de bouton Config en reçoit un**, ajouté par Confee. Le joueur fait : **Mods → le mod → Config** → éditeur Confee.

- Si le mod **a déjà** un écran de config (le sien, ou fourni par Cloth/YACL/Configured…), Confee **ne le remplace pas**. ✅ (comme Configured)
- Si aucun fichier de config n'est trouvé pour ce mod, **pas de bouton** (ne pas afficher un bouton qui mène à un écran vide).
- ✅ DÉCIDÉ (Lucas, remplace l'ancien « pas d'option ») : le joueur peut **forcer l'éditeur Confee** sur des mods choisis qui ont déjà leur propre écran, via `forceConfeeEditor` dans `confee.toml` (§12) :
  - le bouton Config du mod ouvre l'éditeur Confee sur ses fichiers **à la place** de son écran (Cloth, YACL, Configured, écran maison…) ;
  - seuls les mods dont Confee trouve des fichiers (fichiers bruts, mapping, `registerExternalFile`) ou des configs Forge/NeoForge sont concernés ; les suggestions (avec les logos des mods) et le sélecteur en grille ne proposent que ceux-là, et un ID sans fichier garde son écran ;
  - pris en compte **à chaud** (relu à chaque clic sur Config) ;
  - `ignoredMods` bloque l'override ; `injectConfigButtons = false` ne le bloque pas ;
  - Fabric/Quilt : mixin dans Mod Menu (`ModMenu.getConfigScreen`), appliqué seulement si Mod Menu est installé ; les configs cachées dans les réglages de Mod Menu restent cachées. Le même mixin donne l'éditeur Confee aux mods qui déclarent une intégration Mod Menu **sans** écran (fin de la limite connue du §12). Forge/NeoForge : la factory d'écran du mod est enveloppée au `FMLClientSetupEvent`.
- Sans override, le bouton Confee ajouté se comporte exactement comme si le mod avait fourni son propre écran.
- ✅ DÉCIDÉ : les fichiers de `config/` associés à **aucun** mod ne sont pas accessibles en V1 (seule l'option A existe).

### 8.2 Trouver les fichiers de config d'un mod

Ordre de priorité :

1. **Configs Confee** du mod (registre interne) → source `CONFEE`.
2. **Configs Forge/NeoForge** déclarées par le mod (`ConfigTracker` / `ModConfig` du mod) → source `FORGE_SPEC` (Forge/NeoForge uniquement ; cf. §8.4).
3. **Heuristique sur les noms de fichiers** dans `config/` → source `RAW_FILE` :
   - `config/<modid>.<ext>`
   - `config/<modid>-<suffix>.<ext>` et `config/<modid>_<suffix>.<ext>` (ex. `-client`, `-common`)
   - tous les fichiers de `config/<modid>/`, **récursivement, sans limite de profondeur** ✅ DÉCIDÉ (Lucas)
   - comparaison **insensible à la casse**, en traitant `-`, `_` et l'absence de séparateur comme équivalents ; tester aussi le **nom affiché** du mod normalisé (ex. mod ID `xaerominimap` ↔ fichier `xaero-minimap.txt`).
   - exclure les fichiers déjà couverts par 1 et 2, les `.bak`, `.old`, `.broken-*`, les fichiers > 1 Mo, les binaires.
4. **Mapping manuel** dans `config/confee.toml` (cf. §12) pour corriger l'heuristique.
5. **API pour les devs tiers** : `Confee.registerExternalFile(modId, path)` pour qu'un mod déclare ses fichiers sans utiliser Confee pour le reste. ✅ DÉCIDÉ (Lucas)

Si plusieurs fichiers → **écran de sélection de fichier** d'abord (cf. §10.4).

### 8.2 bis Détection automatique (fichiers bruts et configs Forge) ✅ DÉCIDÉ (Lucas)

- Textes et listes de textes qui ressemblent à des IDs (`ns:chemin`, tags `#ns:chemin` acceptés) : le registre est deviné d'abord par le **nom de la clé** (`biome`, `dimension`, `structure`, `enchant`, `effect`/`potion`, `sound`, `fluid`, `entit`/`mob`, `block`, `item`), sinon par les **valeurs** (toutes connues dans item, block, entity_type, enchantment ou mob_effect, dans cet ordre). Mêmes suggestions, icônes, sélecteur et aperçu que pour les configs Confee.
- Textes couleur (`#RRGGBB`, `#AARRGGBB`, `0x…`) : pastille + **color picker** ; la couleur choisie est réécrite au même format (préfixe, longueur, casse).
- Les configs Confee ne sont jamais devinées (le mod déclare ce qu'il veut).
- ✅ DÉCIDÉ (Lucas, V1.1) : **couleurs stockées en entier** dans les fichiers bruts → pastille + color picker si le **nom de la clé** contient `color`, `colour`, `couleur` ou `tint` (insensible à la casse) **et** si la valeur est entre 0 et `0xFFFFFFFF` (les ARGB négatifs, entiers signés, sont acceptés). RGB si la valeur ≤ `0xFFFFFF`, ARGB sinon. Réécrite sous forme d'entier, en signé si l'original était négatif.
- ✅ DÉCIDÉ (Lucas, V1.1) : **structures imbriquées** (liste de listes, objet dans un objet, map dont les valeurs sont des objets) et **listes d'objets YAML** des autres mods → éditables dans un **sous-écran** par objet ou liste, avec fil d'Ariane et bouton Retour, sans limite de profondeur.
- ✅ Widgets custom sur les **configs Forge** (`ForgeConfigSpec`) : `ConfeeWidgets.forOption("modid:client", chemin, …)` / `forType(modid, …)` ; ids de config Forge = `modid:client`, `modid:common`, `modid:server`.
- ✅ DÉCIDÉ (Lucas, 2026-11-19) et implémenté : widgets custom aussi sur les **fichiers bruts** : id `file:<modid>/<chemin depuis config/>` (`ConfeeWidgets.fileId(modid, chemin)`), chemins avec `[n]` pour les éléments de liste, nombres en `Long` / `Double` ; `forType(modid, …)` s'applique aussi aux fichiers de ce mod.

### 8.3 Fichier brut (`RAW_FILE`) : inférence des types

| Valeur dans le fichier          | Type déduit                               | Widget                                                   |
| ------------------------------- | ----------------------------------------- | -------------------------------------------------------- |
| `true` / `false`                | BOOLEAN                                   | Toggle                                                   |
| entier                          | INT / LONG                                | Champ numérique (**pas de slider** : bornes inconnues)   |
| décimal                         | DOUBLE                                    | Champ numérique                                          |
| texte                           | STRING                                    | Champ texte                                              |
| `#RRGGBB` / `0xAARRGGBB`        | STRING (affiché avec pastille couleur) 🟡 | Champ texte + aperçu                                     |
| liste de scalaires              | LIST                                      | Éditeur de liste                                         |
| table / objet                   | Section                                   | Catégorie (ou sous-section)                              |
| liste d'objets / de listes / mixte ✅ | Section « liste »                    | Un sous-écran par élément (`#n · contenu`), ▲ ▼ ✕, « + Ajouter » |
| valeur complexe (date, `null`…) | —                                         | Affichée en lecture seule + bouton « Ouvrir le fichier » |

✅ Implémenté (2026-11-19, V1.1) — structures imbriquées des autres mods (§8.2 bis) :
- JSON/JSON5, YAML et TOML (tableaux en ligne et tableaux de tables `[[x]]`) ; chaque élément est un sous-écran, à toute profondeur ; un scalaire ou une liste de scalaires dans une liste a aussi son sous-écran.
- Valeurs : édition chirurgicale comme ailleurs. « + Ajouter » copie le **dernier élément** (le fichier ne dit pas à quoi ressemble un élément vide) ; ✕ retire l'élément et les commentaires juste au-dessus de lui ; ▲ ▼ échangent deux éléments avec leurs commentaires. Tout reste en mémoire jusqu'à « Enregistrer » ; « Annuler » relit le fichier.
- Limite : une liste vide ne peut pas recevoir d'élément (pas de modèle).

- Les **commentaires** situés juste au-dessus d'une clé (ou en fin de ligne) deviennent la **description/tooltip** de l'option.
- Pour le Forge legacy `.cfg`, utiliser le préfixe de type (`B:` bool, `I:` int, `D:` double, `S:` string) et les commentaires `# Min: / Max:` s'ils existent pour proposer des bornes.
- Un type ne doit **jamais changer** à l'enregistrement (un `5` reste un entier, un `5.0` reste un décimal, une string reste entre guillemets, le style de guillemets d'origine est conservé).

**Valeurs par défaut inconnues** : un fichier brut ne contient pas ses valeurs par défaut. ✅ DÉCIDÉ (Lucas) :

- À la **première modification** d'un fichier par Confee, copie de l'original dans `config/confee/backups/<chemin-relatif>`.
- Le bouton par option devient **« Restaurer la valeur d'origine »** (valeur de cette copie) au lieu de « par défaut ».
- « Réinitialiser tout » → restaure la copie d'origine complète (avec confirmation).
- Le libellé dans l'UI doit être clair : « Valeur d'origine », pas « Valeur par défaut ».

**Application des changements** : Confee ne peut pas savoir si le mod relit son fichier. → Bandeau permanent en haut de l'écran : _« Ce mod n'utilise pas Confee : les changements seront appliqués au prochain redémarrage du jeu. »_ (traduit).

### 8.4 Configs Forge/NeoForge (`FORGE_SPEC`)

Sur Forge/NeoForge, pour chaque mod, récupérer ses `ModConfig` (types CLIENT / COMMON / SERVER) et leur `ForgeConfigSpec` :

- Pour chaque `ConfigValue` : clé/chemin, valeur, **valeur par défaut**, **commentaire**, **clé de traduction**, **bornes** (`ValueSpec#getRange`), drapeau **`needsWorldRestart`** → mappé sur `requiresRestart`.
- Modification : `ConfigValue#set(...)` puis sauvegarde de la spec via l'API Forge → **application à chaud** (Forge déclenche son événement de rechargement pour le mod). Pas de bandeau « redémarrage » sauf si `needsWorldRestart`.
- Le reset utilise les **vraies valeurs par défaut** de la spec.
- Configs **SERVER** : ✅ DÉCIDÉ (Lucas) — en V1, affichées en **lecture seule** avec le message « Les configs serveur seront modifiables dans une future version » (cohérent avec la synchro repoussée). Alternative : modifiables uniquement en solo, monde chargé.
- S'inspirer du fonctionnement de **Configured** (MrCrayfish) et **Config Menus for Forge** (Fuzs) pour l'accès aux specs. **Ne pas copier leur code** (vérifier leurs licences).
- Sur les versions où Forge et NeoForge divergent (≥ 1.20.2), NeoForge utilise `ModConfigSpec` (même principe, autres noms) : à adapter dans la branche de la version concernée.

✅ Implémenté (étape 8, validé en jeu sur Forge 47.1.3) :
- Ordre des fichiers : Client, Common, Server. Badge « Forge ».
- SERVER : bouton grisé « disponible dans un monde, en lecture seule » hors d'un monde ; dans un monde, écran en lecture seule + bandeau.
- Bornes lues via `ValueSpec#correct` (la classe `Range` est privée en 47.1) ; enums restreints (`defineEnum(..., acceptables)`) → seules les valeurs acceptées sont proposées ; la validation de la spec (`ValueSpec#test`) est appliquée à la frappe.
- Application à chaud : le watcher de fichiers de Forge **ne réagit pas** à une sauvegarde faite par `spec.save()`. Confee fait donc ce que fait le watcher, via l'API publique de FML : `spec.afterReload()` puis `ModContainer#dispatchConfigEvent(IConfigEvent.reloading(config))` → le mod reçoit `ModConfigEvent.Reloading`.
- Types non gérés par les widgets (listes mixtes, objets) : affichés en lecture seule.

✅ DÉCIDÉ (Lucas) : sur Fabric, mods utilisant **Forge Config API Port** → même intégration que ci-dessus (implémentée).

---

## 9. Intégration avec les loaders

### 9.1 Fabric / Quilt + Mod Menu

- Entrypoint `modmenu` dans `fabric.mod.json` de Confee (Mod Menu = dépendance **optionnelle**, `suggests`).
- `getModConfigScreenFactory()` → écran de Confee lui-même (`confee.toml`).
- `getProvidedConfigScreenFactories()` → `Map<modId, factory>` contenant :
  - tous les mods qui ont une config Confee,
  - tous les mods sans écran pour lesquels l'éditeur universel a trouvé des fichiers (option A).
  - Mod Menu donne la priorité à l'écran fourni par le mod lui-même → les mods qui ont déjà un écran gardent le leur (vérifier ce comportement sur la version de Mod Menu de 1.20.1).
- Sans Mod Menu installé : les écrans restent ouvrables via l'API (`createScreen`) ; pas d'autre point d'entrée en V1. 🟡 À VALIDER

### 9.2 Forge (et NeoForge 1.20.1)

- Pendant `FMLClientSetupEvent` (une fois tous les mods construits) : pour chaque mod concerné, si son `ModContainer` **n'a pas** déjà d'extension `ConfigScreenHandler.ConfigScreenFactory`, en enregistrer une qui ouvre l'écran Confee → le bouton **Config** de la liste des mods s'active.
- Même logique pour Confee lui-même.

### 9.3 NeoForge ≥ 1.20.2 (plus tard)

- Extension point `IConfigScreenFactory` enregistré sur le `ModContainer` du mod. Même logique « ne pas écraser ».

### 9.4 Côté serveur dédié

- Aucune classe client chargée côté serveur (séparer strictement le code UI : jamais de référence à `Screen`/`Minecraft` dans le chemin serveur).
- Les configs `COMMON` sont chargées/sauvegardées normalement côté serveur.

### 9.5 Abstraction plateforme (`platform/`)

Interface `Platform` chargée par `ServiceLoader`, implémentée dans chaque module loader :
`getConfigDir()`, `isClient()`, `getModIds()`, `getModDisplayName(modId)`, `getModIcon(modId)`, `isModLoaded(id)`, `hasOwnConfigScreen(modId)`, `getForgeConfigs(modId)` (vide sur Fabric)…

### 9.6 Notifications de changement

Après chaque sauvegarde depuis l'écran :

1. mise à jour des valeurs en mémoire (champs de la classe annotée / `ConfigValue`),
2. appel des `ChangeListener` par option (ancienne + nouvelle valeur),
3. appel des callbacks `onChange` de la config,
4. écriture du fichier.

Thread : tout se passe sur le **thread client** (render thread). Écriture disque : ✅ DÉCIDÉ (Lucas) synchrone en V1 (fichiers petits).

---

## 10. Interface utilisateur (style Bedrock Edition)

### 10.1 Références visuelles

Lucas a fourni 4 captures de l'UI de Minecraft **Bedrock Edition** (à placer dans `docs/ui-reference/` du dépôt) :

1. Planche de composants : toggle off, toggle on, bouton, slider.
2. Panneau de réglages : dropdown « UI Profile », slider avec label au-dessus, toggles, slider avec crans + icône d'alerte jaune, slider **désactivé** grisé, bouton « Reset to Default » pleine largeur.
3. Écran « Create New World » : barre de titre avec flèche retour, **sidebar gauche** (sections « Edit Settings », boutons pleine largeur, élément actif en **vert**), panneau droit scrollable avec labels au-dessus des champs, champ texte sombre, dropdowns, toggles, ligne avec icône + chevron `›`.
4. Écran à **onglets** en haut (onglet actif vert), liste à gauche, panneau de détail à droite, scrollbars fines.

**Le style doit être reproduit en Java Edition**, en dessinant les widgets **par code** (rectangles pleins, pas de textures) pour que la couleur d'accent soit recolorable. ✅ DÉCIDÉ (Lucas).

### 10.2 Tokens de couleur ✅ DÉCIDÉ (Lucas : valeurs relevées à l'œil, validées telles quelles)

| Token             | Usage                                   | Valeur approx.                | Personnalisable par le dev |
| ----------------- | --------------------------------------- | ----------------------------- | -------------------------- |
| `accent`          | Toggle ON, onglet/élément actif, focus  | `#3C8527`                     | ✅ oui                     |
| `accentDark`      | Bordure basse / état pressé de l'accent | dérivé (≈ `#1D4D13`)          | dérivé auto                |
| `accentLight`     | Survol d'un élément accent              | dérivé                        | dérivé auto                |
| `onAccentText`    | Texte sur fond accent                   | blanc ou noir selon luminance | dérivé auto                |
| `panelBg`         | Fond des panneaux                       | `#313233`                     | ❌                         |
| `panelBgDeep`     | Fond des champs texte / track de slider | `#1E1E1F`                     | ❌                         |
| `buttonFace`      | Face des boutons, dropdowns, knobs      | `#D0D1D4`                     | ❌                         |
| `buttonFaceHover` | Survol                                  | `#E6E8EB`                     | ❌                         |
| `buttonShadow`    | Liseré bas « 3D » des boutons           | `#58585A`                     | ❌                         |
| `outline`         | Contour 1 px                            | `#1E1E1F`                     | ❌                         |
| `textOnDark`      | Texte sur fond sombre                   | `#FFFFFF`                     | ❌                         |
| `textOnLight`     | Texte sur bouton clair                  | `#1E1E1F`                     | ❌                         |
| `textDisabled`    | Label/option désactivé(e)               | `#6E6E70`                     | ❌                         |
| `warning`         | Icône « ! » (redémarrage requis)        | `#F7D23B`                     | ❌                         |
| `error`           | Bordure/texte d'erreur de validation    | `#E4433A`                     | ❌                         |
| `toggleOffTrack`  | Fond du toggle OFF                      | `#8C8D90`                     | ❌                         |

Dérivation de l'accent : convertir en HSL, `accentDark` = luminosité −15 points, `accentLight` = +8 points. ✅ DÉCIDÉ : `onAccentText` = celle des deux couleurs (blanc ou `textOnLight`) qui a le **meilleur contraste WCAG** avec l'accent. Les tokens dérivés ne sont calculés que si le dev ne les fixe pas lui-même.

✅ DÉCIDÉ : la **barre de titre** est **claire** comme sur la capture 5 (`docs/ui-reference/reference-5.png`), avec texte foncé. Tokens relevés sur la capture (personnalisables comme les autres) : `titleBarBg` = `#E7E8EC`, `titleBarText` = `#1E1E1F` ; liseré bas = `toggleOffTrack` (≈ `#909090` sur la capture) puis 1 px `outline`.

### 10.3 Composants (widgets)

Toutes les dimensions sont en **pixels GUI** (respecter le GUI scale vanilla). Police : police vanilla de Minecraft. Son : clic vanilla sur chaque action. Tous les widgets ont 4 états : normal, survol, focus clavier (contour `accent` 1 px), désactivé.

| Widget                    | Apparence (d'après les captures)                                                                                                                                                                                                                                                                                                         | Comportement                                                                                                                    |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| **Bouton**                | Face `buttonFace`, contour `outline` 1 px, liseré bas `buttonShadow` 2 px (effet relief). Texte centré `textOnLight`. Pressé : liseré bas disparaît, contenu descend de 1 px. Variante **primaire** : face `accent`, liseré `accentDark`, texte `onAccentText`.                                                                          | Clic / Entrée.                                                                                                                  |
| **Toggle**                | Track rectangulaire ~ 2× la largeur du knob. **OFF** : knob clair à gauche, partie droite `toggleOffTrack` avec un « O ». **ON** : partie gauche `accent` avec un « I », knob clair à droite. Label **à droite** du toggle, même ligne.                                                                                                  | Clic sur le toggle **ou** sur le label. Animation courte du knob (≈ 100 ms) 🟡.                                                 |
| **Slider**                | Label **au-dessus** : `Nom : valeur` (ex. « Ray Tracing Render Distance: 16 chunks »). Track sur toute la largeur : partie gauche (jusqu'au knob) `buttonFace`, partie droite `panelBgDeep`, contour `outline`. Knob : rectangle clair vertical avec contour. **Crans** (petits traits verticaux) si le nombre de pas est faible (≤ 10). | Glisser, clic sur le track, flèches ←/→ = ±1 pas, Shift = ±10 pas. Valeur arrondie au `step`.                                   |
| **Dropdown**              | Label au-dessus. Boîte `buttonFace` pleine largeur, texte de la valeur à gauche, ▼ à droite.                                                                                                                                                                                                                                             | Ouvre une liste en surimpression (popup), scrollable si longue, élément courant surligné `accent`. Échap / clic dehors = ferme. |
| **Boutons segmentés** ✅  | Capture 5 (« Game mode », « Difficulty ») : rangée de boutons pleine largeur collés, segment actif fond `accent` + petit trait blanc sous le texte, autres `buttonFace`. Label au-dessus, description de la valeur choisie en dessous (si fournie). | Pour les enums de **2 à 4 valeurs** (au-delà : dropdown). Clic, flèches ←/→. |
| **Champ texte / nombre**  | Label au-dessus. Fond `panelBgDeep`, contour clair 1 px, texte blanc. Erreur : contour `error` + message sous le champ.                                                                                                                                                                                                                  | Validation à la frappe ; valeur invalide non appliquée. Champs nombre : seuls chiffres, `-`, `.`.                               |
| **Éditeur de liste**      | Label au-dessus, liste d'entrées (champ texte + bouton ✕), bouton « + Ajouter » en bas.                                                                                                                                                                                                                                                  | Ajout, suppression, réordonnancement 🟡.                                                                                        |
| **Éditeur de map** ✅      | Label au-dessus, une ligne par entrée : champ clé + valeur (toggle / dropdown / champ) + ✕, bouton « + Ajouter ». | Clé vide ou en double → erreur, bloque « Enregistrer ». |
| **Liste d'objets** ✅      | Une carte par élément (`#n`, ▲▼✕), un champ par propriété, « + Ajouter » crée un élément avec les valeurs par défaut. | Fichiers des autres mods : un sous-écran par élément (§8.3), ou via la map aplatie (properties). |
| **Texte multi-ligne** ✅   | Zone de texte ~ 58 px de haut, même style que le champ texte. | Entrée = nouvelle ligne ; Tab sort du champ. |
| **Autocomplétion** ✅      | Liste sous le champ (au-dessus s'il n'y a pas la place), 6 suggestions max, la sélection en `accent`. | ↑↓, Tab/Entrée pour accepter, Échap pour fermer. |
| **Couleur**               | Champ texte hex + pastille d'aperçu. ✅ Clic sur la pastille → **color picker** : carré saturation/luminosité, barre de teinte, barre d'alpha (si `alpha`), champ hex, ancienne/nouvelle couleur. | Appliqué en direct ; Annuler / Échap remet l'ancienne couleur. |
| **Stepper** ✅            | Bouton −, champ nombre, bouton +. | Clic, ↑↓, Maj = ×10, borné par `@Range`. |
| **Touche** ✅             | Bouton avec le nom de la touche ; en attente : fond `accent` « > Appuyez sur une touche < ». | Clic/Entrée puis touche ou bouton de souris ; Échap annule ; Suppr = aucune. |
| **Cases à cocher** ✅     | Case 10 px (`accent` + ✓ si cochée) + libellé, en colonnes si la ligne est large. | Clic, flèches, Espace/Entrée. |
| **Champ item/bloc** ✅    | Icône de l'item à gauche du texte, bouton grille à droite. Suggestions avec icônes. | Le bouton grille ouvre le **sélecteur** : recherche (nom traduit ou ID), grille d'icônes scrollable, survol = nom + ID, flèches + Entrée. |
| **Widget du mod** ✅      | Dessiné par le mod (`OptionWidget`). | Valeur validée et soumise à Enregistrer/Annuler comme le reste ; une exception du mod désactive le widget avec un message, sans crash. |
| **Ligne de navigation**   | Bouton pleine largeur avec icône à gauche et chevron `›` à droite (capture 3 « Member »).                                                                                                                                                                                                                                                | Ouvre une sous-section.                                                                                                         |
| **Onglets**               | En haut, onglet actif fond `accent`, autres `panelBg` avec contour.                                                                                                                                                                                                                                                                      | Clic, ou touches `[` `]` / Ctrl+Tab.                                                                                            |
| **Icône « ! »**           | Carré `warning` avec « ! » noir, à droite de la ligne du label (capture 2).                                                                                                                                                                                                                                                              | Tooltip : « Nécessite un redémarrage du jeu ».                                                                                  |
| **Bouton reset d'option** | Petite icône ↺ à droite de la ligne du label. **Visible seulement si valeur ≠ défaut.**                                                                                                                                                                                                                                                  | Remet la valeur par défaut (sans confirmation).                                                                                 |
| **Scrollbar**             | Fine, à droite du panneau, track sombre, poignée claire.                                                                                                                                                                                                                                                                                 | Molette, glisser.                                                                                                               |
| **Option désactivée**     | Label et widget en `textDisabled` / couleurs atténuées (capture 2 « Anti-Aliasing »).                                                                                                                                                                                                                                                    | Non interactive, tooltip expliquant pourquoi si fourni.                                                                         |
| **Tooltip** ✅ (Bedrock)  | Fond `panelBgDeep` assombri opaque, contour clair 1 px, ombre portée 2 px, marges 6/5 px, largeur max ~ 200 px. | Au survol du label (délai ~ 500 ms). **Placé sous l'élément survolé (sinon au-dessus), jamais dessus**, reste dans le panneau des options (ne déborde pas sur la colonne aperçu / la sidebar), ne recouvre jamais une liste de suggestions ouverte, et rien ne s'affiche quand la souris est sur cette liste. |

Indicateur « modifié » : 🟡 petit point `accent` à gauche du label quand la valeur ≠ défaut (en plus du bouton reset).

✅ DÉCIDÉ (implémentation UI, validé par Lucas) :
- Clé de traduction d'une config sans nom : `config.<modId>.<chemin>`.
- Valeurs d'enum affichées humanisées (`VERY_HIGH` → « Very High »).
- Champ invalide qu'on quitte → revient à la dernière valeur valide ; dans une liste, l'erreur reste affichée et bloque « Enregistrer ».
- Réordonnancement des listes : boutons ▲▼.
- Sidebar : boutons style capture 3, catégorie active en accent.
- Fond hors du panneau centré : `panelBgDeep` uni.
- « Redémarrage requis » : modale.
- Onglets : non utilisés (les sous-sections passent par la ligne de navigation).
- Option désactivée / lecture seule : drapeau `readOnly` du modèle (utilisé par l'éditeur universel pour les valeurs complexes).

### 10.4 Écrans

**A. Sélection de fichier** (si un mod a plusieurs fichiers de config)

- Barre de titre : `‹ <Nom du mod>`.
- Liste de grands boutons, un par fichier : nom lisible (ex. « Client », « Common ») + nom de fichier en petit gris + badge source (« Confee », « Forge », « Fichier »).
- Fichier en lecture seule (ex. SERVER) : bouton grisé + mention.

**B. Écran de config** (écran principal)

```
┌───────────────────────────────────────────────────────────────┐
│ ‹  <Nom du mod> — <Nom du fichier>                 [Rechercher]│  ← barre de titre (panelBg)
├────────────────────┬──────────────────────────────────────────┤
│ CATÉGORIES         │ [bandeau info si RAW_FILE]               │
│ ▌Général  (actif)  │ Label option 1                  ! ↺      │
│  Dash              │ [=========O-----------------]            │
│  Affichage         │ [I|■] Option toggle                      │
│                    │ Label dropdown                           │
│                    │ [ Normal                       ▼ ]       │
│                    │ …                               ▐ scroll │
├────────────────────┴──────────────────────────────────────────┤
│ [Réinitialiser tout]                  [Annuler] [Enregistrer]  │  ← barre du bas
└───────────────────────────────────────────────────────────────┘
```

- **Sidebar gauche** (capture 3) uniquement si ≥ 2 catégories ; sinon le panneau occupe toute la largeur. Catégorie active = fond `accent`. Options à la racine → catégorie « Général ».
- **Recherche** : filtre en direct sur nom + description de toutes les catégories ; les résultats s'affichent dans le panneau droit groupés par catégorie.
- **Panneau droit** : options dans l'ordre de déclaration ; label au-dessus pour slider/dropdown/champ, à côté pour toggle (comme les captures).
- **Barre du bas** :
  - « Réinitialiser tout » (bouton normal) → **modal de confirmation** → toutes les options du fichier reviennent au défaut (ou à l'original pour RAW_FILE).
  - « Annuler » → ferme sans sauvegarder.
  - « Enregistrer » (bouton **primaire** couleur accent) → valide, sauvegarde, ferme.
- **Mode de sauvegarde** ✅ DÉCIDÉ (Lucas) : les modifications sont **en attente** jusqu'à « Enregistrer ». Échap avec changements non enregistrés → modal « Enregistrer les modifications ? » [Enregistrer] [Ne pas enregistrer] [Annuler]. (Alternative : sauvegarde instantanée à chaque changement, comme Bedrock.)
- Après enregistrement, si au moins une option modifiée est `requiresRestart` → toast/modal « Certains changements nécessitent un redémarrage du jeu ».

**C. Modal (dialogue)**

- Panneau `panelBg` centré, contour, titre, texte, 2–3 boutons en bas (le bouton d'action principale en primaire). Fond derrière assombri.

**D. Fichier illisible / non supporté**

- Message « Confee ne peut pas lire ce fichier (<raison>) » + bouton « Ouvrir le fichier » (ouvre dans l'éditeur du système via l'API vanilla d'ouverture de fichier) + bouton « Ouvrir le dossier ».

### 10.5 Clavier et accessibilité

- Navigation complète au clavier : Tab / Shift+Tab entre widgets, flèches pour sliders et listes, Entrée/Espace pour activer, Échap = retour/fermer.
- Focus toujours visible.
- Narrateur vanilla : fournir les textes de narration des widgets.
- Respect du GUI scale et des petites résolutions (scroll partout, pas de chevauchement à 1280×720 en GUI scale 4 / auto).

### 10.6 Responsive

- Largeur du panneau max ~ 400 px GUI centré ; sidebar ~ 120 px. En dessous d'une largeur minimale, la sidebar se replie en **dropdown de catégorie** en haut. 🟡

### 10.7 Colonne d'aperçu ✅ DÉCIDÉ (Lucas)

- ✅ Survoler la liste de suggestions ne change pas l'aperçu (il reste sur le champ en cours d'édition).
- ✅ Les options `@Item` / `@Block` ont un aperçu automatique (l'item en grand, son nom, son ID) quand le dev n'en fournit pas.

- 3e colonne **à droite** du panneau (~ 150 px), présente seulement si au moins une option de l'écran déclare un aperçu.
- Montre l'option **survolée**, sinon celle qui a le focus clavier (reste affichée quand la souris part ailleurs).
- Contenu, tout optionnel et combinable : **image** (texture d'un resource pack ; `{value}` remplacé par la valeur en minuscules, ex. `quality_{value}.png`), **rendu personnalisé** (`PreviewRenderer`, classe client, créée seulement à l'affichage, erreurs attrapées → message), **texte** (texte brut avec codes `§`, ou clé de traduction).
- ✅ DÉCIDÉ (Lucas, 0.3.0) : `PreviewRenderer#render(..., PreviewContext context, delta)` : `context.value(chemin)` = valeur **en attente** (écran, pas encore enregistrée) d'une autre option de la même config, `context.theme()` = thème de l'écran. Les deux `render` sont des méthodes `default` (l'ancienne reste appelée par défaut : compatible binaire ; une lambda `PreviewRenderer` ne compile plus, il faut une classe).
- Écran trop étroit : colonne **masquée**.
- Les sections (tables TOML) dont le fichier porte un commentaire l'affichent en tooltip sur le bouton de catégorie / la ligne de navigation.

### 10.8 Autocomplétion ✅ DÉCIDÉ (Lucas)

- Sources : registre (`@Registry` ou raccourci typé) et/ou suggestions du mod (`@Suggestions`, `.suggestions(...)`). ✅ Les entrées des **mods tiers** sont incluses.
- ✅ Registres « de monde » (biomes, structures, dimensions…) : **en partie**, le registre du monde (serveur intégré, sinon ce que le serveur a envoyé ; datapacks inclus) ; **hors partie**, les fichiers `data/<ns>/<registre>/**.json` du jeu et de tous les mods (+ les 3 dimensions vanilla, générées par le preset). Résultats mis en cache par monde.
- ✅ Tags (si l'option les accepte) : en partie, les tags du registre ; hors partie, les fichiers `data/<ns>/tags/<registre>/**.json`.
- ✅ Icônes par type : items/blocs (l'item), entités (œuf d'apparition), enchantements (livre enchanté), effets (icône d'effet), fluides (seau). Biomes, sons, structures, dimensions : pas d'icône. Le sélecteur en grille existe pour les registres avec icônes (entrées sans icône : leurs initiales). L'aperçu automatique montre l'icône, le nom traduit (s'il existe) et l'ID ; un tag inconnu ou un ID inconnu s'affiche en erreur.
- Recherche tolérante : commence par > début de mot > contient > lettres dans l'ordre avec peu de trous (« dmnd » → diamond).
- Jusqu'à 50 résultats, 6 visibles, liste scrollable (molette, ↑↓, Page↑↓), Tab/Entrée accepte, Échap ferme, ↓ ouvre la liste même champ vide. Icônes d'items pour les registres item/bloc. La liste s'ouvre au-dessus du champ s'il n'y a pas la place en dessous.

### 10.9 Personnalisation par le dev ✅ DÉCIDÉ (Lucas)

- **Éléments décoratifs** (`Decoration`) : titre, séparateur, texte (info / avertissement / simple), image, bouton d'action (exécuté côté client, exception attrapée). Placés à leur rang parmi les options ; jamais écrits dans le fichier ; `visibleIf` possible.
- **Icônes** : catégories (sidebar) et sous-sections (ligne de navigation) : ID d'item ou texture.
- **Mise en page** (`Layout`) : largeur max du panneau (200–1000), largeur de l'aperçu (100–400), sidebar `AUTO`/`HIDDEN`, bannière en haut des options, fond en texture répétée.
- **Widgets du mod** : `@Widget(Classe)` sur le champ, ou `ConfeeWidgets.forOption(configId, chemin, fabrique)` / `forType(modId, type, fabrique)` depuis l'init client. Ordre de priorité : `@Widget` > `forOption` > `forType`. Configs Confee, configs Forge et fichiers bruts (`ConfeeWidgets.fileId`).
- Thème : tous les tokens (§11).

---

## 11. Personnalisation de la couleur par le dev ✅ DÉCIDÉ (principe)

- Le dev peut changer la couleur d'accent (le vert par défaut) de **son** écran :
  - annotations : `@Theme(accent = 0xFF2D7DD2)`
  - builder : `.accentColor(0xFF2D7DD2)`
- Confee dérive automatiquement les teintes (§10.2) et la couleur de texte lisible.
- La couleur s'applique à **tous les écrans de ce mod** (sélection de fichier comprise).
- Les écrans de l'éditeur universel (mods sans Confee) utilisent la couleur par défaut de Confee.
- ✅ DÉCIDÉ : le dev peut personnaliser **tous les tokens** du §10.2 (l'accent, plus les fonds, boutons, textes, outline, warning, error, toggleOffTrack). Les tokens dérivés de l'accent restent dérivés automatiquement, sauf si le dev les fixe explicitement.
- ✅ DÉCIDÉ : thème déclaré **dans le code uniquement** (pas de couleur via les métadonnées `fabric.mod.json` / `mods.toml` en V1).

---

## 12. Config de Confee lui-même (`config/confee.toml`)

Éditable via le bouton Config de Confee (écran généré par Confee — dogfooding).

✅ DÉCIDÉ (Lucas) : cet écran (et lui seul) utilise un thème **« coffee »** déclaré dans `ConfeeSettings` : accent marron café `#8F5628`, le reste en gris à peine chauds : fonds `#33302E` / `#201E1D`, boutons `#D6D2CE` (survol `#EAE6E2`, liseré `#5C5754`), outline `#1F1D1C`, textes `#FBF8F5` / `#221F1D` / désactivé `#75706C`, toggle OFF `#8F8A86`, barre de titre `#E9E6E3` / `#221F1D`. `warning` et `error` gardent leurs valeurs par défaut. L'éditeur universel et les mods qui utilisent Confee restent sur le thème par défaut (§10.2).

✅ DÉCIDÉ (Lucas, contenu) :

```toml
[general]
# Ajouter un bouton Config aux mods qui n'en ont pas (éditeur universel)
injectConfigButtons = true
# Sauvegarder une copie du fichier d'origine avant la première modification
backupExternalFiles = true

[external]
# Associations manuelles mod → fichiers (chemins relatifs à config/)
# fileMappings = { "xaerominimap" = ["xaerominimap.txt"] }
fileMappings = {}
# Mods à ignorer par l'éditeur universel
ignoredMods = []
# Mods dont l'écran de config est remplacé par l'éditeur Confee (§8.1)
forceConfeeEditor = []
```

✅ DÉCIDÉ (Lucas) : le modèle Confee n'a pas de type « map », donc `fileMappings` est une **liste de chaînes** `"modid=chemin"` (une entrée par fichier), éditable avec l'éditeur de liste :

```toml
fileMappings = ["xaerominimap=xaerominimap.txt"]
```

`injectConfigButtons` et `ignoredMods` sont marqués « redémarrage requis » (Mod Menu / Forge construisent la liste des boutons au démarrage). Scope CLIENT.

✅ DÉCIDÉ (Lucas) : `ignoredMods` et `forceConfeeEditor` ont des suggestions avec les logos des mods et un sélecteur en grille (comme `@ModId`). `ignoredMods` propose les mods que l'éditeur universel gère (fichiers trouvés ou configs Forge/NeoForge) plus ceux déjà ignorés ; `forceConfeeEditor` propose seulement les mods remplaçables (§8.1).

✅ Implémenté (étape 7) : découverte §8.2 points 1, 3, 4, 5 (`Confee.registerExternalFile`) ; le point 2 (specs Forge) exclut pour l'instant les fichiers gérés par Forge, ils seront éditables à l'étape 8. Sur Fabric, Mod Menu garde l'écran déclaré par un mod, sauf `forceConfeeEditor` ; un mod qui déclare une intégration Mod Menu **sans** écran reçoit maintenant l'éditeur Confee (mixin, §8.1). `forceConfeeEditor` n'est **pas** « redémarrage requis ».

---

## 13. Traductions (i18n)

- Interface de Confee : `assets/confee/lang/en_us.json` et `fr_fr.json`.
- Libellés des options d'une config Confee, ordre de résolution :
  1. clé de traduction `config.<modId>.<configName>.<chemin>` (et `.tooltip` pour la description) si présente dans les fichiers lang du mod,
  2. `@Name` / `.name(...)`,
  3. nom de la clé « humanisé » (`dashDistance` → « Dash Distance »).
- Description : `.tooltip` traduit → `@Comment` → rien.
- Configs Forge : utiliser la clé de traduction de la spec si présente, sinon humaniser.
- Fichiers bruts : humaniser la clé, description = commentaire du fichier.

---

## 14. Robustesse et erreurs

- Aucune exception de Confee ne doit faire crasher le jeu à cause d'un fichier de config : attraper, logger (`[Confee]` + mod + fichier + raison), dégrader proprement.
- Une config Confee mal déclarée par un dev (ex. `@Slider` sans `@Range`, type non supporté) → **exception claire au démarrage en environnement de dev** (message qui dit quoi corriger), **warning + champ ignoré en production**. 🟡
- ✅ DÉCIDÉ (Lucas) : **rapport dev** dans le log (environnement de dev uniquement) à l'enregistrement de chaque config : fichier, chaque option avec le widget choisi et ses contraintes, et les noms sans traduction (clés à copier dans `en_us.json`, lu depuis les ressources du mod, sans classe client).
- Fichier modifié à l'extérieur pendant que l'écran est ouvert → à l'enregistrement, détecter (date de modification) et demander « Le fichier a été modifié ailleurs, écraser ? ». 🟡
- Écriture atomique (tmp + rename) pour tous les fichiers.

---

## 15. Tests et validation

- **Mod de test** (source set `testmod`, non publié) qui déclare :
  - une config annotations avec **tous** les types et annotations,
  - une config builder équivalente,
  - une config avec `@Theme` d'une autre couleur.
- **Dossier de fichiers d'exemple** (un par format, avec commentaires, tables imbriquées, listes, valeurs avec guillemets, CRLF, BOM) copié dans `run/config/` pour tester l'éditeur universel.
- **Tests unitaires** (JUnit, sans lancer Minecraft) pour chaque adaptateur de format :
  - aller-retour sans modification = fichier identique octet pour octet,
  - modification d'une valeur = diff d'une seule valeur,
  - inférence des types.
- **Matrice de test manuel** avant chaque release : Fabric, Quilt, Forge, NeoForge (1.20.1) × (avec / sans Mod Menu sur Fabric) × (client / serveur dédié pour vérifier qu'aucune classe client n'est chargée).
- ✅ **Runs Gradle par loader** (dev uniquement, jamais publiés), captures automatiques sur les 4 loaders (`confee.ps1 shots-all`) :
  - Fabric et Forge : modules du build principal.
  - Quilt : build séparé `testenv/quilt` (Quilt Loom 1.7 + Quilt Loader + Quilted Fabric API) qui lance **le jar Fabric de release** (copié dans `run*/mods`).
  - NeoForge : build séparé `testenv/neoforge` (NeoGradle 6, son propre wrapper Gradle 8.1) qui compile les sources common + forge **inchangées** contre NeoForge 1.20.1-47.1.x et les lance.
- ✅ `fabric.mod.json` déclare `fabricloader >= 0.14.21` (pas la version de dev) : sinon Quilt (couche de compatibilité Fabric plus ancienne) refuse le jar.
- Vérifier la cohabitation avec : Mod Menu, Configured, Cloth Config, YACL (pas de bouton en double, pas d'écran écrasé).

---

## 16. Publication

- Noms de jar : `confee-<version>+<mc>-<loader>.jar` (ex. `confee-1.0.0+1.20.1-fabric.jar`). 🟡
- Modrinth & CurseForge :
  - jar Fabric → tags **Fabric + Quilt**,
  - jar Forge 1.20.1 → tags **Forge + NeoForge**,
  - catégorie « Library » + « Utility », environnement : client **et** serveur (serveur optionnel côté joueur 🟡).
- Publication automatisée (ex. plugin Gradle de publication Modrinth/CurseForge) 🟡.
- Maven pour les devs : voir plus bas (plus tard).
- Documentation dev (README + wiki) avec l'exemple « 10 lignes » en premier.

✅ DÉCIDÉ / implémenté (étape 10) :
- README développeur en **anglais** (`README.md`), `CHANGELOG.md`.
- Publication : plugin `me.modmuss50.mod-publish-plugin` 0.8.4, **essai à blanc par défaut** ; publication réelle seulement avec `CONFEE_PUBLISH=true` et les tokens en variables d'environnement (`MODRINTH_TOKEN`, `CURSEFORGE_TOKEN`). IDs des projets dans `gradle.properties` (Modrinth `itTlrYl0`, CurseForge `1817213`), surchargeables par `MODRINTH_PROJECT_ID` / `CURSEFORGE_PROJECT_ID`. Jar Fabric → Fabric + Quilt (requiert fabric-api, optionnel modmenu) ; jar Forge → Forge + NeoForge. CurseForge : client requis, serveur optionnel.
- ✅ DÉCIDÉ (Lucas, 2026-11-19) : type de release `beta` (`release_type` dans `gradle.properties`).
- ✅ DÉCIDÉ (Lucas, 2026-11-19) : la licence reste **LGPL-3.0** (licences « source disponible » type PolyForm Shield écartées).
- ✅ DÉCIDÉ (Lucas, 2026-11-19) : CurseForge, distribution par des tiers **autorisée** (launchers tiers via l'API).
- ✅ DÉCIDÉ (Lucas, 2026-11-19) : les captures Bedrock de `docs/ui-reference/` restent **en local**, hors du dépôt public (ignorées par Git).
- ✅ DÉCIDÉ (Lucas, 2026-11-19) : Maven pour les devs **plus tard** (V1.1) ; en attendant, Modrinth Maven (`maven.modrinth:confee:<version>`).
- Slug `confee` libre sur Modrinth (vérifié). CurseForge : à vérifier par Lucas (API à clé).
- Logo, URL du dépôt / page du mod : à fournir par Lucas (pas encore dans `fabric.mod.json` / `mods.toml`).

✅ Cohabitation (§15), vérifiée automatiquement en jeu (script de captures, mods en dev uniquement) : Mod Menu, Cloth Config et Minecraft gardent leur propre écran ; YACL n'a pas d'écran et Confee ne lui en ajoute pas ; Fabric et Forge. **Configured** n'est pas sur Modrinth → à tester à la main dans une instance CurseForge.
✅ DÉCIDÉ (Lucas, 2026-11-19) : **Configured gagne** pour les mods à config Forge/NeoForge. Sous Forge/NeoForge, Confee enregistre ses écrans en dernier (à l'ouverture du premier écran, après l'initialisation client de tous les mods) : Configured, ou tout mod qui enregistre un écran pendant son initialisation, garde le sien ; Confee l'enveloppe seulement (`forceConfeeEditor` marche toujours). Vérifié avec Configured 2.2.3 sur Fabric et NeoForge (sur NeoForge en dev : `-PwithConfigured` dans `testenv/neoforge`).

---

## 17. Ordre de développement conseillé 🟡

1. Squelette Gradle multi-loader / multi-version (1.20.1 seulement activée), mods qui se chargent sur les 4 loaders.
2. Modèle interne (`ConfigSchema`, `Option`, validation) + tests.
3. Adaptateur **TOML** (lecture/écriture Confee + édition chirurgicale) + tests.
4. API annotations → schema ; API builder → schema.
5. Thème + widgets de base (bouton, toggle, slider, champ, dropdown) + écran de config.
6. Intégration Mod Menu + liste des mods Forge pour les configs Confee.
7. Éditeur universel : découverte de fichiers + inférence de types sur TOML.
8. Intégration specs Forge/NeoForge.
9. Autres formats (JSON/JSON5, properties, YAML, cfg, INI, HOCON).
10. Recherche, i18n, accessibilité, polish, tests de cohabitation, publication.

---

## 18. Récapitulatif des points à faire valider par Lucas

Tous les points du récapitulatif ont été tranchés le 2026-11-19 (passés en ✅ dans leurs sections). Les couleurs du tableau §10.2 sont validées telles quelles.

---

## Sources consultées pour l'analyse

- YACL — https://modrinth.com/mod/yacl
- Fzzy Config (annotations, RequiresAction) — https://moddedmc.wiki/en/project/fzzy-config/latest/docs/config-concepts/Annotations
- Configured (MrCrayfish) — https://github.com/MrCrayfish/Configured
- Config Menus for Forge (Fuzs) — https://modrinth.com/mod/forge-config-screens
- Config Editor — https://modrinth.com/mod/config-editor
- Forge Config API Port — https://modrinth.com/mod/forge-config-api-port
- Konfig — https://modrinth.com/mod/konfig
- Night Config — https://github.com/TheElectronWill/night-config
- Stonecraft (Stonecutter + Architectury) — https://stonecraft.meza.gg/docs
- Compatibilité Forge/NeoForge 1.20.1 — https://modrinth.com/mod/lexforgefix
