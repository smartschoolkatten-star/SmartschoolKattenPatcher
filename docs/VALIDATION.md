## r31: vaste oranje pil en ruimere tekstmarges

Kom morgen terug gebruikt nu vast diep oranje `#FF6D00`, gekozen met de buitenste vlam van Android/Noto 🔥 als visuele referentie; PixelCopy past deze pilkleur niet meer aan. Donkere modus blijft grijs. De tekst krijgt 32dp horizontale padding, ingesteld nadat de achtergrond is geplaatst, zodat achtergrondpadding de tekstruimte niet overschrijft. De rechterrand blijft uitgelijnd met home_avatar. DEX 1, 3 en 4 tellen elk voor 25% van de totale patch, inclusief geschatte tussenliggende UI-voortgang.

## r27: afgeronde verwijderbevestiging en subtiele streakstatus

Het Ja/Nee-venster gebruikt dezelfde 28dp-hoekafronding als het kattenmenu, met geclipte vensterranden. Kom morgen terug krijgt een kleiner afgerond vlak via 8dp-insets: zacht oranje in lichte modus, grijs in donkere modus en witte tekst. Aai de kat blijft wit met zwarte tekst. De patcher bevat ook de golvende procentbalk; de voortgangstest en volledige patchchecks zijn geslaagd vóór deze visuele runtimewijziging. De nieuwe runtime en patcher worden samen gebouwd; uiterlijk nog op toestel te bevestigen.

## Patcher: percentage en golvende voortgangsbalk

De patcher toont tijdens patchen een geanimeerde golvende balk in de Material-kleuren, met percentage en toegankelijkheidsvoortgang. De engine meldt gewogen voortgang: DEX 1, 3 en 4 tellen elk voor 25% van het volledige patchwerk. Overige bestanden delen het resterende bestandsgewicht op basis van bronbytes, met aparte fasen voor payload, ondertekenen, validatie, splits en verpakken. De CLI controleert het gewicht op de echte 12.3.0-basis-APK. Tijdens DEX 1, 3 en 4 schat een UI-timer de tussenliggende voortgang binnen maximaal 24,5 procentpunt van hun 25%-stap. De schatting nadert die bovengrens geleidelijk en wacht op de echte voltooiing; timers stoppen bij volgende stap, fout, voltooiing en ViewModel-opruiming. Percentages schatten voltooid werk, geen resterende tijd. UI-voortgang blijft onder 100% tot het definitieve bestand is verplaatst. De CLI-regressietest controleert begin 0%, monotone waarden binnen bereik en einde 100%. Bestaande statuscallbacks blijven compatibel. Runtimepatchrevision blijft r26 omdat dit alleen de patcherinterface en voortgangsmeldingen verandert.

## r24: minder werk tijdens patchen

De tweede handtekeningcontrole van de basis-APK is verwijderd: `sign()` verifieert elke ondertekende APK al. De volledige DEX/opstartcontrole blijft bestaan. Tijdelijke APKs gebruiken snelle ZIP-compressie; het uiteindelijke APKS-pakket bewaart de al gecomprimeerde APK-bestanden zonder opnieuw comprimeren, met CRC en exact behouden bytes. Dit ruilt wat bestandsgrootte voor minder compressiewerk. De winst op het toestel is nog niet gemeten.

## r23: galerijlading, verversen, streakschakelaar en eigen leerlingenkaart

Afbeeldingen mogen vóór het koppelen van het instellingenvenster aan het scherm worden ingesteld; de requestcontrole blijft gerecyclede views beschermen. Vier laadworkers gebruiken een wachtrij zonder stil weggooien. Tijdelijke laadfouten krijgen maximaal twee herpogingen. Definitieve fouten gaan naar lokale foutgegevens. Bij werkelijk sluiten (knop, terug of buiten tikken) herbouwt de huidige Activity via `recreate()` en de Android saved-state lifecycle, zodat native en Compose-afbeeldingen opnieuw worden gebonden. Interne sluitingen voor importeren en vernieuwen na verwijderen slaan die herbouw over. De nieuwe bewaarde schakelaar `Streakbalk` voorkomt bij uitgeschakelde stand het toevoegen van de balk en bijbehorende bovenmarge; streakgegevens blijven behouden. De eigen leerlingenkaart leest `UserCardState.userPictureUrl` (`gac.a`) in `dac.a`; precies die UI-read gebruikt nu `ownAvatar`. Andere leerlingenkaarten behouden hun eigen selectie. De regressietest controleert de favoriete-kat-hook direct na deze veldread; modelidentiteit blijft ongewijzigd. Schermstatus en visuele werking moeten nog op het toestel worden bevestigd.

