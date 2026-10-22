# ImageOnMapReborn

🇬🇧 **English** | 🇵🇱 [Polski](README.pl.md)

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

The ready-to-use `.jar` is in the **Releases** tab; you can also build it yourself (see
[Building from source](#building-from-source)).

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
4. Put `ImageOnMapReborn-1.1.0.jar` into `plugins/`.
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

### Updating ImageOnMapReborn (e.g. 1.0.0 → 1.1.0)

Stop the server, replace the old `ImageOnMapReborn-x.x.x.jar` with the new one and start the server. That's
all: maps, settings and translations stay. New options are added at the end of `config.yml` (the console
lists them), and the Polish comments written by 1.0.0 are replaced with English ones – your values and your
own comments are not changed.

---

## Commands

| Command | Description |
|---|---|
| `/tomap <link> [resize\|resize-stretched\|resize-covered] [width height] [dither\|nodither]` | Creates maps from a picture. Without a mode the picture keeps its size and is cut into as many maps as needed (centered). `resize` = one map, `resize 4 3` = a 4×3 map poster (`4x3` or `4*3` work too). `dither` / `nodither` turns dithering on or off for this picture. Animated GIFs become animated maps. |
| `/maps [player]` | Map browser (left-click – take a map, right-click – details: rename, delete, single poster pieces). A book shows statistics and limits. |
| `/givemap <player> [owner:]<name>` | Gives a map to a player. Works from the console and command blocks (the owner is then required), supports `@p`. |
| `/maptool new …` | Same as `/tomap`. |
| `/maptool list [player]` | Lists the pictures (click a name to get the map). |
| `/maptool get [player:]<name>` | Gives you a map again. |
| `/maptool getremaining` | Hands out maps that did not fit in your inventory (alias `getrest`). |
| `/maptool explore [player]` | Same as `/maps`. |
| `/maptool give …` | Same as `/givemap`. |
| `/maptool rename [player:]<name> <new name>` | Renames a picture (names with spaces go in quotes). |
| `/maptool update [player:]<name> <new link> [stretched\|covered] [dither\|nodither]` | Draws a new picture (or an animated GIF) on the same maps (posters already hanging change right away). |
| `/maptool delete [player:]<name>` | Deletes a picture (asks for confirmation). |
| `/maptool import [folder]` | Imports from the old ImageOnMap again. |
| `/maptool reload` | Reloads `config.yml` and the translations. |

Map names with spaces: `"A very nice map"`, another player's map: `Player:"A very nice map"` –
exactly like in ImageOnMap.

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
| `imageonmap.administrative` | OP | `/maptool import`, `/maptool reload` |
| `imageonmap.notifyupdate` | OP | Told about a new plugin version when joining (new) |
| `imageonmap.bypasssize` | OP | Ignores `limit-map-size-x/y` |
| `imageonmap.bypassmaplimit` | OP | Ignores `map-player-limit` and `imageonmap.maplimit.<n>` |
| `imageonmap.bypassimagelimit` | OP | Ignores `imageonmap.imagelimit.<n>` |
| `imageonmap.maplimit.<n>` | – | At most `<n>` Minecraft maps per player (the highest value wins) |
| `imageonmap.imagelimit.<n>` | – | At most `<n>` pictures per player |

---

## Configuration (`plugins/ImageOnMapReborn/config.yml`)

| Option | Default | Description |
|---|---|---|
| `lang` | `pl-PL` | Language (`pl-PL`, `en-US`; empty = system language). **Set `en-US` for English.** |
| `per-player-language` | `false` | Messages in each player's client language |
| `map-global-limit` / `map-player-limit` | `0` | Map limit for the server / per player (0 = no limit) |
| `limit-map-size-x` / `limit-map-size-y` | `0` | Maximum picture size in pixels |
| `save-full-image` | `false` | Also saves the full picture next to its pieces |
| `images-hostnames-whitelist` | `[]` | Allowed websites (e.g. `i.imgur.com`, `*.discordapp.com`) |
| `download.timeout-seconds` | `20` | Download time limit |
| `download.max-size-mb` | `25` | Maximum size of a downloaded file |
| `download.block-private-addresses` | `true` | Blocks links to local networks (protects the server) |
| `max-image-pixels` | `40000000` | Protects the server's RAM from gigantic pictures |
| `smooth-scaling` | `true` | Smooth downscaling of photos (`false` = like the old plugin) |
| `dithering` | `floyd-steinberg` | `floyd-steinberg`, `ordered` or `none` – see [Picture quality](#picture-quality-dithering) |
| `animated-gifs.enabled` | `true` | Animated GIFs play on maps (`false` = only the first frame) |
| `animated-gifs.max-frames` | `100` | Most frames of one animation |
| `animated-gifs.max-maps` | `16` | Largest animation, in maps |
| `animated-gifs.max-fps` | `10` | Most frame changes per second (1–20) |
| `protect-item-frames` | `true` | Protects item frames with pictures from mobs and explosions |
| `legacy-import.enabled` / `legacy-import.folder` | `true` / `ImageOnMap` | Automatic import of the old data |
| `check-for-updates` | `true` | Looks for a new version on SpigotMC at startup and every 12 hours |

All texts are in `plugins/ImageOnMapReborn/lang/en_US.yml` and `pl_PL.yml` – you can change them
freely (colors `&a`, `&#RRGGBB`), and add a language by copying a file as e.g. `de_DE.yml`.

---

## What is better than in ImageOnMap

- works on 1.20.1 → 26.x (the old plugin officially stopped at 1.18),
- **WebP** support (besides PNG, JPEG, GIF, BMP),
- **animated GIFs** on maps and posters,
- **dithering** and better quality when photos are scaled down,
- more reliable loading of pictures after a restart (old versions could show a blank map until the
  player switched hotbar slots),
- server protection: links to local networks are blocked, download size and memory are limited,
- English and Polish translations, editable language files,
- renaming from the GUI through the chat (instead of a sign), `imageonmap.renameother` permission,
- no statistics (bStats) – the `collect-data` option of the old config is ignored. The update check only
  asks SpigotMC (or GitHub) for the newest version number, sends no data about the server, never
  downloads anything by itself, and can be turned off with `check-for-updates: false`.

---

## Building from source

Requirements: JDK 17+ and Maven 3.9+.

```bash
mvn package
```

Result: `target/ImageOnMapReborn-1.1.0.jar` (it contains the TwelveMonkeys library for WebP, moved to
the `pl.iomreborn.lib` package so it cannot clash with other plugins). `mvn test` runs the unit tests
(ImageOnMap data format, picture cutting, poster geometry, command parser, decoding, dithering, GIF frames,
animation files, config updates, update check).

---

## Authors and credits

- **Project author:** optiblur – idea, requirements, decisions and testing.
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
