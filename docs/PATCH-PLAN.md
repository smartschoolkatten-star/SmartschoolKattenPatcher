# Huidige implementatie

De eerste 12.3.0-kattenpatch is nu gebouwd. De engine staat in
`app/src/main/java/be/smartschoolkatten/patcher/engine/`, de geïnjecteerde functies
in `patch-runtime/`. De patcher kan de tweede app maken, exporteren en de
Android-installer openen. Het verdere document hieronder bewaart de oorspronkelijke
analyse; eerdere vermeldingen van ontbrekende code zijn historisch.

Zie [VALIDATION.md](VALIDATION.md) voor de uitgevoerde controles en de nog benodigde
toesteltest. De huidige versie voegt 200 avatarhooks toe, inclusief `userPictureUrl`
en geïdentificeerde UI-modellen (`ik0.a`, `di9.e`, `nza.c`). De overige afbeeldingen
worden niet algemeen vervangen. Favorieten en muizenspel staan in een native
kattenmenu, geopend met een zwevende 🐈-knop op NavigationDrawerActivity.

---

# Kattenpatch voor Smartschool 12.3.0

## Vastgelegd

De patcher downloadt release 76524701 en accepteert alleen versie 12.3.0.
Doorgaan opent het voorbereidingsscherm. Er wordt nog geen APK aangepast.

Gewenst gedrag: profielfoto’s vervangen door katten, een katten-appicoon en een
lokale dagelijkse streak. Een browserfavicon heeft geen directe tegenhanger in
een native Android-app; het launchericoon is de voorgestelde vertaling.

## Aanpak, van makkelijk naar moeilijk

1. Onderzoek de echte 12.3.0-APK op de ontwikkelcomputer. Bekijk het manifest,
   de resources en de code die profielfoto’s laadt. Bepaal eerst of de schermen
   native Android zijn of WebViews; dat is nog onbekend.
2. Maak eerst een ongewijzigde, opnieuw ondertekende proefbuild. Test installatie,
   aanmelden en berichten. Als herondertekenen de werking verhindert, onderzoek
   dat voordat de functionele patch wordt gebouwd.
3. Vervang de bestaande launchericoon-resources door katten. Behoud de resource-ID’s
   en ondersteun zowel adaptive als oudere iconvarianten. Test dit afzonderlijk.
4. Voeg lokale kattenafbeeldingen toe en vervang de avatar-laadroute. Bij native
   schermen: vind de centrale avatarbinder of afbeeldingsloader en wijzig alleen
   de profielfoto-route. Bij WebViews: onderzoek een script op het juiste moment
   en de avatar-elementen. Pas geen algemene afbeeldingsvervanging toe, anders
   veranderen ook bijlagen en andere afbeeldingen. Geef dezelfde persoon steeds
   dezelfde kat met een stabiele lokale toewijzing.
5. Voeg een streak toe in de gepatchte Smartschool-app, niet in de patcher.
   Bewaar laatste bezoekdatum en aantal lokaal. Zelfde kalenderdag: ongewijzigd;
   volgende dag: +1; één of meer dagen overgeslagen: opnieuw 1. Kies pas na
   inspectie een geschikte zichtbare plek en lifecycle-aanroep. Gebruik geen
   serverwijzigingen voor deze visuele functionaliteit.
6. Maak een herhaalbare patch voor exact 12.3.0: resources wijzigen, gerichte
   codewijzigingen toepassen, APK opbouwen, uitlijnen, ondertekenen en controleren.
   Bewaar het originele bestand en schrijf de uitvoer naar een apart bestand.
7. Breng de bewezen patch naar de Android-patcher. Selecteer pas na het prototype
   libraries voor DEX/resourcebewerking en APK-ondertekening. Desktoptools kunnen
   niet zomaar als uitvoerbaar bestand in Android worden gebruikt.
8. Voeg voortgang, uitvoer opslaan en installatie via Android toe nadat het
   daadwerkelijke patchproces werkt.

## Ondertekenen en installatie

APK-wijzigingen maken de bestaande handtekening ongeldig. Gebruik een eigen,
blijvende sleutel voor de gepatchte variant. Met hetzelfde pakket-ID maar een
andere sleutel kan deze niet als gewone update over de officiële app heen worden
geïnstalleerd. Een aparte pakketnaam vereist aanvullend onderzoek naar providers,
login, deep links en pushberichten. De gebruiker heeft gekozen voor een aparte appvariant: **Smartschool Katten**.
Voorgesteld pakket-ID: `be.smartschoolkatten.mobile` (de patcher blijft
`be.smartschoolkatten.patcher`).

Bronnen:
- https://developer.android.com/tools/apksigner
- https://developer.android.com/studio/publish/app-signing

## Onderzoek van de aangeleverde APK

Bestand: `/home/bent/Downloads/Smartschool-12.3.0.apk`.
Pakket `be.smartschool.mobile`, versienaam `12.3.0`, versiecode `120300`.
Minimum SDK 32 (Android 12L); target SDK 37. De patcher zelf ondersteunt oudere
Android-versies, maar dat maakt de gedownloade app niet daarop installeerbaar.
SHA-256: `0ff3910b909bcb1e95b0e0ee796d4441bf3a29a85ab0f8a74d90eaa3710a3d25`.

Onderzocht met aapt en dexdump uit Android SDK Build Tools 36.0.0, zonder de APK
te wijzigen. Er zijn vijf DEX-bestanden en native Android/Compose-componenten.
De HTML-assets betreffen onder meer een teksteditor; dit is geen bewijs dat de
hele app een WebView is. Een algemene browserextensie-injectie is dus geen
onderbouwde patchaanpak.