## r22: uploads verwijderen en vaste galerijkolommen

Uploaden en Delete uploads staan naast elkaar. Verwijdermodus laat alleen uploads selecteren, markeert ze met een rode cirkel en blokkeert sluiten/terug/buiten tikken. Annuleren herstelt de gewone selectie; Ja/Nee bevestigt het verwijderen van geselecteerde uploads. Favorieten en avatarselecties worden opnieuw gecontroleerd en de teller wordt vernieuwd. Onvolledige galerijrijen houden drie gelijke kolommen met lege eindcellen. Opslagtests controleren dat verwijderen alleen eigen bestanden raakt, overige uploads behoudt en een uploadslot vrijmaakt.

## r21: ontbrekende logobestanden bij patchen op de telefoon

De patcher kopieert nu alle bestanden uit de gebundelde map `assets/patch` naar zijn werkmap. De vaste lijst miste `in-app-logo.png` en `splash-logo.png`, waardoor r20 op de telefoon faalde terwijl de desktoptest de volledige map gebruikte. De APK-build en aanwezigheid van beide PNG-bestanden zijn gecontroleerd.

Revision **r20**: eigen katten via de Android-bestandskiezer (één of meerdere foto’s), maximaal 100 naast de standaardkatten. De teller `(aantal/100)` staat klein linksonder bij de uploadknop; upload is uitgeschakeld op 100. Foto’s worden gevalideerd en privé gekopieerd, zodat verplaatsen/verwijderen van de bronfoto geen bestaande kat breekt. De opslag controleert zelf de limiet. Mislukte imports laten geen bestand of gebruikte plek achter.

Lang drukken schakelt standaard- en eigen katten uit/in. Uitgeschakelde katten verschijnen grijs in de galerij en worden uitgesloten van nieuwe en eerder gecachte willekeurige toewijzingen. Een uitgeschakelde favoriet wordt vrijgegeven. Als geen katten actief zijn blijft de originele avatarroute beschikbaar. De kleine hint luidt ‘Druk lang om een kat uit te schakelen.’ CustomCatsTest controleert persistente kopieën, mislukte/te grote imports, de 100-grens, de teller en uitsluiting/herinschakeling. Uploadkiezer, terugkeer naar het menu en het uiterlijk moeten nog op het toestel worden gecontroleerd.

Revision **r19**: het aangeleverde 1024 × 1024 kattenicoon vervangt `ic_smartschool_app_logo` (account toevoegen/inloggen en Compose-kop van ‘Over Smartschool’) en beide splashresources. De PNG’s behouden de bronafbeelding, transparante afgeronde hoeken met de originele verhouding en de bestaande splashmarge. Resource-ID’s en layouts blijven behouden; de splashresources gebruiken nu een statisch logo. LogoBrandingTest controleert de echte gepatchte resources, resourcepaden, transparantie en behoud van de oorspronkelijke layoutbestanden.

Revision **r18**: de dagelijkse muisknop is vervangen door ‘🐾 Aai de kat’, met bevestiging ‘Kat geaaid! +1 dag 🐾’. De patcher beschrijft dit als een katten-check-in. Bestaande streak, record, datum en eenmaal-per-dag-regel blijven behouden. r18 bevat ook de Compose/Coil-schaalcorrectie uit r17.

Revision **r17**: berichten (`t25.b → l66.a → b66.e`) en de eigen avatar in de zijbalk (`m66.h → m66.g → b66.e`) gebruiken Compose/Coil via `yx5.a`, niet de ImageView/Glide-hooks uit r16. `b66.e` kiest standaard `qz1.b` (Fit). De drie aanroepen vanuit `b66` naar `yx5.a` worden nu doorgegeven via ComposeImageBridge. Alleen een katten-URL krijgt `qz1.a` (Crop), met het ContentScale-defaultbit gewist en de relevante compositiebits als gewijzigd ingesteld. De originele Coil-aanvraag, animatie, modifier, clip, callbacks en overige argumenten blijven behouden.

