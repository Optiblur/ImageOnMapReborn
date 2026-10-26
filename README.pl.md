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

### Nowości w 1.2.0

- **Domyślnie po angielsku** – nowe instalacje startują po angielsku, polski to jedna opcja
  (`lang: pl-PL`). Serwery, które już używają pluginu, zostają przy swoim języku.
- **Podsumowanie w konsoli przy starcie** – wersja, liczba obrazów, folder upload, język, serwer
  i sprawdzanie aktualizacji w jednym miejscu:
  ```
  +------------------------------------------------------+
  | ImageOnMapReborn 1.2.0  by Optiblur                  |
  | Pictures on maps - the successor of ImageOnMap       |
  +------------------------------------------------------+
  | Images    12 (48 maps, 3 players)                    |
  | Uploads   2 pictures in upload/ (/tomap file:<name>) |
  | Language  pl_PL                                      |
  | Server    Paper 1.21.11                              |
  | Updates   checking SpigotMC for a new version...     |
  +------------------------------------------------------+
  ```
  Kilka sekund później konsola mówi, czy masz najnowszą wersję, albo pokazuje ramkę z nową wersją
  i linkiem do pobrania.
- **Obrazy z dysku serwera** – wrzuć obraz do `plugins/ImageOnMapReborn/upload/` i użyj
  `/tomap file:logo.png`, bez wrzucania go najpierw na jakąś stronę (zobacz
  [Obrazy z dysku serwera](#obrazy-z-dysku-serwera)).
- **`/maptool info`** – wersja, stan aktualizacji i statystyki, w grze albo w konsoli.

### Nowości w 1.1.0

- **Dithering** – zdjęcia i płynne przejścia kolorów wyglądają dużo lepiej (zobacz [Jakość obrazu](#jakość-obrazu-dithering)).
- **Animowane GIF-y** – odtwarzają się na mapach, w ręce i w ramkach (zobacz [Animowane GIF-y](#animowane-gif-y)).
- **Sprawdzanie aktualizacji** – konsola i OP-y dowiadują się o nowej wersji (można wyłączyć).
- `config.yml` ma komentarze po angielsku, a po aktualizacji sam dostaje nowe opcje.

---

## Przejście ze starego ImageOnMap (krok po kroku)

1. **Zrób kopię zapasową** folderu `plugins/ImageOnMap` i świata (mapy są w `world/data/map_*.dat`).
2. Wyłącz serwer.
3. Usuń **tylko plik** `plugins/ImageOnMap-x.x.x.jar`. **Folderu `plugins/ImageOnMap` nie usuwaj** –
   nowy plugin czyta z niego dane.
4. Wrzuć `ImageOnMapReborn-1.2.0.jar` do `plugins/`.
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

### Aktualizacja ImageOnMapReborn (np. 1.1.0 → 1.2.0)

Wyłącz serwer, podmień stary plik `ImageOnMapReborn-x.x.x.jar` na nowy i włącz serwer. To wszystko: mapy,
ustawienia i tłumaczenia zostają. Nowe opcje są dopisywane na końcu `config.yml` (konsola wypisze, które),
a polskie komentarze z wersji 1.0.0 są zamieniane na angielskie – Twoje wartości i Twoje własne komentarze
się nie zmieniają. Język też zostaje: serwer, który działał po polsku, dalej ma `lang: pl-PL`, a po
angielsku startują tylko nowe instalacje.

---

## Komendy

| Komenda | Opis |
|---|---|
| `/tomap <link\|file:nazwa> [resize\|resize-stretched\|resize-covered] [szer. wys.] [dither\|nodither]` | Tworzy mapy z obrazka. Bez trybu obraz ma oryginalny rozmiar i jest cięty na tyle map, ile trzeba (wyśrodkowany). `resize` = jedna mapa, `resize 4 3` = plakat 4×3 mapy (można też `4x3` lub `4*3`). `dither` / `nodither` włącza albo wyłącza dithering dla tego obrazu. Animowane GIF-y stają się animowanymi mapami. `file:<nazwa>` bierze obraz z folderu upload (zobacz [Obrazy z dysku serwera](#obrazy-z-dysku-serwera)). |
| `/maps [gracz]` | Menu z mapami (LPM – weź mapę, PPM – szczegóły: zmiana nazwy, usuwanie, pojedyncze części plakatu). Książka ze statystykami i limitami. |
| `/givemap <gracz> [właściciel:]<nazwa>` | Daje mapę graczowi. Działa z konsoli i bloków poleceń (wtedy właściciel jest wymagany), obsługuje `@p`. |
| `/maptool new …` | To samo co `/tomap`. |
| `/maptool list [gracz]` | Lista obrazów (kliknij nazwę, żeby dostać mapę). |
| `/maptool get [gracz:]<nazwa>` | Daje ponownie mapę. |
| `/maptool getremaining` | Odbiera mapy, które nie zmieściły się w ekwipunku (alias `getrest`). |
| `/maptool explore [gracz]` | To samo co `/maps`. |
| `/maptool give …` | To samo co `/givemap`. |
| `/maptool rename [gracz:]<nazwa> <nowa nazwa>` | Zmiana nazwy (nazwy ze spacjami w cudzysłowie). |
| `/maptool update [gracz:]<nazwa> <nowy link\|file:nazwa> [stretched\|covered] [dither\|nodither]` | Podmienia obraz (albo animowany GIF) na tych samych mapach (wiszące plakaty zmieniają się od razu). |
| `/maptool delete [gracz:]<nazwa>` | Usuwa obraz (z potwierdzeniem). |
| `/maptool import [folder]` | Ponowny import ze starego ImageOnMap. |
| `/maptool reload` | Przeładowanie `config.yml` i tłumaczeń. |
| `/maptool info` | Wersja, stan aktualizacji i statystyki (alias `version`). W konsoli to ta sama ramka co przy starcie. |

Nazwy map ze spacjami: `"Bardzo fajna mapa"`, mapa innego gracza: `Gracz:"Bardzo fajna mapa"` –
dokładnie jak w ImageOnMap.

### Obrazy z dysku serwera

Zamiast linku `/tomap` i `/maptool update` mogą wziąć obraz z folderu
`plugins/ImageOnMapReborn/upload/` (powstaje przy pierwszym uruchomieniu):

```
/tomap file:logo.png resize 4 3
/maptool update "Tabliczka spawn" file:event/dzien1.png
```

- Działają PNG, JPEG, GIF (też animowane), BMP i WebP; podfoldery zapisuje się przez `/`.
- Nazwy plików nie mogą mieć spacji. Podpowiadanie Tabem pokazuje obrazy z folderu.
- Można użyć tylko plików z folderu upload (`file:../cokolwiek` jest odrzucane).
- Gracze potrzebują uprawnienia `imageonmap.localfiles` (OP-y mają je domyślnie). Lista dozwolonych
  stron nie obowiązuje, a limity rozmiaru (`max-image-pixels`, `limit-map-size-x/y`,
  `download.max-size-mb`) tak.

### Mapy splatter (plakaty)

Plakat dostaje się jako jeden błyszczący przedmiot „Plakat” (w ImageOnMap nazywany mapą splatter).
Powieś puste ramki (na ścianie, podłodze albo suficie) i kliknij nim **lewą dolną** ramkę – cały
plakat zawiśnie naraz. Na podłodze i suficie obraz jest obrócony tak, żeby czytał się poprawnie z miejsca, w którym
stoisz. **Kucnij i uderz** dowolną część plakatu, żeby zdjąć go w całości i odzyskać mapę splatter.

Ramki z obrazami są chronione przed mobami, strzałami i wybuchami – zdjąć mapę może tylko gracz.

### Jakość obrazu (dithering)

Mapy w Minecrafcie pokazują tylko ok. 200 kolorów. Bez ditheringu każdy piksel dostaje najbliższy z nich,
przez co powstają płaskie plamy i widoczne pasy na niebie, twarzach i przejściach kolorów. Z ditheringiem
plugin miesza dostępne kolory w drobne kropki, co z kilku bloków wygląda dużo bliżej oryginału.

- `floyd-steinberg` (domyślnie) – najlepsza jakość zdjęć,
- `ordered` – regularny wzór kropek (używany dla animowanych GIF-ów, bo nie migocze),
- `none` – bez ditheringu, jak w starym ImageOnMap; najlepsze dla pixel artu, logo i napisów.

Domyślną metodę serwera ustawia opcja `dithering` w `config.yml`; gracz może wybrać dla jednego obrazu,
dopisując `dither` albo `nodither` do komendy, np. `/tomap https://example.com/logo.png resize nodither`.
Obrazy utworzone przed 1.1.0 się nie zmieniają (żeby narysować je od nowa, użyj `/maptool update`).

### Animowane GIF-y

Animowany GIF podany w `/tomap` albo `/maptool update` staje się animowaną mapą albo plakatem: odtwarza się
w ręce i w ramkach, wszystkie części plakatu są zsynchronizowane, a po restarcie serwera animacja działa dalej.

- Animacja może mieć najwyżej `animated-gifs.max-maps` map (domyślnie 16, np. plakat 4×4) i
  `animated-gifs.max-frames` klatek (100; dłuższe GIF-y pomijają część klatek, ale trwają tyle samo).
  Większe GIF-y są używane jako zwykły obraz (pierwsza klatka), a gracz dostaje informację dlaczego.
- Mapy w ramkach serwer normalnie odświeża tylko dwa razy na sekundę, więc plugin sam wysyła klatki
  graczom stojącym przy ramce, najwyżej `animated-gifs.max-fps` razy na sekundę (10).
- Klatki są trzymane w pamięci tylko wtedy, gdy ktoś patrzy na animację (16 KB na klatkę i mapę), a po
  5 minutach bez widzów są zwalniane.
- Uprawnienie `imageonmap.animated` (domyślnie mają je wszyscy) pozwala tworzyć animacje; bez niego GIF
  staje się zwykłym obrazem. `animated-gifs.enabled: false` wyłącza animacje na całym serwerze.
- Klatki są zapisywane w `images/map<id>.anim`. Plik `images/map<id>.png` zawiera pierwszą klatkę, więc
  po ewentualnym powrocie do oryginalnego ImageOnMap mapy nadal pokazują obraz.

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
| `imageonmap.animated` | wszyscy | Animowane GIF-y stają się animowanymi mapami (nowe) |
| `imageonmap.listother`, `imageonmap.exploreother` | OP | Podgląd map innych graczy |
| `imageonmap.getother` | OP | Branie map innych graczy |
| `imageonmap.renameother` | OP | Zmiana nazw map innych graczy (nowe) |
| `imageonmap.deleteother` | OP | Usuwanie map innych graczy |
| `imageonmap.updateother` | OP | `/maptool update` map innych graczy |
| `imageonmap.give` | OP | `/givemap` |
| `imageonmap.administrative` | OP | `/maptool import`, `/maptool reload`, `/maptool info` |
| `imageonmap.notifyupdate` | OP | Informacja o nowej wersji pluginu przy wejściu na serwer (nowe) |
| `imageonmap.localfiles` | OP | Obrazy z folderu upload: `/tomap file:<nazwa>` (nowe) |
| `imageonmap.bypasssize` | OP | Ignoruje `limit-map-size-x/y` |
| `imageonmap.bypassmaplimit` | OP | Ignoruje `map-player-limit` i `imageonmap.maplimit.<n>` |
| `imageonmap.bypassimagelimit` | OP | Ignoruje `imageonmap.imagelimit.<n>` |
| `imageonmap.maplimit.<n>` | – | Maksymalnie `<n>` map Minecrafta na gracza (najwyższa wartość wygrywa) |
| `imageonmap.imagelimit.<n>` | – | Maksymalnie `<n>` obrazów na gracza |

---

## Konfiguracja (`plugins/ImageOnMapReborn/config.yml`)

| Opcja | Domyślnie | Opis |
|---|---|---|
| `lang` | `en-US` | Język (`en-US`, `pl-PL`; pusty = język systemu). **Ustaw `pl-PL`, żeby mieć polski.** |
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
| `dithering` | `floyd-steinberg` | `floyd-steinberg`, `ordered` albo `none` – zobacz [Jakość obrazu](#jakość-obrazu-dithering) |
| `animated-gifs.enabled` | `true` | Animowane GIF-y odtwarzają się na mapach (`false` = tylko pierwsza klatka) |
| `animated-gifs.max-frames` | `100` | Maks. liczba klatek jednej animacji |
| `animated-gifs.max-maps` | `16` | Największa animacja, w mapach |
| `animated-gifs.max-fps` | `10` | Maks. liczba zmian klatki na sekundę (1–20) |
| `protect-item-frames` | `true` | Ochrona ramek z obrazami przed mobami i wybuchami |
| `legacy-import.enabled` / `legacy-import.folder` | `true` / `ImageOnMap` | Automatyczny import starych danych |
| `check-for-updates` | `true` | Sprawdza nową wersję na SpigotMC po starcie i co 12 godzin |

Wszystkie teksty są w `plugins/ImageOnMapReborn/lang/pl_PL.yml` i `en_US.yml` – można je dowolnie
zmieniać (kolory `&a`, `&#RRGGBB`), a nowy język dodać, kopiując plik jako np. `de_DE.yml`.

---

## Co jest lepsze niż w ImageOnMap

- działa na 1.20.1 → 26.x (stary plugin oficjalnie kończył się na 1.18),
- obsługa **WebP** (oprócz PNG, JPEG, GIF, BMP),
- **animowane GIF-y** na mapach i plakatach,
- **dithering** i lepsza jakość zmniejszanych zdjęć,
- pewniejsze wczytywanie obrazów po restarcie (stare wersje potrafiły pokazać pustą mapę, dopóki
  gracz nie zmienił slotu),
- ochrona serwera: blokada linków do sieci lokalnej, limity rozmiaru pobierania i pamięci,
- polskie tłumaczenie i edytowalne pliki językowe,
- zmiana nazwy w GUI przez czat (zamiast tabliczki), uprawnienie `imageonmap.renameother`,
- nie wysyła statystyk (bStats) – opcja `collect-data` ze starego configu jest ignorowana. Sprawdzanie
  aktualizacji tylko pyta SpigotMC (albo GitHub) o numer najnowszej wersji, nie wysyła żadnych danych
  o serwerze, niczego samo nie pobiera i można je wyłączyć opcją `check-for-updates: false`.

---

## Budowanie ze źródeł

Wymagane: JDK 17+ i Maven 3.9+.

```bash
mvn package
```

Wynik: `target/ImageOnMapReborn-1.2.0.jar` (zawiera bibliotekę TwelveMonkeys do WebP, przeniesioną
do pakietu `pl.iomreborn.lib`, więc nie koliduje z innymi pluginami). `mvn test` uruchamia testy
jednostkowe (format danych ImageOnMap, cięcie obrazów, geometria plakatów, parser komend, dekodowanie,
dithering, klatki GIF-ów, pliki animacji, aktualizacja configu, sprawdzanie wersji, ramka w konsoli,
folder upload).

---

## Autorzy i podziękowania

- **Autor projektu:** Optiblur – pomysł, wymagania, decyzje i testy.
- **Testy:** oprócz testów jednostkowych plugin był uruchamiany na serwerach Paper 1.20.1, 1.21.11
  i 26.3 (import starych danych, wyświetlanie map, komendy z konsoli, dithering, klatki GIF-ów,
  aktualizacja configu). Dla 1.1.0 na serwer Paper 1.20.1 wchodził też testowy klient, który liczył
  otrzymane aktualizacje map: animacje w ręce i w ramkach, zwykłe obrazy i powiadomienie o aktualizacji.
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
