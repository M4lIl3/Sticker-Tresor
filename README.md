# Sticker-Tresor

Android-App, die deine WhatsApp-Sticker separat sichert – damit sie nicht verloren gehen, wenn du WhatsApp neu installierst, Chats löschst oder das Handy wechselst.

## Was die App macht

- **Sichern:** kopiert alle Sticker aus dem WhatsApp-Sticker-Ordner in den Tresor und zusätzlich nach **Bilder/Sticker-Tresor**, ohne Doppelte
- **Kategorien:** eigene Kategorien mit Farbe anlegen und Sticker zuordnen (einzeln oder mehrere auf einmal), dazu Filter für Neu, Animiert und Statisch
- **Packs für WhatsApp:** eigene Sticker-Packs zusammenstellen und mit einem Tipp zu WhatsApp hinzufügen
- **Emojis:** jedem Sticker bis zu 3 Emojis geben, damit WhatsApp ihn über die Emoji-Suche findet
- **Automatisch:** auf Wunsch einmal täglich, während das Handy lädt
- **Dunkles Design** mit Jetpack Compose

Die App braucht keine Speicher-Berechtigung und kein Internet. Sie liest nur den einen Ordner, den du ihr freigibst.

## Bedienung

- **Tresor:** alle Sticker. Antippen öffnet die Details (Kategorien, Emojis, Teilen, Löschen). Lange drücken startet die Mehrfachauswahl.
- **Kategorien:** anlegen, bearbeiten, eine Kategorie direkt als Pack anlegen
- **Packs:** WhatsApp verlangt 3–30 Sticker pro Pack, entweder alle animiert oder alle statisch. Die App zeigt an, was noch fehlt. Der erste Sticker wird das Pack-Symbol.

## Installieren

1. Auf dem Handy auf der GitHub-Seite dieses Repos rechts (oder unten) auf **Releases** tippen
2. Beim neuesten Release unter **Assets** die Datei `Sticker-Tresor-v1.0.X.apk` herunterladen
3. Die Datei öffnen. Beim ersten Mal fragt Android, ob dein Browser Apps installieren darf → **Einstellungen → Zulassen**, zurück, **Installieren**
4. Falls Google Play Protect warnt („Unbekannte App“): **Weitere Details → Trotzdem installieren**

Updates installierst du genauso; die App und deine Sicherung bleiben dabei erhalten.

## Einrichten

1. App öffnen → **WhatsApp-Sticker-Ordner verbinden**
2. Der richtige Ordner ist schon geöffnet (*Android › media › com.whatsapp › WhatsApp › Media › WhatsApp Stickers*) → unten **Diesen Ordner verwenden** → **Zulassen**
3. **Jetzt sichern** tippen

## Gut zu wissen

- Gesichert werden die Sticker, die WhatsApp auf dem Handy gespeichert hat, also alle, die du empfangen oder gesendet hast. Die Favoritenliste selbst steckt in der WhatsApp-Datenbank und lässt sich nicht auslesen.
- Löschst du gesicherte Sticker in der Galerie, werden sie nicht erneut gesichert, solange die App installiert ist.

## Technik

Kotlin + Jetpack Compose, minSdk 30 (Android 11). Sticker-Packs werden WhatsApp über einen ContentProvider nach dem offiziellen Schema angeboten. Jeder Push auf `main` baut über GitHub Actions eine signierte APK und veröffentlicht sie als Release. Der Signaturschlüssel liegt als Repository-Secret (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`). Ohne diese Secrets entsteht nur eine Test-APK als Actions-Artefakt, kein Release.

Lokal bauen: Projekt in Android Studio öffnen oder `./gradlew assembleRelease`.