Op het aangesloten toestel is r16 uit de APK-marker bevestigd. `ComposeScaleDeviceTest` gebruikt de echte geïnstalleerde hostclasses en reproduceert Fit: 400 × 200 wordt 80 × 40 binnen 80 × 80. Crop vult hetzelfde vak. 27 combinaties van negen diameters en drie verhoudingen slagen met de echte Crop-implementatie. Geen gebruikers- of berichtgegevens gelezen. `test-compose-bridge.py` controleert reflectieve doorgifte, behoud van niet-kattenargumenten en wijziging van alleen de schaal/default/compositiebits. Het uiteindelijke uiterlijk na installatie van r17 moet nog visueel worden bevestigd.

Revision **r16**: screenshots tonen rechte afkappingen binnen sommige profielcirkels. Naast de wrapperfuncties worden nu ook Glide-aanroepen `kw9.z(ImageView)` en `kw9.y(target, options)` onderschept. Bij expliciete targets wordt alleen een `vw0`-ImageView-target met een katten-URL in requestveld `M` via de kattenrenderer geladen. Andere modellen en targets behouden de originele Glide-aanroep en opties. Reflectiesignaturen en beide velden worden tegen de basis-APK gecontroleerd. Geen namen of berichtinhoud uit screenshots verwerkt. Visuele bevestiging van berichten en zijbalk op het toestel blijft nodig.

Revision **r15**: ook de negen rechtstreekse `d94.a`-laadaanroepen worden onderschept, inclusief de delegatie vanuit `d94.b`. Alleen katten-URL’s gebruiken de eigen decoder; overige aanvragen behouden hun originele argumenten en loader. Kattenavatars krijgen nul binnenpadding zodat de foto hetzelfde vlak vult als de ronde clip. De weergave wordt opnieuw ingesteld bij het afleveren van de foto, wanneer de actuele layout bekend is. CoverDrawable schaalt op zijn werkelijke bounds; er is geen lijst met vaste cirkelgroottes. Schaalcontrole omvat elf diameters en staande, liggende en vierkante bronnen. Berichten en de menuzijbalk moeten nog visueel op het toestel worden bevestigd.

Revision **r14**: de streakbalk neemt een achtergrondpixel van de zichtbare homepagina over. Profielfoto’s worden expliciet rond geclipt. CoverDrawable gebruikt voor de schaal max(vakbreedte/fotobreedte, vakhoogte/fotohoogte): in een cirkel is de kortste zijde minimaal de diameter. De lange zijde wordt vanuit het midden bijgesneden. Er worden geen vermeende witte randen uit de bronfoto verwijderd. GIFs blijven als animatie beschikbaar. Uiterlijk op het toestel controleren.

Revision 13: streakbalk transparant; tellertekst volgt licht/donker systeemthema. Witte knop met zwarte medium tekst blijft behouden. Runtime en ingebedde payload opnieuw gebouwd. Visuele aansluiting op de achtergrond op het toestel controleren.

Revision 12: zwarte streakknoptekst in sans-serif-medium (14 sp). Afgerond kattenmenu met dynamische Android 12+-kleuren, afgeronde knoppen en selectieomlijning voor de favoriete kat. Vier extra eo0-afbeeldingsladers behandelen katten met center crop; andere URL’s behouden de originele laadfunctie. API-signaturen van alle vier gecontroleerd tegen de originele APK. Wachtrij uitgebreid en achterhaalde aanvragen worden vóór laden genegeerd. Op het toestel moeten vormgeving en de eerder afwijkende foto’s nog gecontroleerd worden.

Revision 11: streakbalk #FE5000. De afgeronde knop blijft #FFFFFF na het vangen, met oranje tekst ‘Kom morgen terug’. Dagelijkse blokkering blijft actief. Runtime en ingebedde RuntimePayload opnieuw gebouwd.

