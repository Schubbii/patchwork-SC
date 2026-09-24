# PhoneZone (Prototyp)

Android-App: Per AR einen Bereich auf dem Schreibtisch festlegen. Liegt das Handy dort, ist es gesperrt (nur Notfall). Nimmst du es wieder heraus, ist es frei.

## Benutzung

1. App öffnen und Kamera erlauben.
2. Handy langsam über den Tisch bewegen, bis eine Fläche erkannt wird.
3. Auf den Tisch tippen: Ein blaues Quadrat (24 × 24 cm) erscheint.
4. Handy mit dem Display nach oben in das Quadrat legen. Der Sperrbildschirm erscheint.
5. Beim ersten Mal fragt Android, ob die App **angeheftet** werden soll. Bestätigen.
6. Handy hochnehmen: Es wird entsperrt. Der Knopf **Notfall** entsperrt das Handy sofort.

## So funktioniert es

- **Hineinlegen:** ARCore verfolgt die Position der Kamera. Ist sie weniger als 8 cm über dem Quadrat, wird gesperrt.
- **Herausnehmen:** Auf dem Tisch sieht die Kamera nichts mehr. Deshalb erkennt der Beschleunigungssensor, wenn das Handy hochgenommen oder gekippt wird.
- **Sperre:** Android-„App-Anheften“ (Screen Pinning) blockiert Home- und Zurück-Taste.

## Grenzen des Prototyps

- Die App muss offen sein, wenn du das Handy ablegst (AR läuft nur im Vordergrund).
- Die Sperre ist keine echte Gerätesperre. Wer Zurück + Übersicht lange drückt, kann das Anheften beenden. Eine harte Sperre bräuchte den „Device Owner“-Modus.
- Der Bereich wird nicht gespeichert. Nach einem Neustart der App musst du ihn neu setzen.
- Das Verschieben auf dem Tisch ohne Anheben wird nicht immer erkannt.
- Benötigt ein Gerät mit ARCore-Unterstützung.

## Bauen

- **Android Studio:** Ordner öffnen und auf „Run“ klicken.
- **Ohne Android Studio:** Jeder Push baut über GitHub Actions eine APK. Du findest sie unter *Actions → Build APK → Artifacts*.
