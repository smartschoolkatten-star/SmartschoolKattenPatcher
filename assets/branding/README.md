# Smartschool Katten appicoon

`smartschool-katten-icon-source.jpg` is de door de gebruiker aangeleverde
1024×1024 JPEG uit Downloads/YmrXB.jpg. Gebruik dit als bron voor het launchericoon
van de tweede app Smartschool Katten. De patcher heeft een eigen icoon.

De build gebruikt dit bestand voor het launchericoon en vanaf r19 ook voor het
logo bij aanmelden/account toevoegen, ‘Over Smartschool’ en opstarten.
`tools/BuildBranding.java` rendert transparante, afgeronde hoeken met de originele
verhouding (5/48 voor het app-logo). De splashvariant behoudt de oorspronkelijke
marge (64/240) en afronding (11,2/112). Het bronbestand blijft ongewijzigd.

Het patchericoon is de afzonderlijke 1024×1024 JPEG `Downloads/ECm6b.jpg`,
opgenomen als `app/src/main/res/drawable-nodpi/patcher_icon.jpg` en gekoppeld
aan de adaptive launchericonen met marge voor launcher-maskers.
