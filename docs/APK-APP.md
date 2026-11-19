# Smartschool Katten als gewone APK

Status: testfase. Er worden nog geen releases gemaakt.

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

De workflow **APK testen** bouwt op de branch `apk-test` een ongetekend testbestand.
Hij publiceert geen release en gebruikt geen privésleutels. De bron is de complete
Smartschool 12.3.0-set; APKEditor 1.4.9 voegt de configuratie-APK's samen. De JAR
wordt tegen zijn gepinde SHA-256 gecontroleerd. Een losse basis-APK met ontbrekende
splits wordt geweigerd.

Lokaal controleren:

```sh
python3 tools/test-runtime.py --java-home /pad/naar/jdk --sdk /pad/naar/sdk
python3 tools/test-compose-bridge.py --java-home /pad/naar/jdk
node tools/test-downloads.mjs
python3 tools/build-apk.py --java-home /pad/naar/jdk --sdk /pad/naar/sdk --unsigned
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

## Downloadpagina

De bestanden in `site/` vormen een statische GitHub Pages-pagina. De Chrome- en
Firefox-knoppen gaan naar de bestaande extensiewinkels. Android blijft als
**Binnenkort beschikbaar** staan totdat een definitieve APK-release bestaat.
Daarna haalt de pagina automatisch de juiste downloadlink op. Er is geen
buildframework, externe lettertypebron of tracking nodig.
