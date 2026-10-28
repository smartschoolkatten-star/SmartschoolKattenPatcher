Huidige patchrevision: **r38**. De foto rechts bovenaan onder dag/datum in de planner heeft een eigen kattenlaadroute. Plannerfoto’s en leerkrachtfoto’s bij resultaten ondersteunen nu ook `pictureUrl`. Ontbrekende plannerfoto’s krijgen bij ingeschakelde kattenprofielfoto’s een kat. ‘Kat op leerlingenkaart’ blijft een aparte instelling, standaard uit. ‘Kom morgen terug’ heeft een transparante achtergrond. Werk de patcher bij en maak daarna een nieuw r38-installatiepakket.

Revision **r14**: de streakbalk neemt een achtergrondpixel van de zichtbare homepagina over. Profielfoto’s worden expliciet rond geclipt. CoverDrawable gebruikt voor de schaal max(vakbreedte/fotobreedte, vakhoogte/fotohoogte): in een cirkel is de kortste zijde minimaal de diameter. De lange zijde wordt vanuit het midden bijgesneden. Er worden geen vermeende witte randen uit de bronfoto verwijderd. GIFs blijven als animatie beschikbaar. Uiterlijk op het toestel controleren.

Huidige patchrevision: **r12**. Zwarte medium tekst op de streakknop, afgerond kattenmenu met telefoonthemakleuren en extra afbeeldingladers voor bijsnijden van kattenprofielfoto’s. Werk de patcher bij via Run en maak een nieuwe patch om deze wijziging te installeren.

Revision **r10**: de DEX-runtime wordt nu als gegenereerde Java-bron (`RuntimePayload`) met de patchengine gecompileerd. Een verouderd runtime.dex-bestand uit assets/cache wordt niet meer gelezen. De build genereert beide uitvoerbestanden vóór het compileren/verpakken; linkagevalidatie blijft actief. Installeer de patcher volledig met Run, maak een nieuwe patch en installeer r10. De bestaande download, favorieten, streak en ondertekeningssleutel hoeven niet gewist te worden.

Huidige patchrevision: **r9**. De runtime wordt automatisch vóór het verpakken van de patcher opnieuw gebouwd. Elke statische aanroep naar de kattenruntime wordt gecontroleerd op een bijpassende methode in de uiteindelijke APK. Dit voorkomt de gemelde `NoSuchMethodError: configureCard` door een verouderde runtime. Installeer de patcher met de groene **Run**-knop in Android Studio (volledige installatie), maak daarna een nieuwe patch en installeer r9. Gebruik hiervoor geen Apply Code Changes. Bestaande favoriet en streak blijven bij een normale update behouden.

Huidige patchrevision: **r8**. De katteninstellingen staan als vierkante kattenknop naast ‘Startscherm instellen’ op home. Kattenprofielfoto’s worden bij het laden vanuit het midden bijgesneden zodat cirkels gevuld blijven. Na bijwerken van de patcher opnieuw patchen en installeren; bestaande favoriet en streak blijven behouden.


## Patchrevision 3 — opstartcrash en eenvoudiger bediening

Het telefoonlog toont een fatale `IMGLYAutoInit`-licentiefout doordat de tweede app een ander pakket-ID heeft. Revision 3 verwijdert de optionele foto-editorprovider en editoractiviteiten uit de manifesten. Acties in Smartschool die deze editor openen worden uitgeschakeld met een melding. De SDK-licentie en autorisatiecontroles blijven behouden. Gebruik de officiële Smartschool-app voor de ingebouwde foto-editor.

De patcher toont één grote volgende-stapknop: downloaden → doorgaan → patch maken → installeren. Opslaan, opnieuw downloaden en een nieuwe patch maken staan kleiner onder ‘Extra opties’. ‘Terug’ is een secundaire knop.

Maak na het bijwerken van de patcher een nieuw installatiepakket (r3) en installeer dit. Eerdere pakketten worden niet als het nieuwe resultaat gebruikt. Lokale controles vervangen geen opstarttest op de Motorola; die bevestiging is nog nodig.