Revision **r10**: de DEX-runtime wordt nu als gegenereerde Java-bron (`RuntimePayload`) met de patchengine gecompileerd. Een verouderd runtime.dex-bestand uit assets/cache wordt niet meer gelezen. De build genereert beide uitvoerbestanden vóór het compileren/verpakken; linkagevalidatie blijft actief. Installeer de patcher volledig met Run, maak een nieuwe patch en installeer r10. De bestaande download, favorieten, streak en ondertekeningssleutel hoeven niet gewist te worden.

Revision 9 — regressie gemeld op telefoon: `NoSuchMethodError configureCard` in rc5.b. De lokale bronruntime bevat de exacte methode; het toestel kreeg dus een verouderde runtime bij de nieuwe engine. De Gradle-build bouwt de runtime voortaan automatisch vóór preBuild/mergeAssets. PatchValidation vergelijkt statische runtime-aanroepen met de exacte methodehandtekeningen in alle uiteindelijke DEX-bestanden. RuntimeLinkageTest verwijdert bewust configureCard uit een juiste APK en controleert dat die gebroken APK wordt afgewezen, terwijl de juiste APK wordt geaccepteerd. Nieuwe outputnaam r9 voorkomt hergebruik van een eerder r8-resultaat. Volledige Gradle-build en fysieke opstart moeten nog in Android Studio/op het toestel bevestigd worden.

Revision 8: 32 native profielfoto-laadaanroepen verwijzen naar een katten-specifieke decoder die eerst de verhouding behoudt en vervolgens vanuit het midden vierkant bijsnijdt. Andere URL’s blijven via de originele lader lopen. Bij hergebruik van een ImageView worden verouderde resultaten genegeerd. De zwevende kattenknop is verwijderd. Alleen de ConfigureCard op home bevat nu ‘Startscherm instellen’ en een vierkante kattenknop, met dezelfde kleurresources, 16-dp-hoeken, rand en ripple. De bestaande startschermactie blijft behouden; AndroidView zorgt dat de rij met de originele home-inhoud meescrollt en verdwijnt.

Lokaal geslaagd: runtime/payload compilatie, één ConfigureCard-hook, 32 afbeeldinghooks, API-signaturen gecontroleerd tegen de echte APK (inclusief geërfde afbeeldingslader), handtekeningen, 16-KiB-uitlijning, behoud van 23.899 originele classes en 4.734 model-/SDK-methoden. Tests gebruiken de echte basis-APK en synthetische splitonderdelen. De beeldvulling, AndroidView-recompositie en plaatsing moeten nog op de Motorola visueel getest worden.

Revision 7: home-streakbalk krijgt #FF4900 met witte tekst. De muisknop is een witte afgeronde pil (24 dp hoeken, 48 dp hoog) met oranje tekst, ripple en een aparte afgeronde uitgeschakelde toestand. Runtime opnieuw gecompileerd en DEX-payload gebouwd; uiterlijk nog op toestel te controleren.

Revision 6: streak, record en dagelijkse muisknop verplaatst van het kattenmenu naar een compacte balk direct boven de begroeting op home. Bestaande voorkeuren/teller blijven behouden. De home-hook ontvangt bij methode-ingang het oorspronkelijke View-register; layoutreserve voorkomt overlap met de begroeting. De balk verdwijnt met de home-view en ververst bij terugkeer en klikken. Dagelijkse controle voorkomt dubbele telling. Plaatsing op het toestel moet nog visueel bevestigd worden.

Revision 5: favorietenselectie toont vierkante vakjes van 80 × 80 dp met licht afgeronde hoeken. CENTER_CROP en de uitsnede vanuit het midden behouden de beeldverhouding. Profielcirkels blijven rond. Runtime opnieuw gecompileerd en DEX-payload gebouwd.

Revision 4: kattenvoorbeelden behouden de beeldverhouding bij decoderen en worden daarna vanuit het midden vierkant bijgesneden. De favoriete-kattengalerij toont ronde, even grote voorbeelden met CENTER_CROP. Geanimeerde afbeeldingen blijven geanimeerd. De originele Smartschool CircleImageView gebruikt reeds CENTER_CROP voor profielcirkels; deze weergave blijft behouden.

