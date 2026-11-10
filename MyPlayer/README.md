# Mój Odtwarzacz – prosta aplikacja muzyczna na Androida

Odtwarzacz plików audio z telefonu, z własnymi playlistami, wyborem folderów i **trybami Bluetooth**
(np. „Samochód”): po połączeniu telefonu z wybranym urządzeniem aplikacja sama się uruchamia
i zaczyna odtwarzać wskazaną playlistę.

Napisany w Kotlinie: Jetpack Compose (Material 3), Media3 (ExoPlayer + MediaSession).
Docelowo: Google Pixel 10 z Androidem 17, ale działa od Androida 10 (API 29) wzwyż.

> **Uwaga:** kod został napisany bez dostępu do Android SDK, więc nie był kompilowany ani uruchamiany
> na telefonie. Składnię sprawdził kompilator Kotlina, ale pierwsze zbudowanie w Android Studio może
> wymagać drobnych poprawek (np. numer wersji biblioteki). Treść błędu wystarczy, żeby je naprawić.

## Funkcje

| Zakładka | Co robi |
|---|---|
| **Utwory** | Lista wszystkich utworów z zaznaczonych folderów, wyszukiwarka, odtwarzanie. Przytrzymaj utwór, aby zaznaczyć wiele i dodać do playlisty. |
| **Playlisty** | Tworzenie, zmiana nazwy, usuwanie; w playliście: odtwarzanie, losowo, usuwanie i zmiana kolejności utworów. |
| **Foldery** | Zaznacz foldery, z których aplikacja ma pokazywać muzykę. Z menu folderu: „Odtwórz folder”, „Utwórz playlistę z folderu”. |
| **Auto** | Tryby Bluetooth (patrz niżej). |

Do tego mini-odtwarzacz na dole, pełny ekran odtwarzacza (okładka, suwak, losowo, powtarzanie),
powiadomienie z przyciskami i obsługa przycisków słuchawek/samochodu.

## Jak uruchomić

1. Zainstaluj aktualny **Android Studio** i w *SDK Manager* pobierz **SDK Platform 37 (Android 17)**.
2. *File → Open* i wskaż folder `MyPlayer`. Studio samo pobierze Gradle 9.5.0 i biblioteki.
3. Podłącz telefon (włączone *Debugowanie USB*) i kliknij **Run**.
   Z wiersza poleceń: `./gradlew assembleDebug` (plik APK pojawi się w `app/build/outputs/apk/debug/`).

Po pierwszym uruchomieniu zezwól na dostęp do muzyki i powiadomień.

## Budowanie APK w chmurze (bez Android Studio)

W projekcie jest gotowy workflow GitHub Actions (`.github/workflows/build-apk.yml`).

1. Utwórz repozytorium na github.com (może być prywatne) i wgraj do niego **zawartość** folderu `MyPlayer`
   (*Add file → Upload files*). Jeśli ukryty folder `.github` się nie wgrał: *Add file → Create new file*,
   nazwa `.github/workflows/build-apk.yml`, wklej zawartość pliku z projektu.
2. Po wgraniu zmian wejdź w zakładkę **Actions** – budowanie rusza samo i trwa kilka minut.
3. Gotowy plik APK jest w **Releases** (link do pobrania na telefonie) oraz w wynikach przebiegu w Actions.

Wszystkie wersje są podpisane tym samym kluczem `app/debug.keystore` (publiczne hasło `android`,
tylko do własnych testów), więc kolejną wersję instalujesz bez odinstalowywania poprzedniej.

## Tryb samochodowy – krok po kroku

1. Sparuj telefon z samochodem w ustawieniach Bluetooth systemu.
2. W aplikacji: zakładka **Auto** → przyznaj uprawnienie Bluetooth i powiadomienia.
3. **Nowy tryb** → wpisz nazwę, wybierz urządzenie (samochód), playlistę (albo „Wszystkie utwory”),
   opcjonalnie losową kolejność i pauzę po rozłączeniu.
4. Przycisk ▶ na karcie trybu odtwarza playlistę od razu (test bez Bluetooth).
5. Wyłącz optymalizację baterii dla aplikacji (przycisk „Ustawienia” na górze zakładki Auto).