# Smartschool Katten Patcher

Android-patcher in Kotlin en Jetpack Compose. Downloadt Smartschool **12.3.0** en
maakt lokaal een tweede app: **Smartschool Katten**, pakket
`be.smartschoolkatten.mobile`. De officiële Smartschool-app blijft staan.

## Gebruik

1. Synchroniseer Gradle in Android Studio en bouw de patcher opnieuw.
2. Kies **Smartschool 12.3.0 downloaden**. De download gebruikt Aptoide-release
   `76524701` met `aab=1`. Processoronderdelen worden lokaal geselecteerd. De basis-APK en bijbehorende
   processor-, scherm- en taalonderdelen worden samen opgehaald; de aangeboden en
   gedownloade versies worden gecontroleerd.
3. Kies **Doorgaan → Kattenpatch maken**. Dit kan even duren en gebruikt behoorlijk
   wat werkgeheugen. Sluit andere zware apps als het toestel onvoldoende geheugen meldt.
4. Kies **Smartschool Katten installeren**. Android vraagt eventueel om de patcher
   toestemming te geven om apps te installeren, gevolgd door installatiebevestiging.
5. Open Smartschool Katten en meld je aan. Op de navigatie/home-Activity staat een
   **🐈-knop** voor katten aan/uit, je favoriete kat en de dagelijkse katten-check-in.

**Pakket opslaan** bewaart de originele download. **Installatiepakket opslaan**
bewaart het gepatchte `.apks`-bestand: een ZIP met alle APK-onderdelen. Installeer
via de knop in de patcher; Androids gewone bestandsinstaller kan dit pakket niet
als losse APK installeren. Er is geen handmatige APK-keuze of controle op afkomst.

De patcher zelf werkt vanaf Android 8. Smartschool 12.3.0 en de gepatchte app
vereisen **Android 12L (API 32) of nieuwer**.

