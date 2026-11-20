# Changelog

## 1.2.2 – Five new languages

### Added
- **German, Spanish, French, Portuguese (Brazil) and Russian** translations (`de_DE.yml`, `es_ES.yml`,
  `fr_FR.yml`, `pt_BR.yml`, `ru_RU.yml`), with the plural rules of Russian and Portuguese. They are copied to
  `plugins/ImageOnMapReborn/lang/` on every server, also the ones that already run the plugin. The
  translations may not be perfect – corrections are welcome on GitHub.
- The rename prompt accepts the word for "cancel" in every included language (`abbrechen`, `cancelar`,
  `annuler`, `отмена`), besides `cancel`, `anuluj` and `-`.

### Changed
- **`per-player-language: true` for new installations**, so each player sees messages and menus in the
  language of their game; languages without a translation get the one of the `lang` option. Existing
  `config.yml` files keep their value – set it to `true` yourself to use it.
- The comment of the `lang` option in `config.yml` lists all the languages (existing files get the new
  comment by themselves).

## 1.2.1 – Safer GIFs, downloads and limits

A security update: the first three problems below could be caused by any player with the default permissions,
so updating is recommended for every server.

### Fixed
- **Animated GIFs**: a specially made GIF smaller than 1 KB could make the server reserve gigabytes of
  memory, because the frames of a GIF may claim to be far bigger than the picture itself. Frames are now cut
  to the size of the GIF before they are decoded, like in web browsers, and a GIF whose frames claim more
  than 4 million pixels is used as a still picture (the player is told why).
- **Downloads**: `download.timeout-seconds` only limited every single read, so a website sending a picture
  (or its headers) a little at a time could keep a download going forever. The plugin prepares only two
  pictures at a time, so two such links blocked `/tomap` and `/maptool update` for everyone until a restart.
  A whole download, redirects included, now stops after three times `download.timeout-seconds` (60 s by
  default).
- **Long animated GIFs**: a GIF within every limit, with up to 2000 big frames, could keep one of the two
  threads that prepare pictures busy for more than half a minute. GIFs that would need too much work (roughly
  more than 125 frames of 2000 × 2000 px, or 2000 frames of 500 × 500 px) are used as a still picture now, and
  the player is told why.
- **`/maps` overview**: the book with the statistics ignored the `imageonmap.maplimit.<n>` and
  `imageonmap.imagelimit.<n>` permissions and `imageonmap.bypassmaplimit`, so it could show "no limit". It now
  shows the limits that really apply (the same ones that are checked when a picture is made; for players who
  are offline only those of `config.yml`).

### Changed
- **Limits for new installations**: `map-player-limit: 200` (OPs are not limited) and
  `max-image-pixels: 16000000` (about 4000 × 4000 px). Before, one picture could use thousands of maps and a lot
  of memory. Existing `config.yml` files keep their values – set them yourself to get the same protection –
  and a migration from ImageOnMap still takes the old plugin's limits.
- The startup summary in the console and `/maptool info` show the map limits; a server without any is pointed
  out.
- The comments above `download.timeout-seconds` and `max-image-pixels` in `config.yml` explain the new limits.
  Existing files get the new comments by themselves; your values are kept.

### For developers
- Maven Wrapper (`mvnw`, `mvnw.cmd`): building only needs a JDK 17+, Maven is downloaded and checked by itself.
- GitHub Actions builds the plugin and runs all tests on every push, on Java 17, 21 and 25 and on Windows.

## 1.2.0 – Pictures from the server's disk & /maptool info

### New
- **Pictures from the server's disk**: `/tomap file:<name>` and `/maptool update <map> file:<name>` take a
  picture from `plugins/ImageOnMapReborn/upload/` (created on the first start, with a short `README.txt`), so
  it does not have to be uploaded to a website first. Subfolders work (`file:event/day1.png`), tab completion
  lists the pictures, and files outside the folder are refused. New `imageonmap.localfiles` permission (OPs
  by default). The size limits still apply, the website whitelist does not.
- **`/maptool info`** (alias `version`): the version, the update status and statistics – chat lines for
  players, a framed summary in the console. It needs `imageonmap.administrative`.
- **Startup summary in the console**: version, pictures, upload folder, language, server and update check in
  one framed box. A few seconds later the update check says whether the latest version runs, or shows a box
  with the new version and its download link.

### Changed
- New installations start in English (`lang: en-US`). Existing `config.yml` files keep their language; only
  the comment above the option is updated.

## 1.1.0 – Animated GIFs & Dithering

### New
- **Dithering**: maps only have about 200 colors, so pictures are now drawn by mixing them in tiny dots.
  Photos, skies and color gradients look much closer to the original. Three methods in `config.yml`
  (`dithering`): `floyd-steinberg` (default), `ordered` and `none` (exact colors, like ImageOnMap – best
  for pixel art and logos). Players can choose for one picture with `dither` / `nodither` in `/tomap`
  and `/maptool update`.
- **Animated GIFs** become animated maps and posters. They play in the hand and in item frames, all pieces
  of a poster stay in sync, and they keep playing after a restart. Maps in item frames are only refreshed
  twice per second by the server, so the plugin sends the frames itself to nearby players.
  New options `animated-gifs.enabled`, `max-frames` (100), `max-maps` (16) and `max-fps` (10), and the
  `imageonmap.animated` permission (everyone by default). Frames are kept in memory only while someone
  is watching. The PNG files keep the first frame, so the data stays readable by ImageOnMap.
- **Update check**: on startup and every 12 hours the plugin asks SpigotMC (or GitHub) for the newest
  version number. A new version is announced in the console and to players with
  `imageonmap.notifyupdate` (OPs). Nothing is downloaded by itself; `check-for-updates: false` turns it off.

### Changed
- `config.yml` and the header of the language files are commented in English. On updating, the new
  options are added to an existing `config.yml` automatically and the Polish comments of 1.0.0 are
  replaced with English ones (values and your own comments are kept).

### Fixed
- Maps in item frames could take up to 2.5 seconds to show a new picture after `/maptool update` (or
  their picture after a restart); it is now sent as soon as it is ready (about 0.3 s instead of 1 s in tests).

## 1.0.0

- First release: an unofficial, from-scratch successor of ImageOnMap for Minecraft 1.20.1+, with the same
  commands, permissions and data format, and an automatic import of the old plugin's maps.
