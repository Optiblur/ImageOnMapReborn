# ImageOnMapReborn

🇬🇧 [English](README.md) | 🇵🇱 **Polski**

Plugin do Minecrafta, który wczytuje obrazek z internetu na mapy – napisany od nowa,
**nieoficjalny następca ImageOnMap** (nierozwijanego od 2021 roku). Ma te same funkcje, te same
komendy i te same uprawnienia, a przy pierwszym starcie **sam przenosi wszystkie mapy ze starego
pluginu** – obrazy na ścianach, w ramkach i w ekwipunkach graczy działają dalej, nic nie trzeba
tworzyć od nowa.

> Projekt nie jest powiązany z autorami ImageOnMap ani zespołem zDevelopers.
> Autor: **Optiblur** – zobacz [Autorzy i podziękowania](#autorzy-i-podziękowania).

- Silniki: **Paper / Purpur / Spigot**
- Wersje Minecrafta: **1.20.1 i nowsze** – przetestowane na Paper **1.20.1**, **1.21.11** i **26.3**
- Java: 17+ dla 1.20.1–1.20.4, 21+ dla 1.20.5–1.21.x, 25+ dla 26.x (tak jak wymaga sam serwer)
- Folia nie jest obsługiwana
- Licencja: **GPL-3.0**

Gotowy plik `.jar` jest w zakładce **Releases**; można też zbudować go samemu (sekcja
[Budowanie ze źródeł](#budowanie-ze-źródeł)).

---

## Przejście ze starego ImageOnMap (krok po kroku)

1. **Zrób kopię zapasową** folderu `plugins/ImageOnMap` i świata (mapy są w `world/data/map_*.dat`).
2. Wyłącz serwer.
3. Usuń **tylko plik** `plugins/ImageOnMap-x.x.x.jar`. **Folderu `plugins/ImageOnMap` nie usuwaj** –
   nowy plugin czyta z niego dane.
4. Wrzuć `ImageOnMapReborn-1.0.0.jar` do `plugins/`.
5. Włącz serwer. W konsoli pojawi się:
   ```
   [ImageOnMapReborn] Found ImageOnMap data in plugins/ImageOnMap, importing it ...
   [ImageOnMapReborn] ImageOnMap import done: X player files, Y images (Z Minecraft maps) imported ...
   ```
6. Gotowe. Wszystkie stare obrazy wyświetlają się dalej, `/maps` pokazuje mapy każdego gracza,
   stare mapy *splatter* z ekwipunków nadal wieszają cały plakat, a uprawnienia z LuckPerms
   (`imageonmap.*`) działają bez zmian.

Co dokładnie robi import:
- kopiuje listy map graczy (`maps/<uuid>.yml`) i pliki obrazów (`images/map<id>.png`) do
  `plugins/ImageOnMapReborn/`,
- przenosi ustawienia wspólne z ImageOnMap: `map-global-limit`, `map-player-limit`,
  `limit-map-size-x/y`, `save-full-image`, `images-hostnames-whitelist`,
- **niczego nie zmienia w starym folderze** – zostaje jako kopia zapasowa. Po sprawdzeniu, że
  wszystko działa, można go usunąć.

Import wykonuje się automatycznie tylko raz (powstaje plik `legacy-import.yml`). Komenda
`/maptool import` (alias `/maptool migrate`) uruchamia go ponownie i dokłada tylko brakujące
obrazy – duplikatów nie będzie.

Format plików jest **identyczny** jak w ImageOnMap, więc w razie potrzeby można wrócić do starego
pluginu, kopiując `maps/` i `images/` z powrotem.

> Jeśli stary plugin jest nadal zainstalowany, ImageOnMapReborn wypisze w konsoli duże ostrzeżenie –
> oba pluginy nie powinny działać jednocześnie.

---

## Komendy

| Komenda | Opis |
|---|---|
| `/tomap <link> [resize\|resize-stretched\|resize-covered] [szer. wys.]` | Tworzy mapy z obrazka. Bez trybu obraz ma oryginalny rozmiar i jest cięty na tyle map, ile trzeba (wyśrodkowany). `resize` = jedna mapa, `resize 4 3` = plakat 4×3 mapy (można też `4x3` lub `4*3`). |
| `/maps [gracz]` | Menu z mapami (LPM – weź mapę, PPM – szczegóły: zmiana nazwy, usuwanie, pojedyncze części plakatu). Książka ze statystykami i limitami. |
| `/givemap <gracz> [właściciel:]<nazwa>` | Daje mapę graczowi. Działa z konsoli i bloków poleceń (wtedy właściciel jest wymagany), obsługuje `@p`. |
| `/maptool new …` | To samo co `/tomap`. |
| `/maptool list [gracz]` | Lista obrazów (kliknij nazwę, żeby dostać mapę). |
| `/maptool get [gracz:]<nazwa>` | Daje ponownie mapę. |
| `/maptool getremaining` | Odbiera mapy, które nie zmieściły się w ekwipunku (alias `getrest`). |
| `/maptool explore [gracz]` | To samo co `/maps`. |
| `/maptool give …` | To samo co `/givemap`. |
| `/maptool rename [gracz:]<nazwa> <nowa nazwa>` | Zmiana nazwy (nazwy ze spacjami w cudzysłowie). |
| `/maptool update [gracz:]<nazwa> <nowy link> [stretched\|covered]` | Podmienia obraz na tych samych mapach (wiszące plakaty zmieniają się od razu). |
| `/maptool delete [gracz:]<nazwa>` | Usuwa obraz (z potwierdzeniem). |
| `/maptool import [folder]` | Ponowny import ze starego ImageOnMap. |
| `/maptool reload` | Przeładowanie `config.yml` i tłumaczeń. |

Nazwy map ze spacjami: `"Bardzo fajna mapa"`, mapa innego gracza: `Gracz:"Bardzo fajna mapa"` –
dokładnie jak w ImageOnMap.

### Mapy splatter (plakaty)

Plakat dostaje się jako jeden błyszczący przedmiot „Plakat” (w ImageOnMap nazywany mapą splatter).
Powieś puste ramki (na ścianie, podłodze albo suficie) i kliknij nim **lewą dolną** ramkę – cały
plakat zawiśnie naraz. Na podłodze i suficie obraz jest obrócony tak, żeby czytał się poprawnie z miejsca, w którym
stoisz. **Kucnij i uderz** dowolną część plakatu, żeby zdjąć go w całości i odzyskać mapę splatter.

Ramki z obrazami są chronione przed mobami, strzałami i wybuchami – zdjąć mapę może tylko gracz.

---

## Uprawnienia

Takie same jak w ImageOnMap. Domyślnie wszyscy gracze mają uprawnienia „użytkownika”, a
uprawnienia moderatorskie (`…other`), `give` i administracyjne – tylko OP.

| Uprawnienie | Domyślnie | Opis |
|---|---|---|
| `imageonmap.*` | wszyscy | Wszystkie uprawnienia użytkownika (bez moderatorskich i admina) |
| `imageonmap.new` (`imageonmap.userender`) | wszyscy | `/tomap` |
| `imageonmap.list`, `imageonmap.explore` | wszyscy | `/maptool list`, `/maps` |
| `imageonmap.get` | wszyscy | Branie swoich map |
| `imageonmap.rename` | wszyscy | Zmiana nazwy swoich map |
| `imageonmap.delete` | wszyscy | Usuwanie swoich map |
| `imageonmap.update` | wszyscy | `/maptool update` swoich map |
| `imageonmap.placesplattermap` | wszyscy | Wieszanie plakatów mapą splatter |
| `imageonmap.removesplattermap` | wszyscy | Zdejmowanie całego plakatu (kucnij + uderz) |
| `imageonmap.placeinvisiblesplattermap` | wszyscy | Ramki pod plakatem stają się niewidzialne |
| `imageonmap.bypasswhitelist` | wszyscy | Pomija `images-hostnames-whitelist` (odbierz, żeby wymusić listę) |
| `imageonmap.listother`, `imageonmap.exploreother` | OP | Podgląd map innych graczy |
| `imageonmap.getother` | OP | Branie map innych graczy |
| `imageonmap.renameother` | OP | Zmiana nazw map innych graczy (nowe) |
| `imageonmap.deleteother` | OP | Usuwanie map innych graczy |
| `imageonmap.updateother` | OP | `/maptool update` map innych graczy |
| `imageonmap.give` | OP | `/givemap` |
| `imageonmap.administrative` | OP | `/maptool import`, `/maptool reload` |
| `imageonmap.bypasssize` | OP | Ignoruje `limit-map-size-x/y` |
| `imageonmap.bypassmaplimit` | OP | Ignoruje `map-player-limit` i `imageonmap.maplimit.<n>` |
| `imageonmap.bypassimagelimit` | OP | Ignoruje `imageonmap.imagelimit.<n>` |
| `imageonmap.maplimit.<n>` | – | Maksymalnie `<n>` map Minecrafta na gracza (najwyższa wartość wygrywa) |
| `imageonmap.imagelimit.<n>` | – | Maksymalnie `<n>` obrazów na gracza |

---

## Konfiguracja (`plugins/ImageOnMapReborn/config.yml`)

| Opcja | Domyślnie | Opis |
|---|---|---|
| `lang` | `pl-PL` | Język (`pl-PL`, `en-US`; pusty = język systemu) |
| `per-player-language` | `false` | Wiadomości w języku klienta gracza |
| `map-global-limit` / `map-player-limit` | `0` | Limit map na serwer / na gracza (0 = bez limitu) |
| `limit-map-size-x` / `limit-map-size-y` | `0` | Maks. rozmiar obrazka w pikselach |
| `save-full-image` | `false` | Zapis pełnego obrazu obok części |
| `images-hostnames-whitelist` | `[]` | Dozwolone strony (np. `i.imgur.com`, `*.discordapp.com`) |
| `download.timeout-seconds` | `20` | Limit czasu pobierania |
| `download.max-size-mb` | `25` | Maks. rozmiar pobieranego pliku |
| `download.block-private-addresses` | `true` | Blokuje linki do sieci lokalnej (ochrona serwera) |
| `max-image-pixels` | `40000000` | Ochrona pamięci RAM przed gigantycznymi obrazami |
| `smooth-scaling` | `true` | Płynne zmniejszanie zdjęć (`false` = jak w starym pluginie) |
| `protect-item-frames` | `true` | Ochrona ramek z obrazami przed mobami i wybuchami |
| `legacy-import.enabled` / `legacy-import.folder` | `true` / `ImageOnMap` | Automatyczny import starych danych |

Wszystkie teksty są w `plugins/ImageOnMapReborn/lang/pl_PL.yml` i `en_US.yml` – można je dowolnie
zmieniać (kolory `&a`, `&#RRGGBB`), a nowy język dodać, kopiując plik jako np. `de_DE.yml`.

---

## Co jest lepsze niż w ImageOnMap

- działa na 1.20.1 → 26.x (stary plugin oficjalnie kończył się na 1.18),
- obsługa **WebP** (oprócz PNG, JPEG, GIF, BMP),
- lepsza jakość zmniejszanych zdjęć,
- pewniejsze wczytywanie obrazów po restarcie (stare wersje potrafiły pokazać pustą mapę, dopóki
  gracz nie zmienił slotu),
- ochrona serwera: blokada linków do sieci lokalnej, limity rozmiaru pobierania i pamięci,
- polskie tłumaczenie i edytowalne pliki językowe,
- zmiana nazwy w GUI przez czat (zamiast tabliczki), uprawnienie `imageonmap.renameother`,
- nie wysyła statystyk (bStats) i nie sprawdza aktualizacji – opcje `collect-data` i
  `check-for-updates` ze starego configu są ignorowane.

---

## Budowanie ze źródeł

Wymagane: JDK 17+ i Maven 3.9+.

```bash
mvn package
```

Wynik: `target/ImageOnMapReborn-1.0.0.jar` (zawiera bibliotekę TwelveMonkeys do WebP, przeniesioną
do pakietu `pl.iomreborn.lib`, więc nie koliduje z innymi pluginami). `mvn test` uruchamia testy
jednostkowe (format danych ImageOnMap, cięcie obrazów, geometria plakatów, parser komend, dekodowanie).

---

## Autorzy i podziękowania

- **Autor projektu:** Optiblur – pomysł, wymagania, decyzje i testy.
- **Testy:** oprócz testów jednostkowych plugin był uruchamiany na serwerach Paper 1.20.1, 1.21.11
  i 26.3 (import starych danych, wyświetlanie map, komendy z konsoli).
- **ImageOnMap** – dziękujemy twórcom oryginału, na którym wzorowane są funkcje, komendy,
  uprawnienia i format danych: Moribus (2013), ProkopyL (2015), Amaury Carrade (2016–2021),
  Vlammar (2019–2021) i wszystkim współtwórcom z zespołu
  [zDevelopers](https://github.com/zDevelopers/ImageOnMap).

ImageOnMapReborn nie zawiera kodu ImageOnMap ani QuartzLib, a wszystkie teksty komunikatów zostały
napisane od nowa. Zgodne z oryginałem są tylko format plików, nazwy komend i uprawnień oraz opcje
konfiguracji – tak, żeby migracja nie wymagała żadnej pracy.

## Licencja

Copyright (C) 2026 Optiblur

Ten program jest wolnym oprogramowaniem: możesz go rozpowszechniać i/lub modyfikować na warunkach
licencji **GNU General Public License w wersji 3** opublikowanej przez Free Software Foundation.
Jest rozpowszechniany bez jakiejkolwiek gwarancji. Pełny tekst licencji jest w pliku
[LICENSE](LICENSE).

Dołączona do pliku `.jar` biblioteka [TwelveMonkeys ImageIO](https://github.com/haraldk/TwelveMonkeys)
(odczyt WebP) jest na licencji BSD-3-Clause – jej tekst jest w
`src/main/resources/META-INF/LICENSE-TwelveMonkeys.txt` i w samym pliku `.jar`.

---

<sub>Wykonane przez Optiblur, z pomocą Claude (Anthropic).</sub>