De split-download volgt het formaat uit de [Aptoide API-documentatie](https://partners-center-be.aptoide.com/partners_api_doc/en), sectie 2.4.2.

## Opstartfix — patchrevision 2

De eerste patch kon direct bij starten crashen: R8 hergebruikt het `this`-register
in `Application.onCreate` op een uitzonderlijk retourpad als String. De oude
patch riep daar `init(Application)` aan met die String. De initialisatie staat nu
één keer aan het begin van de methode, waar het register zeker Application bevat.

De patch laat constructors, equality, hashCode, toString, kopieermethoden en
parcel-serialisatie intact. Alleen foto-uitlezing en weergave worden aangepast.
`PatchValidation` weigert onveilige opstartaanroepen bij patchen én installeren.
De nieuwe uitvoer heet `Smartschool-Katten-12.3.0-r2.apks`, zodat een oude patch
niet opnieuw als de bijgewerkte uitvoer wordt aangeboden.

Na bijwerken van de patcher: **Doorgaan → Kattenpatch maken → Installeren**.
Een eerder succesvol gedownload pakket wordt bewaard en opnieuw geselecteerd als
het nog bestaat. Download opnieuw als er geen pakket klaarstaat. De private
ondertekeningssleutel blijft dezelfde, zodat installeren de kattenapp kan bijwerken.
De bestaande installatie wordt niet vanzelf aangepast door de patcher te bouwen.

Fouten in de katteninitialisatie/fotokeuze vallen terug op de originele foto's.
Java-crashgegevens worden lokaal opgeslagen voor probleemonderzoek. Het gebruikersmenu
bevat geen debugopties. Er worden geen logs verstuurd.
Voor crashes vóór aanmelden, zie [probleemoplossing](docs/TROUBLESHOOTING.md).

## Kattenfuncties

- Avatarvelden en geïdentificeerde avatar-UI-modellen worden naar de 18
  afbeeldingslinks van Smartschool Katten 1.93 omgeleid.
- Dezelfde oorspronkelijke foto houdt dezelfde willekeurige kat binnen het
  appproces. De gekozen favoriete kat voor het eigen profiel blijft bewaard.
- Favorieten zijn te kiezen in het kattenmenu. GIF’s bewegen in de galerij;
  animatie in Smartschools eigen avatarweergave hangt van diens loader af.
- De dagelijkse katten-check-in telt maximaal eenmaal per lokale kalenderdag. Opeenvolgende
  dagen verhogen de streak; een gemiste dag breekt de reeks. Het record blijft.
- Katten aan/uit en favorieten veranderen toekomstige avatarweergaven. Heropen het
  betreffende scherm om reeds geladen afbeeldingen te verversen.

## Bouw en verificatie

Normale Android-build:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

De geïnjecteerde runtime staat los van de patcher-broncode in `patch-runtime/`.
De vooraf gebouwde payload staat in `app/src/main/assets/patch/`. Na wijzigingen
in de runtime, icoonbron of kattenlinks opnieuw genereren:

```sh
python3 tools/build-payload.py --java-home /pad/naar/android-studio/jbr
```

Lokale desktopproef voor een **zelfstandige APK**, zonder Gradle (de huidige
Aptoide-basis-APK wordt terecht afgewezen; split-pakketten gebruiken `ApkSet.patch`):

```sh
mkdir -p build/patch-check
javac --release 11 -cp 'app/libs/*' -d build/patch-check \
  app/src/main/java/be/smartschoolkatten/patcher/engine/*.java tools/PatchCli.java
java -Xmx256m -cp 'build/patch-check:app/libs/*' PatchCli \
  /pad/naar/Smartschool-12.3.0.apk build/Smartschool-Katten-12.3.0.apk \
  app/src/main/assets/patch /pad/naar/private-signing-map
```

Split-pakketcontrole (synthetische fixtures, geen installeerbare Smartschool-app):

```sh
python3 tools/test-splits.py --java-home /pad/naar/android-studio/jbr \
  --source-apk /pad/naar/Smartschool-12.3.0.apk
```

`tools/StreakTest.java` controleert de dagelijkse check-in en kalendergrenzen.
`tools/PatchAssertions.java` controleert behoud van originele classes, DEX-limieten,
avatar-aanroepen en benodigde assets. Zie [validatie](docs/VALIDATION.md).

## Status en praktische grenzen

De eerdere losse 12.3.0-test-APK is **niet installeerbaar**: de originele download
blijkt een basis-APK met verplichte ABI- en density-splits. De patcher verwerkt nu
een volledig `.apks`-pakket en gebruikt één `PackageInstaller`-sessie voor alle
onderdelen. Het ontbreken van splits wordt gecontroleerd, niet omzeild.

De gebruiker heeft downloaden en installeren bevestigd. De opstartfix in revision 2
moet nog op het toestel bevestigd worden. De engine is getest met de echte
basis-APK en synthetische splits: alle 23.899 originele classes en 807 beschermde
modelbewerkingen blijven behouden; de oude onveilige opstartpatch wordt afgewezen.
De volledige Gradle-build
kon in de agentomgeving niet starten wegens geblokkeerde netwerksockets; Java,
Kotlin/Compose en de DEX-bewerking zijn daar met lokale tools gecontroleerd.
Starten, aanmelden, alle avatars, bestandsdeling en pushmeldingen moeten nog op
het toestel worden getest. Een andere pakketnaam en handtekening kunnen invloed
hebben op externe integraties.

De patcher bewaart een eigen ondertekeningssleutel in zijn private opslag, zodat
latere lokaal gemaakte patches elkaar kunnen bijwerken. De desktop-test-APK
gebruikt een afzonderlijke ontwikkelsleutel. Installeer je die eerst, dan moet je
de testvariant verwijderen voordat je de door de patcher ondertekende variant
installeert. Verwijder alleen de kattenvariant; de officiële app blijft behouden.

De gewijzigde app gebruikt externe afbeeldingslinks. Niet-bereikbare links kunnen
ontbrekende kattenfoto’s geven. Er worden geen aanmeldgegevens of originele APK’s
naar een patchserver gestuurd; APK-bewerking gebeurt lokaal.

Technische beschrijving: [patchplan](docs/PATCH-PLAN.md).
