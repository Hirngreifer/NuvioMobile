# NuvioMobile

Multiplattform-Streaming-App (Android/iOS, Compose Multiplatform). Dieses Glossar hält die
verbindliche Sprache des Projekts fest; begonnen mit dem Watch-Party-Kontext.

## Language

### Watch Party

**Watch Party**:
Eine synchronisierte Wiedergabesitzung mehrerer Teilnehmer über einen gemeinsamen Raum.
_Avoid_: Sync-Session, Group Watch

**Raum (Room)**:
Der adressierbare Treffpunkt einer Watch Party, identifiziert durch einen Room-Code. Ein Raum existiert, solange er Teilnehmer hat.
_Avoid_: Channel, Session (Session bezeichnet die lokale Verbindung eines Teilnehmers zum Raum)

**Room-Code**:
Kurzer, teilbarer Code, der einen Raum identifiziert und zum Beitreten berechtigt.

**Teilnehmer (Participant)**:
Eine im Raum sichtbare Identität. Der Anzeigename wird beim Beitritt festgelegt (explizite Eingabe, sonst Name des aktiven Profils) und ändert sich während der Teilnahme nicht.
_Avoid_: User, Member

**Profilwechsel-Regel**:
Party-Teilnahme hängt an der Identität: Ein Profilwechsel beendet die Teilnahme automatisch (Auto-Leave). Es gibt keinen automatischen Wiederbeitritt; das neue Profil tritt bewusst bei.

**Lobby**:
Der Screen zum Erstellen, Beitreten und Wiederbeitreten von Räumen inklusive Teilnehmerliste.
_Avoid_: Watch-Party-Screen (UI-Dateiname, kein Domänenbegriff)

**Rejoin-Shortcut**:
Ein-Tap-Wiederbeitritt zum zuletzt genutzten Raum des aktiven Profils; wird nur angeboten, wenn der Raum noch belegt ist. Die Raum-Historie ist profilgebunden.

**Occupancy-Peek**:
Unsichtbare Prüfung, ob ein Raum Teilnehmer hat, ohne ihm beizutreten oder darin sichtbar zu werden.

**Presence**:
Das Lebenszeichen eines Teilnehmers im Raum (inkl. Wiedergabestatus). Presence-Updates unterliegen einem Budget, um das Backend nicht zu überlasten.

**Snapshot**:
Engine-entkoppelter Wiedergabezustand (Inhalt, Position, Pause/Play), über den sich Teilnehmer angleichen.

**Drift-Korrektur**:
Periodisches Angleichen der lokalen Wiedergabeposition an den Raum.

**Follow**:
Das automatische Mitgehen eines Teilnehmers, wenn der Raum den Inhalt wechselt — innerhalb des Players (In-Player-Follow) oder per App-Start der Wiedergabe (Launch-Follow).

**Content-Change-Hold**:
Koordinierte Wartephase des Raums während eines Inhaltswechsels, bis alle Teilnehmer bereit sind (Auto-Resume).

**Umzugs-Prompt**:
Rückfrage an einen Teilnehmer, dessen lokaler Inhalt vom Raum abweicht, ob er dem Raum folgen will. Wird nur gestellt, wenn der Teilnehmer sie beantworten kann (nicht in PiP); solange sie zurückgehalten wird, gilt der Teilnehmer als IDLE und hält den Raum nicht auf.
_Avoid_: Content-Prompt (Code-Begriff), Room-Move-Dialog

**Away**:
Zustand eines Teilnehmers, dessen App im Hintergrund ist, ohne dass die Wiedergabe weiterläuft (kein PiP, kein Background-Audio). Lifecycle-bedingte Pausen eines Away-Teilnehmers pausieren den Raum nicht; der Raum läuft ohne ihn weiter. Bei Rückkehr gleicht er sich automatisch an den Raum an.
_Avoid_: Background-Pause, Suspend (OS-Begriff, kein Domänenbegriff)

**Banner**:
Globale, screen-unabhängige Anzeige einer aktiven Party mit Rücksprung in die Wiedergabe.
