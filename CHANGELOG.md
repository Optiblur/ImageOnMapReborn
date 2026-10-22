# Changelog

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
