# Lokale patchlibraries

Deze JAR’s zijn als lokale dependencies gekoppeld in app/build.gradle.kts zodat
patchen op het toestel werkt zonder libraries tijdens gebruik te downloaden.

- smali-dexlib2 3.0.9 — uit de geïnstalleerde Android Studio-distributie.
  DEX lezen/bewerken/schrijven; BSD-3-Clause.
  https://github.com/google/smali
- Guava 33.4.0-jre en failureaccess 1.0.2 — uit de lokale Gradle-cache.
  Collections voor dexlib2; Apache-2.0.
  https://github.com/google/guava
- Android apksig 9.4.1 — uit de lokale Gradle-cache.
  APK ondertekenen en handtekeningen verifiëren; Apache-2.0.
  https://android.googlesource.com/platform/tools/apksig/

Gebruik bij upgrades de bijbehorende libraryversies en voer de APK-checks opnieuw
uit. De patch blijft specifiek voor Smartschool 12.3.0.