Concrete aanknopingspunten:
- `be.smartschool.mobile.model.User.getAvatarUrl()` en `setAvatarUrl()`.
- Afzonderlijke avatargegevens in `modules.results.data.LvsResultsUser`,
  `network.responses.AccountInfo`, `model.messages.User`, `Message` en
  `MessageDetail`; een enkele getter wijzigen dekt niet aantoonbaar alles.
- Aanwezigheid van Coil 3-afbeeldingslaadcode. Onderzoek of avatar-aanvragen
  centraal te onderscheiden zijn voordat een specifieke loader wordt aangepast.
- Launcherresources `drawable/ic_launcher_background` (0x7f080b49) en
  `drawable/ic_launcher_foreground` (0x7f080b4a).
- `be.smartschool.mobile.NavigationDrawerActivity` en de home-module zijn
  kandidaten voor een zichtbare streak. Het daadwerkelijke invoegpunt is nog
  niet bepaald.

Volgende technische stap: de APK decompileren met JADX/Apktool, de avatarcallers
traceren en eerst een herondertekende ongewijzigde proefbuild testen. Die tools
zijn hier nog niet beschikbaar; netwerktoegang vanuit de shell werkt niet.
Er is nog geen functionele patch of installatieproef uitgevoerd.

## Onderzoek van de Chrome-extensie 1.93

Aangeleverd CRX: `/home/bent/Downloads/ndembkilcdoipaefhmbnoiamccnkjgco.crx`.
Uitgepakt en als tekst gelezen; geen extensiescripts uitgevoerd.
Afbeeldingslinks zijn vastgelegd in `extension-assets.json` (18 katten, waaronder
GIF’s, en één faviconlink). De beschikbaarheid van deze links is niet getest.

Vastgesteld gedrag uit content.js:
- Alleen afbeeldingen waarvan de URL `userpicture` bevat worden vervangen,
  met zichtbare grootte groter dan 0 en hoogstens 120 pixels.
- Een Map kiest willekeurig een kat per originele foto-URL. Dit is paginageheugen,
  geen blijvende koppeling aan een persoon. Na herladen kan de kat veranderen.
- Het eigen profiel bovenaan gebruikt een opgeslagen favoriete kat. Sterknoppen
  bij avatars laten de gebruiker een andere kat als favoriet bewaren.
- Ronde avatars met hoververgroting en gloed; dat vraagt een aparte vertaling
  naar aanraakbediening op Android.
- Een aan/uit-schakelaar in de extensiepopup.
- Op het startscherm staat een muis. Klikken telt maximaal één keer per lokale
  kalenderdag. Opeenvolgende dagen verhogen de streak; bij een gat begint de
  volgende klik op 1. Na twee of meer dagen toont het scherm eerst 0.
- Het beste record blijft bewaard. De muis verdwijnt na de klik voor die dag.
- Favoriet, streak, record, datum en aan/uit staan in chrome.storage.sync.
  Android gebruikt eigen lokale opslag; browsersynchronisatie is niet automatisch
  beschikbaar in de gepatchte app.

Correctie op het eerdere voorstel: check-in vereist een muisklik, geen automatische
verhoging bij openen. Een blijvende persoon-naar-kat-koppeling zou bovendien afwijken
van de oorspronkelijke extensie. Voor een getrouwe port moeten we deze verschillen
bewust vermijden en eerst de native avatar-laadroutes traceren.

## Tweede app en icoon

De gepatchte app moet naast de officiële Smartschool-app installeerbaar zijn,
met label `Smartschool Katten` en een eigen pakket-ID. Alleen het label wijzigen
maakt geen tweede app. Wijzig manifestpackage, projectgebonden provider-authorities
(en eventuele hardgecodeerde verwijzingen daarnaar), eigen permissions en eventuele
pakketgebonden intentverwijzingen. Behoud bestaande volledige Java/Kotlin-
klassennamen tenzij inspectie een gerichte wijziging vereist; geen globale
tekstvervanging van alle `be.smartschool.mobile`-waarden.

Het originele manifest bevat onder meer packagegebonden IMGLY-, Firebase- en
Sentry-provider-authorities. Aanmelden, pushberichten, deep links en bestandsdeling
moeten op een toestel getest worden; een gewijzigde app-identiteit kan invloed
hebben op integraties. Hun werking is nog niet aangetoond.

De aangeleverde extensie heeft PNG-iconen van 16×16, 48×48 en 128×128 pixels.
`icon128.png` toont een witte S in kattenvorm op een oranje-rode achtergrond.
Dit is een bruikbaar voorlopig icoon, maar 128×128 kan op hoge pixeldichtheid
minder scherp zijn. Een origineel van 512×512 of groter of een vectorbron heeft
voor definitieve launcherresources de voorkeur; opschalen voegt geen detail toe.
De externe faviconlink is nog niet gedownload en bytegewijs vergeleken met het
gebundelde icon128.png.

## Definitieve icoonbron aangeleverd

De gebruiker heeft `Downloads/YmrXB.jpg` aangeleverd. Dit is een 1024×1024 JPEG,
visueel hetzelfde witte katten-S-logo op oranje-rood. De ongewijzigde bron staat
in `assets/branding/smartschool-katten-icon-source.jpg`. Deze vervangt het
128×128-extensieicoon als bron voor de launcherresources van de tweede app.
De daadwerkelijke resources en gepatchte APK zijn nog niet gegenereerd.
