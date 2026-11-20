# ImageOnMapReborn

🇬🇧 **English** | 🇵🇱 [Polski](README.pl.md)

[![Available on SpigotMC](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/spigot_vector.svg)](https://www.spigotmc.org/resources/imageonmapreborn.139800/)

A Minecraft plugin that puts pictures from the Internet onto maps – a from-scratch, **unofficial
successor of ImageOnMap** (which has not been updated since 2021). It has the same features, the same
commands and the same permissions, and on its first start it **moves every map of the old plugin over
by itself** – pictures on walls, in item frames and in player inventories keep working, nothing has to
be created again.

> This project is not affiliated with the authors of ImageOnMap or the zDevelopers team.
> Author: **Optiblur** – see [Authors and credits](#authors-and-credits).

- Server software: **Paper / Purpur / Spigot**
- Minecraft versions: **1.20.1 and newer** – tested on Paper **1.20.1**, **1.21.11** and **26.3**
- Java: 17+ for 1.20.1–1.20.4, 21+ for 1.20.5–1.21.x, 25+ for 26.x (whatever the server itself needs)
- Folia is not supported
- License: **GPL-3.0**

The ready-to-use `.jar` is on [SpigotMC](https://www.spigotmc.org/resources/imageonmapreborn.139800/) and in the **Releases** tab; you can also build it yourself (see
[Building from source](#building-from-source)).

### New in 1.2.2

- **Five new languages** – German, Spanish, French, Portuguese (Brazil) and Russian, besides English and
  Polish. The translations may not be perfect – corrections are welcome on GitHub.
- **Every player in their own language** – `per-player-language` is on for new installations, so each player
  sees messages and menus in the language of their game (other languages get the one of the `lang` option).
  Servers that already run the plugin keep their setting: set `per-player-language: true` to use it.
- The rename prompt accepts the word for "cancel" in every included language (e.g. `abbrechen`, `annuler`).

### New in 1.2.1

A security update, recommended for every server:
- **Safer animated GIFs** – a specially made GIF could make the server reserve gigabytes of memory. Only the
  part of each frame that lies on the picture is decoded now (like in web browsers), and GIFs whose frames
  claim more than 4 million pixels are used as still pictures.
- **Time limit for whole downloads** – a website sending a picture very slowly could keep a download going
  forever and block `/tomap` for everyone. A whole download now stops after 3 × `download.timeout-seconds`
  (60 s by default).
- **Long GIFs no longer hold up picture making** – a GIF with up to 2000 big frames could keep it busy for
  half a minute. GIFs that would need too much work are used as still pictures now.
- **Limits for new installations** – `map-player-limit: 200` and `max-image-pixels: 16000000`, so one picture
  can no longer use thousands of maps. Servers that already run the plugin keep their values (see
  [Configuration](#configuration-pluginsimageonmaprebornconfigyml)). The startup summary and `/maptool info`
  show the map limits, and the overview book in `/maps` now counts the limits given with permissions too.

### New in 1.2.0

- **English by default** – new installations start in English, Polish is one option away (`lang: pl-PL`).
  Servers that already run the plugin keep the language they have.
- **Startup summary in the console** – version, number of pictures, the upload folder, language, server and
  the update check at a glance:
  ```
  +------------------------------------------------------+
  | ImageOnMapReborn 1.2.0  by Optiblur                  |
  | Pictures on maps - the successor of ImageOnMap       |
  +------------------------------------------------------+
  | Images    12 (48 maps, 3 players)                    |
  | Uploads   2 pictures in upload/ (/tomap file:<name>) |
  | Language  en_US                                      |
  | Server    Paper 1.21.11                              |
  | Updates   checking SpigotMC for a new version...     |
  +------------------------------------------------------+
  ```
  A few seconds later the console says whether you run the latest version, or shows a box with the new
  version and its download link.
- **Pictures from the server's disk** – put a picture in `plugins/ImageOnMapReborn/upload/` and use
  `/tomap file:logo.png`, no need to upload it to a website first (see
  [Pictures from the server's disk](#pictures-from-the-servers-disk)).
- **`/maptool info`** – version, update status and statistics, in the game or in the console.

### New in 1.1.0

- **Dithering** – photos and color gradients look much better (see [Picture quality](#picture-quality-dithering)).
- **Animated GIFs** – they play on maps, in the hand and in item frames (see [Animated GIFs](#animated-gifs)).
- **Update check** – the console and OPs are told when a new version is out (can be turned off).
- `config.yml` is commented in English, and after updating it gets the new options by itself.

---

## Switching from the old ImageOnMap (step by step)

1. **Back up** the `plugins/ImageOnMap` folder and your world (maps live in `world/data/map_*.dat`).
2. Stop the server.
3. Delete **only the file** `plugins/ImageOnMap-x.x.x.jar`. **Do not delete the `plugins/ImageOnMap`
   folder** – the new plugin reads its data from there.
4. Put `ImageOnMapReborn-1.2.2.jar` into `plugins/`.
5. Start the server. The console will show:
   ```
   [ImageOnMapReborn] Found ImageOnMap data in plugins/ImageOnMap, importing it ...
   [ImageOnMapReborn] ImageOnMap import done: X player files, Y images (Z Minecraft maps) imported ...
   ```
6. Done. All old pictures are still displayed, `/maps` shows every player's maps, old *splatter*
   maps in inventories still hang the whole poster, and your permission setup (`imageonmap.*`, e.g. in
   LuckPerms) works unchanged.

What the import does:
- copies the players' map lists (`maps/<uuid>.yml`) and the picture files (`images/map<id>.png`) to
  `plugins/ImageOnMapReborn/`,
- carries over the settings shared with ImageOnMap: `map-global-limit`, `map-player-limit`,
  `limit-map-size-x/y`, `save-full-image`, `images-hostnames-whitelist`,
- **changes nothing in the old folder** – it stays as a backup. Once you have checked that
  everything works, you can delete it.

The import runs automatically only once (a `legacy-import.yml` file is created). The
`/maptool import` command (alias `/maptool migrate`) runs it again and only adds missing pictures –
there will be no duplicates.

The file format is **identical** to ImageOnMap's, so if needed you can go back to the old plugin by
copying `maps/` and `images/` back.

> If the old plugin is still installed, ImageOnMapReborn prints a big warning in the console – both
> plugins should not run at the same time.

### Updating ImageOnMapReborn (e.g. 1.2.1 → 1.2.2)

Stop the server, replace the old `ImageOnMapReborn-x.x.x.jar` with the new one and start the server. That's
all: maps, settings and translations stay. New options are added at the end of `config.yml` (the console
lists them), and the Polish comments written by 1.0.0 are replaced with English ones – your values and your
own comments are not changed. The language you use stays as well: a server that ran in Polish keeps
`lang: pl-PL`, only new installations start in English.

---

## Commands

| Command | Description |
|---|---|
| `/tomap <link\|file:name> [resize\|resize-stretched\|resize-covered] [width height] [dither\|nodither]` | Creates maps from a picture. Without a mode the picture keeps its size and is cut into as many maps as needed (centered). `resize` = one map, `resize 4 3` = a 4×3 map poster (`4x3` or `4*3` work too). `dither` / `nodither` turns dithering on or off for this picture. Animated GIFs become animated maps. `file:<name>` takes a picture from the upload folder (see [Pictures from the server's disk](#pictures-from-the-servers-disk)). |
| `/maps [player]` | Map browser (left-click – take a map, right-click – details: rename, delete, single poster pieces). A book shows statistics and limits. |
| `/givemap <player> [owner:]<name>` | Gives a map to a player. Works from the console and command blocks (the owner is then required), supports `@p`. |
| `/maptool new …` | Same as `/tomap`. |
| `/maptool list [player]` | Lists the pictures (click a name to get the map). |
| `/maptool get [player:]<name>` | Gives you a map again. |
| `/maptool getremaining` | Hands out maps that did not fit in your inventory (alias `getrest`). |
| `/maptool explore [player]` | Same as `/maps`. |
| `/maptool give …` | Same as `/givemap`. |
| `/maptool rename [player:]<name> <new name>` | Renames a picture (names with spaces go in quotes). |
| `/maptool update [player:]<name> <new link\|file:name> [stretched\|covered] [dither\|nodither]` | Draws a new picture (or an animated GIF) on the same maps (posters already hanging change right away). |
| `/maptool delete [player:]<name>` | Deletes a picture (asks for confirmation). |
| `/maptool import [folder]` | Imports from the old ImageOnMap again. |
| `/maptool reload` | Reloads `config.yml` and the translations. |
| `/maptool info` | Version, update status and statistics (alias `version`). In the console it is the same framed summary as at startup. |

Map names with spaces: `"A very nice map"`, another player's map: `Player:"A very nice map"` –
exactly like in ImageOnMap.

### Pictures from the server's disk

Instead of a link, `/tomap` and `/maptool update` can take a picture from the
`plugins/ImageOnMapReborn/upload/` folder (it is created on the first start):

```
/tomap file:logo.png resize 4 3
/maptool update "Spawn sign" file:event/day1.png
```

- PNG, JPEG, GIF (also animated), BMP and WebP work; subfolders are written with `/`.
- File names must not contain spaces. Tab completion lists the pictures of the folder.
- Only files inside the upload folder can be used (`file:../something` is refused).
- Players need the `imageonmap.localfiles` permission (OPs have it by default). The website whitelist does
  not apply, the size limits (`max-image-pixels`, `limit-map-size-x/y`, `download.max-size-mb`) do.

### Splatter maps (posters)

A poster is given as one shiny "Poster" item (called a splatter map in ImageOnMap). Put up empty item
frames (on a wall, a floor or a ceiling) and right-click the **bottom-left** frame with it – the whole
poster is hung at once. On floors and ceilings the picture is rotated so it reads correctly from where
you stand. **Sneak and hit** any piece of the poster to take it all down and get the poster item back.

Item frames holding pictures are protected from mobs, arrows and explosions – only players can take a
map down.

### Picture quality (dithering)

Minecraft maps can only show about 200 colors. Without dithering every pixel takes the closest of them,
which gives flat areas and visible stripes in skies, faces and color gradients. With dithering the plugin
mixes the available colors in tiny dots, which looks much closer to the original from a few blocks away.

- `floyd-steinberg` (default) – the best quality for photos,
- `ordered` – a regular dot pattern (used for animated GIFs, because it does not flicker),
- `none` – no dithering, like the old ImageOnMap; best for pixel art, logos and text.

The server default is the `dithering` option in `config.yml`; a player can choose for one picture by adding
`dither` or `nodither` to the command, e.g. `/tomap https://example.com/logo.png resize nodither`.
Pictures created before 1.1.0 are not changed (use `/maptool update` to draw them again).

### Animated GIFs

An animated GIF given to `/tomap` or `/maptool update` becomes an animated map or poster: it plays in the
hand and in item frames, all pieces of a poster stay in sync, and it keeps playing after a restart.

- The animation is limited to `animated-gifs.max-maps` maps (16 by default, e.g. a 4×4 poster) and
  `animated-gifs.max-frames` frames (100; longer GIFs skip frames but keep their length). Bigger GIFs are
  used as a still picture (their first frame) and the player is told why.
- GIFs that would take too long to read (roughly more than 125 frames of 2000 × 2000 px, or 2000 frames of
  500 × 500 px) are used as a still picture too.
- Maps in item frames are normally updated by the server only twice per second, so the plugin sends
  the frames itself to players near the frame, at most `animated-gifs.max-fps` times per second (10).
- Frames are kept in memory only while someone is looking at the animation (16 KB per frame and map) and
  are dropped after 5 minutes without viewers.
- The `imageonmap.animated` permission (everyone by default) allows making animations; without it a GIF
  becomes a still picture. `animated-gifs.enabled: false` turns animations off for the whole server.
- The frames are saved in `images/map<id>.anim`. The `images/map<id>.png` file keeps the first frame, so
  the original ImageOnMap still shows a picture if you ever go back.

---

## Permissions

The same as in ImageOnMap. By default every player has the "user" permissions, while moderation
permissions (`…other`), `give` and the administrative ones are for OPs only.

| Permission | Default | Description |
|---|---|---|
| `imageonmap.*` | everyone | All user permissions (no moderation or admin ones) |
| `imageonmap.new` (`imageonmap.userender`) | everyone | `/tomap` |
| `imageonmap.list`, `imageonmap.explore` | everyone | `/maptool list`, `/maps` |
| `imageonmap.get` | everyone | Getting your own maps |
| `imageonmap.rename` | everyone | Renaming your own maps |
| `imageonmap.delete` | everyone | Deleting your own maps |
| `imageonmap.update` | everyone | `/maptool update` on your own maps |
| `imageonmap.placesplattermap` | everyone | Hanging posters with the poster item |
| `imageonmap.removesplattermap` | everyone | Taking down a whole poster (sneak + hit) |
| `imageonmap.placeinvisiblesplattermap` | everyone | Item frames under a poster become invisible |
| `imageonmap.bypasswhitelist` | everyone | Ignores `images-hostnames-whitelist` (remove it to enforce the list) |
| `imageonmap.animated` | everyone | Animated GIFs become animated maps (new) |
| `imageonmap.listother`, `imageonmap.exploreother` | OP | Viewing other players' maps |
| `imageonmap.getother` | OP | Getting other players' maps |
| `imageonmap.renameother` | OP | Renaming other players' maps (new) |
| `imageonmap.deleteother` | OP | Deleting other players' maps |
| `imageonmap.updateother` | OP | `/maptool update` on other players' maps |
| `imageonmap.give` | OP | `/givemap` |
| `imageonmap.administrative` | OP | `/maptool import`, `/maptool reload`, `/maptool info` |
| `imageonmap.notifyupdate` | OP | Told about a new plugin version when joining (new) |
| `imageonmap.localfiles` | OP | Pictures from the upload folder: `/tomap file:<name>` (new) |
| `imageonmap.bypasssize` | OP | Ignores `limit-map-size-x/y` |
| `imageonmap.bypassmaplimit` | OP | Ignores `map-player-limit` and `imageonmap.maplimit.<n>` |
| `imageonmap.bypassimagelimit` | OP | Ignores `imageonmap.imagelimit.<n>` |
| `imageonmap.maplimit.<n>` | – | At most `<n>` Minecraft maps per player (the highest value wins) |
| `imageonmap.imagelimit.<n>` | – | At most `<n>` pictures per player |

---

## Configuration (`plugins/ImageOnMapReborn/config.yml`)

| Option | Default | Description |
|---|---|---|
| `lang` | `en-US` | Language (`en-US`, `pl-PL`, `de-DE`, `es-ES`, `fr-FR`, `pt-BR`, `ru-RU`; empty = system language) |
| `per-player-language` | `true` | Messages in each player's client language (`false` on servers installed before 1.2.2) |
| `map-global-limit` / `map-player-limit` | `0` / `200` | Map limit for the server / per player (0 = no limit; OPs have no player limit) |
| `limit-map-size-x` / `limit-map-size-y` | `0` | Maximum picture size in pixels |
| `save-full-image` | `false` | Also saves the full picture next to its pieces |
| `images-hostnames-whitelist` | `[]` | Allowed websites (e.g. `i.imgur.com`, `*.discordapp.com`) |
| `download.timeout-seconds` | `20` | Time limit for connecting and reading; a whole download may take 3× as long |
| `download.max-size-mb` | `25` | Maximum size of a downloaded file |
| `download.block-private-addresses` | `true` | Blocks links to local networks (protects the server) |
| `max-image-pixels` | `16000000` | Protects the server's RAM from gigantic pictures (16 000 000 ≈ 4000 × 4000 px) |
| `smooth-scaling` | `true` | Smooth downscaling of photos (`false` = like the old plugin) |
| `dithering` | `floyd-steinberg` | `floyd-steinberg`, `ordered` or `none` – see [Picture quality](#picture-quality-dithering) |
| `animated-gifs.enabled` | `true` | Animated GIFs play on maps (`false` = only the first frame) |
| `animated-gifs.max-frames` | `100` | Most frames of one animation |
| `animated-gifs.max-maps` | `16` | Largest animation, in maps |
| `animated-gifs.max-fps` | `10` | Most frame changes per second (1–20) |
| `protect-item-frames` | `true` | Protects item frames with pictures from mobs and explosions |
| `legacy-import.enabled` / `legacy-import.folder` | `true` / `ImageOnMap` | Automatic import of the old data |
| `check-for-updates` | `true` | Looks for a new version on SpigotMC at startup and every 12 hours |

> The defaults of `map-player-limit` (200) and `max-image-pixels` (16 000 000) are those of new installations
> since 1.2.1. Servers that installed an older version keep the values of their `config.yml` (often `0` and
> `40000000`) – set them yourself to get the same protection. Players can also get their own limits with the
> `imageonmap.maplimit.<n>` and `imageonmap.imagelimit.<n>` permissions.

All texts are in `plugins/ImageOnMapReborn/lang/` (`en_US.yml`, `pl_PL.yml`, `de_DE.yml`...) – you can change them
freely (colors `&a`, `&#RRGGBB`), and add a language by copying a file as e.g. `it_IT.yml`.

---

## What is better than in ImageOnMap

- works on 1.20.1 → 26.x (the old plugin officially stopped at 1.18),
- **WebP** support (besides PNG, JPEG, GIF, BMP),
- **animated GIFs** on maps and posters,
- **dithering** and better quality when photos are scaled down,
- more reliable loading of pictures after a restart (old versions could show a blank map until the
  player switched hotbar slots),
- server protection: links to local networks are blocked, download size and memory are limited,
- 7 languages (English, Polish, German, Spanish, French, Portuguese, Russian), each player in their own, editable language files,
- renaming from the GUI through the chat (instead of a sign), `imageonmap.renameother` permission,
- no statistics (bStats) – the `collect-data` option of the old config is ignored. The update check only
  asks SpigotMC (or GitHub) for the newest version number, sends no data about the server, never
  downloads anything by itself, and can be turned off with `check-for-updates: false`.

---

## Building from source

Requirements: JDK 17+ (and `unzip` on Linux and macOS). Maven does not have to be installed: the Maven
Wrapper downloads it the first time and checks its checksum.

```bash
./mvnw package
```

On Windows run `mvnw.cmd package`, with `JAVA_HOME` set to a JDK 17+ (for example
`set JAVA_HOME=C:\Program Files\Java\jdk-21`).

Result: `target/ImageOnMapReborn-1.2.2.jar` (it contains the TwelveMonkeys library for WebP, moved to
the `pl.iomreborn.lib` package so it cannot clash with other plugins). `./mvnw test` runs the unit tests
(ImageOnMap data format, picture cutting, poster geometry, command parser, decoding, dithering, GIF frames,
animation files, download limits, map limits, config updates, update check, console box, upload folder).
GitHub Actions builds and tests every push on Java 17, 21 and 25 (and on Windows); the jar of each run can be
downloaded from its page.

---

## Authors and credits

- **Project author:** Optiblur – idea, requirements, decisions and testing.
- **Testing:** besides the unit tests, the plugin was run on Paper 1.20.1, 1.21.11 and 26.3 servers
  (old data import, map display, console commands, dithering, GIF frames, config update). For 1.1.0 a
  test client also joined a Paper 1.20.1 server and counted the map updates it received: animations in the
  hand and in item frames, still pictures and the update notification.
- **ImageOnMap** – thanks to the creators of the original, which the features, commands, permissions
  and data format follow: Moribus (2013), ProkopyL (2015), Amaury Carrade (2016–2021),
  Vlammar (2019–2021) and all contributors of the
  [zDevelopers](https://github.com/zDevelopers/ImageOnMap) team.

ImageOnMapReborn contains no code of ImageOnMap or QuartzLib, and all message texts were written from
scratch. Only the file format, the command and permission names and the config options match the
original – so that migrating takes no work at all.

## License

Copyright (C) 2026 Optiblur

This program is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License version 3** as published by the Free Software Foundation. It is
distributed without any warranty. The full license text is in the [LICENSE](LICENSE) file.

The [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys) library bundled in the `.jar`
(WebP reading) is licensed under BSD-3-Clause – its text is in
`src/main/resources/META-INF/LICENSE-TwelveMonkeys.txt` and inside the `.jar` itself.

---

<sub>Made by Optiblur, with the help of Claude (Anthropic).</sub>
