# Smartschool Katten als gewone APK

De Android-app wordt rechtstreeks als getekende APK gepubliceerd.

Revision 46 verwijdert de screenshotblokkering op de eigen leerlingenkaart.
De wijziging is beperkt tot de vlagupdate bij het openen van die pagina;
helderheid, opruimen van de pagina en andere vensters behouden hun gedrag.
De APK-build en controle van de precieze wijziging slagen. Een screenshot op
het toestel moet na opnieuw starten via Android Studio worden bevestigd.

De Android-download wordt een complete `Smartschool-Katten.apk`. Downloaden en
patchen op de telefoon vervallen. De bestaande engine blijft als bouwgereedschap
op de computer beschikbaar; hij maakt geen deel uit van de gebruiksstappen.

De app controleert op de achtergrond maximaal eenmaal per dag op een nieuwere
definitieve GitHub-release. In het kattenmenu staat ook **Controleren op updates**.
Tags hebben het formaat `katten-r45` en het bestand heet `Smartschool-Katten.apk`.
Na downloaden controleert de app de SHA-256 van GitHub, pakketnaam, versiecode en
dezelfde ondertekenaar als de geïnstalleerde app. Android vraagt bevestiging bij
installatie. Bij ontbrekende releases of netwerkproblemen blijft de app bruikbaar.

## Eerst testen

### Met de Play-knop in Android Studio

Open het bestaande project, kies **Sync Project with Gradle Files** en selecteer
de runconfiguratie **Smartschool Katten** en je telefoon. **Run** bouwt,
debug-ondertekent en installeert rechtstreeks **Smartschool Katten**.
De actieve `:app`-module is nu `standalone/`; de oude patcherinterface wordt niet
gebouwd of gestart. De Java-runtime staat in het project zodat je er ook met de
debugger doorheen kunt stappen.

De complete originele basis wordt op de computer eenmalig opgehaald en daarna
met een SHA-256-controle in de lokale buildcache hergebruikt. APK's handmatig
downloaden, uitpakken of in een patcher kiezen is niet nodig. De eerste build
vereist internet; latere builds gebruiken de cache. De bestaande kattenapp op
je telefoon blijft afzonderlijk beschikbaar.

### Automatische controles

De workflow **APK testen** bouwt op de branch `apk-test` een getekende debugtest.
Die heet **Smartschool Katten**, gebruikt pakket `be.smartschoolkatten.mobile.test`
en staat naast de bestaande app. Voor deze variant is geen releasesleutel nodig.
De testsleutel staat in een aparte buildcache en wordt nooit voor de gewone app gebruikt.
De workflow publiceert geen release. De bron is de complete
Smartschool 12.3.0-set; APKEditor 1.4.9 voegt de configuratie-APK's samen. De JAR
wordt tegen zijn gepinde SHA-256 gecontroleerd. Een losse basis-APK met ontbrekende
splits wordt geweigerd.

Lokaal controleren:

```sh
python3 tools/test-runtime.py --java-home /pad/naar/jdk --sdk /pad/naar/sdk
python3 tools/test-compose-bridge.py --java-home /pad/naar/jdk
node tools/test-downloads.mjs
python3 tools/build-apk.py --java-home /pad/naar/jdk --sdk /pad/naar/sdk --debug-test
```

Een aanwezige volledige bron kun je meegeven met `--source /pad/naar/bron.apks`.
Zonder die optie downloadt het script de volledige bron. De APK moet nog op een
toestel getest worden: starten, aanmelden, eigen/andere katten aan en uit,
favoriet veranderen, kaartfoto onafhankelijk schakelen en de streakpil aan beide
uiteinden aantikken. Daarna een tweede, hoger genummerde testversie met dezelfde
sleutel installeren en controleren dat login, favoriete kat en streak behouden
blijven. De echte updateflow via GitHub vereist later een getekende testrelease.

## Bestaande sleutel behouden

De architectuurwissel verandert de sleutel niet. Alleen de sleutel waarmee de
**kattenapp** is getekend kan die installatie blijven bijwerken. De oude patcher
bewaart een eigen `files/patch-signing/signing.pk8` en `signing.der`. Dit is niet
automatisch de keystore waarmee de patcher-app is getekend. Controleer daarom de
certificaatvingerafdruk vóór de eerste installatie.

Met de bestaande identiteit:

```sh
python3 tools/build-apk.py --java-home /pad/naar/jdk --sdk /pad/naar/sdk \
  --signing-dir /privé/map/met/bestaande-app-sleutel
```

Dit vereist beide bestaande bestanden. Het script genereert nooit automatisch
een nieuwe releasesleutel. Bewaar de identiteit buiten de repository en upload
haar niet naar GitHub. Een download voor eindgebruikers krijgt altijd dezelfde
APK-bestandsnaam; `versionCode = 120300000 + revision` neemt bij elke versie toe.

Een bestaande JKS/PKCS12-keystore kan ook worden gebruikt na een ongetekende build:

```sh
python3 tools/sign-apk.py artifacts/apk-release/Smartschool-Katten-unsigned.apk \
  --output artifacts/apk-release/Smartschool-Katten.apk --java-home /pad/naar/jdk \
  --keystore /privé/map/bestaande-sleutel.jks --alias smartschoolkatten \
  --match-installed /pad/naar/geëxporteerde-huidige-kattenapp.apk
```

De signer vraagt het wachtwoord in de terminal en stopt bij een afwijkend
certificaat. De keystore of het wachtwoord worden niet naar GitHub verstuurd.

## Downloadpagina

De bestanden in `site/` vormen een statische GitHub Pages-pagina. De Chrome- en
Firefox-knoppen gaan naar de bestaande extensiewinkels. Android blijft als
**Binnenkort beschikbaar** staan totdat een definitieve APK-release bestaat.
Daarna haalt de pagina automatisch de juiste downloadlink op. Er is geen
buildframework, externe lettertypebron of tracking nodig.

De Pages-workflow draait pas na samenvoegen naar `main` of handmatig starten.
Kies eenmalig **Settings → Pages → Source: GitHub Actions** in de repository.
De gekoppelde GitHub-tools kunnen broncode en workflows beheren, maar hebben
geen instellingstool om Pages namens de eigenaar te activeren. De website wordt vanuit de branch `gh-pages` gepubliceerd.
APK-publicatie gebeurt met de aparte publicatieworkflow en een lokaal getekend bestand.

## Ondertekening en publicatie

De vaste app-identiteit staat lokaal in `~/.local/share/smartschool-katten/signing/`.
Die identiteit is overgenomen van de al werkende Android Studio-installatie.
Het bestaande pakket-ID blijft behouden zodat updates de appgegevens bewaren;
de zichtbare appnaam en openbare APK bevatten geen testnaam. Een gewone release
is niet debuggable. De private sleutel wordt niet naar GitHub verstuurd.

De publicatieworkflow controleert de vooraf getekende APK, certificaatvingerafdruk,
pakketnaam, versie en SHA-256 vóór de release beschikbaar wordt.