Jak to działa: odbiornik w manifeście łapie zdarzenie połączenia Bluetooth także przy zamkniętej
aplikacji → uruchamia usługę odtwarzania jako usługę pierwszoplanową → usługa czeka, aż system
podłączy wyjście audio A2DP samochodu (żeby muzyka nie zagrała na chwilę z głośnika telefonu) →
zaczyna odtwarzać playlistę. Po rozłączeniu pauzuje (jeśli tak ustawisz).

### Android 17 i odtwarzanie z tła – dlaczego `targetSdk = 36`

Android 17 ma „Background Audio Hardening”: gdy aplikacja jest w tle, odtwarzanie, żądanie fokusu
audio i zmiana głośności są po cichu blokowane (bez błędu!), jeśli aplikacja nie ma widocznej
aktywności albo usługi pierwszoplanowej. Dla aplikacji z `targetSdk = 37` usługa musi dodatkowo mieć
uprawnienia „while-in-use”, a usługa uruchomiona z tła (jak po połączeniu Bluetooth) zazwyczaj ich
nie dostaje. Dlatego projekt kompiluje się z `compileSdk = 37`, ale ma `targetSdk = 36` – wtedy
wystarczy działająca usługa pierwszoplanowa, którą aplikacja uruchamia przed odtwarzaniem.
(Źródło: dokumentacja Androida 17, „Background audio hardening”.) Jeśli kiedyś podniesiesz
`targetSdk` do 37, przetestuj autostart od nowa.

Plany awaryjne, gdyby system mimo to zablokował start:

- **Powiadomienie „Dotknij, aby odtworzyć”** – jeśli system odrzuci start usługi z tła, aplikacja
  pokaże powiadomienie; dotknięcie to akcja użytkownika, więc system pozwala wtedy grać.
- **Przycisk „play” w samochodzie** – aplikacja pamięta ostatnią playlistę i utwór; po
  naciśnięciu „play” na zestawie samochodowym wznawia je (Media3 `onPlaybackResumption`).

Diagnostyka (telefon przez USB):

```
adb shell cmd audio set-enable-hardening enable     # włącz pełne reguły
adb logcat | grep -E "AudioHardening|BluetoothReceiver|PlaybackService"
```

## Ograniczenia i uwagi

- Aplikacja widzi pliki zaindeksowane przez system (MediaStore). Foldery z plikiem `.nomedia`
  i pliki spoza indeksu się nie pojawią. Dzwonki, powiadomienia i alarmy są pomijane.
- Playlisty zapisują utwory po ścieżce i nazwie pliku. Odznaczenie folderu ukrywa jego utwory
  także w playlistach (zostają w playliście jako „Niedostępny” i wracają po ponownym zaznaczeniu).
- Ustawienia, playlisty i tryby leżą w pliku `files/store.json` w pamięci aplikacji (kopia zapasowa
  Androida je obejmuje).
- Rozpoznanie połączenia opiera się na zdarzeniu `ACL_CONNECTED`. Wymaga uprawnienia
  *BLUETOOTH_CONNECT* – bez niego system nie dostarcza zdarzenia i autostart nie ruszy.
- Nie ma integracji z Android Auto (wymagałaby `MediaLibraryService`); samochód steruje telefonem
  przez zwykły Bluetooth (A2DP/AVRCP).
- Brak korektora, kolejki „odtwórz następny”, tekstów utworów i edycji tagów.

## Struktura projektu

```
app/src/main/java/com/example/myplayer/
  MusicApp.kt                 – Application, kanały powiadomień, kontener zależności
  MainActivity.kt             – punkt wejścia Compose
  data/                       – modele, MediaStore (AudioCatalog), zapis JSON (AppStore), kolejki
  playback/                   – PlaybackService (Media3), PlayerController, konwersja utworów
  bluetooth/                  – BluetoothReceiver (autostart), lista sparowanych urządzeń, plan B
  ui/                         – ekrany Compose i MainViewModel
  util/Permissions.kt         – sprawdzanie uprawnień
gradle/libs.versions.toml     – wersje bibliotek (AGP 9.3.0, Kotlin 2.3.21, Media3 1.10.1, Compose BOM 2026.08.00)
```

## Gdy Gradle zgłosi błąd

- *„requires libraries … to compile against version 37”* – zainstaluj SDK Platform 37 lub zaktualizuj Android Studio.
- *„Could not find …” / nieistniejąca wersja* – popraw numer wersji w `gradle/libs.versions.toml`
  (biblioteki AndroidX: <https://maven.google.com>).
- Starszy Android Studio nie otworzy projektu z AGP 9.3 – zaktualizuj Studio.
