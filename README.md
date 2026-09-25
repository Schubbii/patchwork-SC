# PhoneZone (Prototyp)

Android-App: Per AR einen Bereich festlegen. Solange dein Handy dort ist, ist es gesperrt, entweder komplett oder nur ausgewählte Apps.

## Benutzung

1. Auf der Startseite wählen:
   - **Bereich:** *Ablage* (Handy liegt auf dem Tisch) oder *Arbeitsplatz* (du sitzt z. B. am Schreibtisch).
   - **Was wird gesperrt?** *Ganzes Handy* oder *Nur Apps*. Bei *Nur Apps* die Apps auswählen und die Bedienungshilfe aktivieren.
2. **Bereich festlegen** tippen und das Handy langsam bewegen, bis eine Fläche erkannt wird.
3. Auf die Fläche tippen, um die Mitte zu setzen. Mit dem Schieberegler die Größe einstellen (20 cm – 3 m).
4. Mit dem Handy in den Bereich gehen bzw. es hineinlegen. Die Sperre startet.
5. Entsperren:
   - *Ablage:* Handy hochnehmen.
   - *Arbeitsplatz:* weggehen (ca. 10 Schritte).
   - Jederzeit: **Notfall – entsperren**.

## So funktioniert es

| Teil | Technik |
|---|---|
| Bereich betreten | ARCore verfolgt die Kameraposition relativ zum Bereich. |
| Ablage verlassen | Beschleunigungssensor erkennt Hochnehmen oder Kippen. |
| Arbeitsplatz verlassen | Schrittzähler (Berechtigung „Körperliche Aktivitäten“). |
| Ganzes Handy sperren | Android „App anheften“ blockiert Home- und Zurück-Taste. |
| Nur Apps sperren | Eine Bedienungshilfe überdeckt gesperrte Apps beim Öffnen. |

## Grenzen des Prototyps

- Die App muss offen sein, wenn du den Bereich betrittst. AR läuft nur im Vordergrund.
- Der Bereich wird nicht gespeichert und muss jedes Mal neu gesetzt werden.
- Die Sperre ist keine echte Gerätesperre. Das Anheften lässt sich mit Zurück + Übersicht beenden.
- Android 13+: Bei per APK installierten Apps ist die Bedienungshilfe erst nach *App-Info → ⋮ → Eingeschränkte Einstellungen zulassen* aktivierbar.
- Benötigt ein Gerät mit ARCore-Unterstützung.

## Bauen

- **Android Studio:** Ordner öffnen und auf „Run“ klicken.
- **GitHub Actions:** Jeder Push baut eine APK, zu finden unter *Actions → Build APK → Artifacts*.

## Code

| Datei | Aufgabe |
|---|---|
| `MainActivity.kt` | Startseite mit Einstellungen |
| `ZoneActivity.kt` | AR-Ansicht, Bereich festlegen und Betreten erkennen |
| `GlRenderers.kt` | Kamerabild und Bereich zeichnen (OpenGL) |
| `ZoneLock.kt` | Sperrzustand und Erkennung des Verlassens |
| `LockActivity.kt` | Sperrbildschirm |
| `AppBlockerService.kt` | Sperrt einzelne Apps |
| `AppPickerActivity.kt` | App-Auswahl |
| `Prefs.kt` | Gespeicherte Einstellungen |