Revision 3 lokaal gecontroleerd: Kotlin/Compose-bronnen compileren; payload opnieuw gebouwd; volledige patchcontrole op de echte 12.3.0-basis-APK met synthetische ABI/density-testonderdelen slaagt. Zes foto-editoracties uitgeschakeld, 23.899 originele classes behouden, 4.734 model-/SDK-methoden onveranderd, manifest zonder editorprovider, ondertekening en 16-KiB-ZIP-uitlijning gecontroleerd. De synthetische testonderdelen zijn geen installeerbare Smartschool-release. Volledige Gradle-build en ADB-opstarttest zijn in deze omgeving geblokkeerd door socketrestricties; opstart op het toestel moet nog bevestigd worden.


## Patchrevision 3 — opstartcrash en eenvoudiger bediening

Het telefoonlog toont een fatale `IMGLYAutoInit`-licentiefout doordat de tweede app een ander pakket-ID heeft. Revision 3 verwijdert de optionele foto-editorprovider en editoractiviteiten uit de manifesten. Acties in Smartschool die deze editor openen worden uitgeschakeld met een melding. De SDK-licentie en autorisatiecontroles blijven behouden. Gebruik de officiële Smartschool-app voor de ingebouwde foto-editor.

De patcher toont één grote volgende-stapknop: downloaden → doorgaan → patch maken → installeren. Opslaan, opnieuw downloaden en een nieuwe patch maken staan kleiner onder ‘Extra opties’. ‘Terug’ is een secundaire knop.

Maak na het bijwerken van de patcher een nieuw installatiepakket (r3) en installeer dit. Eerdere pakketten worden niet als het nieuwe resultaat gebruikt. Lokale controles vervangen geen opstarttest op de Motorola; die bevestiging is nog nodig.

# Opstartfix revision 2 (7 oktober 2026)

De gebruiker bevestigde dat installeren lukt, maar meldde een crash bij starten.
In de echte bytecode is een concrete fout gevonden: het oorspronkelijke
`Application.onCreate` overschrijft v9/p0 met een String op het retourpad bij
"Cannot plant Timber into itself.". De oude patch gaf dat register vervolgens
als Application door aan `init`, wat ongeldige registertypes oplevert.

Herstel en controles:

- Eén `init(Application)` aan methode-entry met het inkomende receiverregister.
- Productiecontrole weigert iedere init-aanroep elders of met het verkeerde register.
- De eerdere echte test-APK wordt aantoonbaar als onveilig afgewezen.
- De volledige engine met de echte basis-APK en synthetische configuraties slaagt
  met 256 MB heap. De fixtures zijn geen werkende Smartschool-installatie.
- 23.899 originele classes blijven behouden; 61 foto-aanpassingen en één init-hook.
- 807 constructors/equality/hashCode/toString/kopieer-/parcelbewerkingen hebben
  dezelfde instructies, registers, referenties, literals en exception handlers.
- Runtime javac/D8 en Kotlin/Compose-compilatie slagen met de lokale tools.
- Pakketonderdelen, gelijke certificaten, signatures en 16 KiB ZIP-alignment slagen.
- Zeven dagelijkse streak-scenario's slagen.

Extra herstel: immutable kattenlijst met veilige publicatie tussen threads,
begrensde en uitgestelde afbeeldingstaken, weak references naar gesloten schermen,
atomaire cachebestanden en verwijderen van kapotte afbeeldingen. Kattenfouten
vallen terug op oorspronkelijke profielfoto's. Crashgegevens worden lokaal
opgeslagen; Androids normale fatale-foutafhandeling blijft behouden.

Niet bevestigd: starten/aanmelden en de kattenfuncties op de Motorola na revision 2.
ADB en Gradles sockets blijven hier geblokkeerd; er is geen echte telefoon-crashlog
ontvangen en geen volledige Android-build vanuit deze agentomgeving uitgevoerd.

## Eerdere installatie- en structuurcontroles (historisch)

# Installatiefix voor APK-onderdelen (7 oktober 2026)

De eerdere losse test-APK voldoet aan signature-/DEX-controles, maar is **niet
installeerbaar** zonder de verplichte splits. Het bronmanifest bevat
`requiredSplitTypes="base__abi,base__density"`. De oude installatieproef is
vervangen door volledige APK-pakketten.

Nieuwe controles:

- Java-engine en Kotlin/Compose compileren met de lokale compiler/classpath.
- `tools/test-splits.py` compileert binaire manifest-fixtures met aapt2.
- Ontbrekende ABI/density, dubbele splits en afwijkende versies worden afgewezen.
- De echte gedownloade basis-APK wordt als onvolledig herkend.
- Volledig pakket blijft geldig na uitpakken, hernoemen, ondertekenen en verpakken.
- Elk onderdeel heeft de kattenpakketnaam en dezelfde geverifieerde certificaatsleutel.
- Native ZIP-bestanden zijn ongecomprimeerd en 16 KiB uitgelijnd (`zipalign -P 16`).
- De volledige engine verwerkt de echte basis-APK met synthetische ABI/density-
  fixtures onder een heap van 256 MB: 200 avatarhooks en een geldig pakket van
  drie APKs. Dit test de bewerking, niet de echte native bestanden of installatie.
- De installatiecode schrijft alle onderdelen in één PackageInstaller-sessie en
  behandelt Androids gebruikersbevestiging en concrete resultaatmelding.

Nog vereist: live Aptoide-download en bevestigde installatie/werking op de Motorola
Edge 60 met Android 16. Hier zijn DNS en Gradles sockets geblokkeerd. Synthetische
fixtures bewijzen geen complete echte download of werking op een telefoon.

## Eerdere controles van de losse APK (historisch)

# Validatie van de eerste kattenpatch

Bron-APK: Smartschool 12.3.0, versiecode 120300, minimum API 32.
Uitvoer: `artifacts/Smartschool-Katten-12.3.0.apk`.

Geslaagd in de agentomgeving:

- Injectieruntime gecompileerd met javac (Java 11) en D8 voor API 32.
- Java-patchengine gecompileerd; engine en alle vendored dependencies naar DEX
  gecompileerd met D8 voor de patcher (API 26).
- Kotlin/Compose-code gecompileerd met lokale Kotlin- en Compose-compilers. Dit
  is een aanvullende codecontrole; niet hetzelfde als de volledige AGP-build.
- Echte APK gepatcht met 256 MB Java-heaplimiet.
- 23.899 originele classes behouden, zonder dubbele classes. Extra runtime en
  overgelopen DEX-classes correct toegevoegd aan een zesde DEX.
- 200 avatarveld-lezingen voorzien van helpers. Null/lege avatars behouden hun
  oorspronkelijke afwezigheid; algemene afbeeldingen worden niet omgeleid.
- Appnaam `Smartschool Katten`, pakket `be.smartschoolkatten.mobile`, versie 12.3.0
  bevestigd via aapt. Pakketgebonden provider-authorities en eigen permission
  hebben de nieuwe naam; de bestaande Java/Kotlin-klassennamen blijven behouden.
- Nieuw adaptive icoon verwijst naar de juiste bestaande resource-ID’s.
- APK-handtekening geverifieerd met de engine en Android SDK apksigner.
- `zipalign -c -v 4` geslaagd; resources.arsc is opgeslagen en 4-byte uitgelijnd.
- Zeven streakscenario’s: eerste klik, dubbele klik, volgende dag, overgeslagen
  dag, herstart na gat, zomertijdgrens en jaargrens.

Nog niet uitvoerbaar in deze omgeving:

- Gradle/AGP-build: de Gradle-start blokkeert op het bepalen van een bruikbaar
  wildcard-netwerkadres. ADB kan evenmin een luistersocket openen.
- Installatie en ART-verificatie op een echt toestel of emulator.
- Aanmelden, avatarweergave in alle modules, GIF-avatars, pushmeldingen en delen.

Toesteltest:

1. Bouw en installeer de bijgewerkte patcher via Android Studio.
2. Download, patch en installeer via de knoppen; controleer dat beide apps bestaan.
3. Meld aan en open home, berichten, resultaten en een gebruikerskaart.
4. Kies een favoriete kat; heropen het profiel en controleer de afbeelding.
5. Tik op ‘Aai de kat’ en controleer dat opnieuw klikken dezelfde dag niet meetelt.
6. Test katten aan/uit, APK opslaan, bestandsdeling en meldingen.
7. Meld eventuele fouttekst of crashlog. Die uitkomsten bepalen of deze testversie
   ook functioneel klaar is; statische controles alleen bewijzen dat nog niet.
