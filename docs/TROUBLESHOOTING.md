
## Patchrevision 3 — opstartcrash en eenvoudiger bediening

Het telefoonlog toont een fatale `IMGLYAutoInit`-licentiefout doordat de tweede app een ander pakket-ID heeft. Revision 3 verwijdert de optionele foto-editorprovider en editoractiviteiten uit de manifesten. Acties in Smartschool die deze editor openen worden uitgeschakeld met een melding. De SDK-licentie en autorisatiecontroles blijven behouden. Gebruik de officiële Smartschool-app voor de ingebouwde foto-editor.

De patcher toont één grote volgende-stapknop: downloaden → doorgaan → patch maken → installeren. Opslaan, opnieuw downloaden en een nieuwe patch maken staan kleiner onder ‘Extra opties’. ‘Terug’ is een secundaire knop.

Maak na het bijwerken van de patcher een nieuw installatiepakket (r3) en installeer dit. Eerdere pakketten worden niet als het nieuwe resultaat gebruikt. Lokale controles vervangen geen opstarttest op de Motorola; die bevestiging is nog nodig.

# Smartschool Katten bijwerken en foutgegevens verzamelen

## Opstartfix installeren

1. Bouw de bijgewerkte patcher met **▶ Run** in Android Studio.
2. Open de patcher. Als het eerdere downloadpakket klaarstaat: kies **Doorgaan**.
   Download Smartschool opnieuw als er geen pakket klaarstaat.
3. Kies **Kattenpatch maken**. De nieuwe uitvoer is revision 2 (`-r2.apks`).
4. Kies **Smartschool Katten installeren** en bevestig de Android-installatie.
5. Start Smartschool Katten opnieuw en meld je aan.

De patcher opnieuw bouwen wijzigt niet automatisch een al geïnstalleerde kattenapp.
Maak daarom echt een nieuwe patch. Bewaar de gegevens van de patcher: daarin staat
de private ondertekeningssleutel waarmee bestaande katteninstallaties bijgewerkt
worden. De officiële Smartschool-app blijft staan.

## Als de app nog sluit vóór aanmelden

De volledige stacktrace is nodig om de resterende oorzaak vast te stellen. Een
algemene melding zoals "app is gestopt" bevat die oorzaak niet.

Met USB-foutopsporing en een verbonden toestel kun je in Android Studio **Logcat**
openen, Smartschool Katten starten en de `FATAL EXCEPTION` / `VerifyError` /
`Caused by`-regels opslaan.

Een lokale terminaloptie:

```sh
python3 tools/capture-crash.py
```

Dit start de kattenapp en slaat foutgegevens op in `~/Downloads`. Er wordt niets
geüpload en bestaande telefoonlogs worden niet gewist. Gebruik bij meerdere
toestellen `--serial SERIENUMMER`.

## Als de app werkt maar een kattenfunctie faalt

Het kattenmenu bevat geen foutgegevensknop. Gebruik voor probleemonderzoek
de hierboven beschreven Logcat-opname. Java-fouten worden ook lokaal opgeslagen;
native crashes en fouten vóór de katteninitialisatie kunnen alleen in Logcat staan.

Bij een mislukte foto-download blijft de galerij bruikbaar en kun je opnieuw
openen om te proberen. Beschadigde cachebestanden worden verwijderd. Heropen het
Smartschool-scherm na het kiezen van een nieuwe favoriet of katten aan/uit.
## Gesigneerde patcher sluit meteen af

Op 9 oktober 2026 gaf de releasepatcher bij openen een `IllegalAccessError`:
`kotlin.collections.e extended by class ld is inaccessible`.
De mapping identificeert deze classes als `CollectionsKt__IterablesKt` en
`CollectionsKt__MutableCollectionsJVMKt`. De gedeeltelijke release-optimalisatie
verplaatste Kotlin-classes naar verschillende pakketten en brak hun toegang.
Release-optimalisatie is daarom uitgeschakeld in `app/build.gradle.kts`.
Bouw opnieuw met **Build → Generate Signed App Bundle or APK → APK**, met dezelfde
keystore als de vorige release, en installeer de nieuwe APK als update.

