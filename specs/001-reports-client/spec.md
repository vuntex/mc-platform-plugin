# Feature Specification: Reports — Plugin-Slice (Paper-1.21-Client)

**Feature Branch**: `001-reports-client`

**Created**: 2026-06-22

**Status**: Draft

**Input**: User description: "Migriere den Plugin-seitigen Slice von „Reports (#47)“ gemäß docs/REPORTS_PLUGIN_BRIEF.md. Das Backend (Source of Truth) ist fertig; hier entsteht NUR der Paper-1.21-Client."

## Overview

Ein Spieler meldet einen Mitspieler mit Kategorie + Grund über `/report`. Das Online-Team wird **live** benachrichtigt und arbeitet die Meldungen über ein Inbox-Menü ab (offen → in Bearbeitung → erledigt/abgelehnt). Optional hängt die Meldung einen Schnappschuss der letzten öffentlichen Chat-Nachrichten an. Das **Backend ist die Wahrheit**; der Client schreibt über REST, liest live über Pub/Sub und hält nur einen lokalen Lese-Cache der offenen Reports. Berechtigungen sind **backend-autoritativ**; UI-Gates sind nur Komfort.

## Clarifications

### Session 2026-06-22

- Q: Was zeigt die Team-Inbox angesichts `GET /api/reports/open?staff=<uuid>` an? → A: **Geteilte Warteschlange** — alle offenen Reports; `staff=<uuid>` ist nur die Identität des anfragenden Team-Mitglieds (Permission-/Audit-Kontext). Kein Zuweisungs-/Claim-Konzept in diesem Slice.
- Q: Wer erhält bei einem neuen Report den Live-Ping (Chat-Zeile + Ton), und wie wird „Online-Team“ clientseitig bestimmt? → A: **Alle online befindlichen Spieler mit dem UI-Permission-Node `mcplatform.report.view`** (kein Per-Spieler-Backend-Call); Backend bleibt für den echten Inbox-Zugriff autoritativ.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Spieler meldet einen Mitspieler (Priority: P1)

Ein Spieler erlebt Fehlverhalten und tippt `/report <spieler>`. Es öffnet sich ein Menü zur **Kategorie-Auswahl** (Cheating, Beleidigung, Spam/Werbung, Teaming/Bug-Abuse, Sonstiges). Nach Wahl einer Kategorie wird er aufgefordert, den **Grund im Chat** einzugeben (mit Abbruch-Wort). Beim Absenden wird die Meldung — inkl. eines Schnappschusses der letzten öffentlichen Chat-Nachrichten — an das Backend übermittelt. Der Reporter erhält eine **Eingangsbestätigung**.

**Why this priority**: Das ist der Kern des Features und für sich allein nutzbar — ohne Erstellung gibt es nichts zu bearbeiten. Liefert sofort Wert (Spieler können melden, Team bekommt Daten ins Backend).

**Independent Test**: Mit einem einzelnen Spieler `/report` gegen das laufende Backend ausführen, Kategorie wählen, Grund eingeben → im Backend liegt ein neuer offener Report mit korrektem Reporter/Target/Kategorie/Grund und (sofern vorhanden) Chat-Kontext; der Reporter sieht die Bestätigung.

**Acceptance Scenarios**:

1. **Given** ein Online-Spieler und ein gültiger Ziel-Name, **When** er `/report <spieler>` ausführt, **Then** öffnet sich das Kategorie-Auswahl-Menü mit den fünf Kategorien als deutschsprachige Labels.
2. **Given** das Kategorie-Menü ist offen, **When** der Spieler eine Kategorie wählt, **Then** schließt das Menü und er wird aufgefordert, den Grund im Chat einzugeben.
3. **Given** die Grund-Eingabe läuft, **When** der Spieler einen nicht-leeren Text sendet, **Then** wird die Meldung an das Backend gesendet und er erhält eine Eingangsbestätigung mit der gewählten Kategorie.
4. **Given** die Grund-Eingabe läuft, **When** der Spieler das Abbruch-Wort sendet, **Then** wird der Vorgang ohne Erstellung abgebrochen und er erhält eine Abbruch-Meldung.
5. **Given** im Server-Chat lagen zuletzt öffentliche Nachrichten, **When** die Meldung erstellt wird, **Then** wird der aktuelle Schnappschuss des öffentlichen Chat-Fensters als `chatContext` mitgeschickt.
6. **Given** der Spieler meldet sich selbst, **When** er die Meldung absendet, **Then** lehnt das Backend ab (422) und der Spieler erhält eine verständliche Fehlermeldung; es wird kein Report erstellt.
7. **Given** der Spieler hat erst kürzlich gemeldet, **When** er erneut zu schnell meldet, **Then** lehnt das Backend ab (429) und der Spieler erhält einen Wartezeit-Hinweis.

---

### User Story 2 - Team bearbeitet Meldungen im Inbox-Menü (Priority: P2)

Ein Team-Mitglied öffnet die Report-Inbox (Befehl bzw. Menü-Einstieg). Es sieht eine **paginierte Liste aller offenen Reports** (neueste zuerst) — eine **geteilte Warteschlange** für das gesamte Team (keine persönliche Zuweisung in diesem Slice). Es öffnet einen Report im Detail, sieht Reporter, Ziel, Kategorie, Grund, Status und den angehängten **Chat-Kontext**. Es setzt den Status (in Bearbeitung / erledigt / abgelehnt) gemäß den erlaubten Übergängen. Die Liste aktualisiert sich live, während das Menü offen ist; beim Schließen meldet sich die Live-Ansicht sauber ab.

**Why this priority**: Ohne Bearbeitung bleiben Meldungen liegen. Baut auf den von US1 erzeugten Daten auf, ist aber unabhängig gegen das Backend testbar (Backend kann Reports auch ohne Client-US1 enthalten).

**Independent Test**: Mit einem Team-Account die Inbox öffnen → offene Reports erscheinen; einen Report öffnen → Detail inkl. Chat-Kontext sichtbar; Status auf „in Bearbeitung“ setzen → Report verschwindet/aktualisiert sich; ein erneutes Setzen eines unzulässigen Übergangs wird sauber abgewiesen (409).

**Acceptance Scenarios**:

1. **Given** ein Team-Mitglied mit Inbox-Berechtigung, **When** es die Inbox öffnet, **Then** werden die offenen Reports neueste-zuerst und paginiert angezeigt (28 Einträge pro Seite, Seiten-Pfeile nur wenn weitere Seiten existieren).
2. **Given** die Inbox ist offen, **When** ein Listeneintrag angeklickt wird, **Then** öffnet sich das Detail mit Reporter, Ziel, Kategorie, Grund, Status, Zeitstempel und Chat-Kontext.
3. **Given** ein offener Report im Detail, **When** das Team „in Bearbeitung“ wählt, **Then** wird der Status per REST gesetzt und die Ansicht spiegelt den neuen Status (offen → in Bearbeitung).
4. **Given** ein Report „in Bearbeitung“, **When** das Team „erledigt“ oder „abgelehnt“ wählt, **Then** wird der Status gesetzt und der Report verlässt die Liste der offenen Reports.
5. **Given** das Backend lehnt einen Statuswechsel ab (ungültiger Übergang / konkurrierende Änderung, 409), **When** der Versuch erfolgt, **Then** erhält das Team eine verständliche Fehlermeldung und die Ansicht bleibt konsistent.
6. **Given** ein Spieler ohne Team-Berechtigung, **When** er die Inbox aufruft oder einen Status setzen will, **Then** verweigert das Backend (403) und der Client zeigt eine entsprechende Meldung — unabhängig davon, ob das UI-Gate den Einstieg ausgeblendet hat.
7. **Given** die Inbox ist als LIVE-Ansicht offen, **When** sie geschlossen wird, **Then** wird die Live-Beobachtung sauber abgemeldet (keine Beobachter-Leaks).

---

### User Story 3 - Team wird live über neue Meldungen benachrichtigt (Priority: P3)

Sobald irgendwo ein neuer Report erstellt wird, wird das gesamte **online befindliche Team** (alle Spieler mit dem UI-Permission-Node `mcplatform.report.view`) unmittelbar benachrichtigt — mit einer Chat-Zeile plus Hinweiston. Eine bereits geöffnete Inbox aktualisiert sich live.

**Why this priority**: Beschleunigt die Reaktionszeit, ist aber kein Blocker für Erstellen/Bearbeiten. Setzt voraus, dass Reports erstellt werden (US1) und idealerweise bearbeitet werden (US2), liefert aber eigenständigen Mehrwert.

**Independent Test**: Während ein Team-Mitglied online ist, einen neuen Report erstellen (oder ein `mc:report:changed`-Event mit changeType=CREATED auslösen) → das Team-Mitglied erhält eine Chat-Benachrichtigung + Ton; eine offene Inbox zeigt den neuen Report ohne erneutes Öffnen.

**Acceptance Scenarios**:

1. **Given** mindestens ein Team-Mitglied ist online, **When** ein neuer Report erstellt wird, **Then** erhält jedes online befindliche Team-Mitglied eine Chat-Benachrichtigung mit Kurzinfos (Kategorie, Ziel) und einen Hinweiston.
2. **Given** eine Inbox ist gerade geöffnet, **When** ein neuer Report eintrifft, **Then** erscheint er live in der Liste, ohne dass das Menü neu geöffnet werden muss.
3. **Given** ein `mc:report:changed`-Event mit changeType=STATUS_CHANGED, **When** es eintrifft, **Then** aktualisiert sich der lokale Lese-Cache und eine offene Inbox entsprechend (z. B. verschwindet ein nicht mehr offener Report).
4. **Given** kein Team-Mitglied ist online, **When** ein neuer Report erstellt wird, **Then** wird keine Spieler-Benachrichtigung versucht (keine Fehler), die Daten liegen im Backend bereit.

---

### Edge Cases

- **Ungültiger / unbekannter Ziel-Spieler**: `/report` mit nicht auflösbarem Namen → verständlicher Hinweis, keine Erstellung. In diesem Slice wird das Ziel **online** aufgelöst; ein offline Spieler gilt als „unbekannt“ und führt zum selben Hinweis (offline-Meldungen sind nicht im Scope).
- **Leerer / zu langer Grund**: Leereingabe bricht ab oder fordert erneut; zu langer Grund wird vom Backend abgelehnt (422) und sauber gemeldet.
- **Chat-Kontext leer**: Lagen keine öffentlichen Nachrichten vor, wird `chatContext` leer/`null` gesendet — die Erstellung gelingt trotzdem.
- **Chat-Kontext zu groß**: Backend lehnt mit 422 ab; Client meldet verständlich (sollte durch Begrenzung auf ~20 Nachrichten praktisch nicht auftreten).
- **Backend nicht erreichbar / 5xx**: Verständliche „später erneut versuchen“-Meldung; kein Absturz, Main-Thread bleibt frei.
- **Report nicht gefunden (404)** beim Statuswechsel (z. B. zwischenzeitlich entfernt): verständliche Meldung, Ansicht bleibt konsistent.
- **Doppeltes Absenden / konkurrierende Statusänderung (409)**: sauber abgefangen, keine inkonsistente Anzeige.
- **Spieler verlässt den Server während laufender Grund-Eingabe**: Vorgang verfällt ohne Erstellung; keine hängenden Eingabe-Prompts.
- **Privatnachrichten (PNs)**: gelangen **nicht** in den Chat-Kontext (außerhalb dieses Slices).

## Requirements *(mandatory)*

### Functional Requirements

**Erstellen (US1)**
- **FR-001**: Spieler MÜSSEN über `/report <spieler>` eine Meldung gegen einen anderen Spieler starten können (Erstellen ist für alle offen).
- **FR-002**: Das System MUSS ein Kategorie-Auswahl-Menü mit genau diesen Kategorien anzeigen: Cheating, Beleidigung, Spam/Werbung, Teaming/Bug-Abuse, Sonstiges (deutschsprachige Anzeige-Labels; kanonische Wire-Werte intern).
- **FR-003**: Nach Kategorie-Wahl MUSS das System den Reporter zur Grund-Eingabe per Chat auffordern, inkl. erkennbarem Abbruch-Wort.
- **FR-004**: Das System MUSS bei Absenden eine Meldung mit Reporter, Ziel, Kategorie, Grund und (sofern vorhanden) Chat-Kontext über den CREATE-Endpoint erstellen.
- **FR-005**: Das System MUSS dem Reporter nach erfolgreicher Erstellung eine Eingangsbestätigung anzeigen.
- **FR-006**: Das System MUSS Erstell-Fehler verständlich behandeln: Self-Report/ungültige Eingabe (422), Cooldown (429) mit Wartezeit-Hinweis, Backend-Fehler (5xx) mit Wiederhol-Hinweis.

**Chat-Ringpuffer (US1, unterstützend)**
- **FR-007**: Das System MUSS einen **globalen, RAM-basierten Ringpuffer der letzten ~20 öffentlichen Chat-Nachrichten** führen; jede Nachricht enthält Absender-UUID, Text und Zeitstempel.
- **FR-008**: Der Ringpuffer DARF ausschließlich **öffentliche** Chat-Nachrichten aufnehmen; Privatnachrichten und nicht-öffentliche Kanäle MÜSSEN ausgeschlossen sein.
- **FR-009**: Beim Erstellen einer Meldung MUSS der **aktuelle Schnappschuss des globalen öffentlichen Chat-Fensters** als `chatContext` mitgeschickt werden (darf leer/`null` sein).
- **FR-010**: Der Ringpuffer MUSS verlustfrei für den Main-Thread arbeiten (kein Blockieren); er hält keine persistente Historie und überlebt keinen Server-Neustart.

**Team-Inbox (US2)**
- **FR-011**: Team-Mitglieder MÜSSEN eine Inbox **aller offenen** Reports öffnen können (neueste zuerst, geteilte Warteschlange). Die Liste wird nicht nach Bearbeiter gefiltert; der `staff`-Parameter trägt lediglich die Identität des anfragenden Team-Mitglieds (Permission-/Audit-Kontext).
- **FR-012**: Die Inbox-Liste MUSS paginiert sein (28 Einträge pro Seite; Seiten-Pfeile nur, wenn weitere Seiten existieren).
- **FR-013**: Ein Detail je Report MUSS Reporter, Ziel, Kategorie, Grund, Status, Zeitstempel und den angehängten Chat-Kontext anzeigen.
- **FR-014**: Team-Mitglieder MÜSSEN den Status setzen können, beschränkt auf die erlaubten Übergänge: offen→in Bearbeitung, offen→abgelehnt, in Bearbeitung→erledigt, in Bearbeitung→abgelehnt.
- **FR-015**: Das System MUSS Inbox-/Status-Fehler verständlich behandeln: fehlende Berechtigung (403), nicht gefunden (404), ungültiger Übergang/Konflikt (409), ungültige Eingabe (422).
- **FR-016**: Die Inbox MUSS eine **LIVE**-Ansicht sein und sich beim Schließen sauber von der Live-Beobachtung abmelden (keine Beobachter-Leaks).
- **FR-017**: Das System DARF die Inbox per UI-Gate nur Team-Mitgliedern anzeigen (reiner Komfort); die **echte** Berechtigungsprüfung erfolgt backend-autoritativ.

**Live-Benachrichtigung (US3)**
- **FR-018**: Das System MUSS auf den Live-Kanal `mc:report:changed` lauschen und Events dekodieren.
- **FR-019**: Bei einem neuen Report (changeType=CREATED) MUSS jeder online befindliche Spieler mit dem UI-Permission-Node `mcplatform.report.view` eine **Chat-Benachrichtigung plus Hinweiston** erhalten (Kurzinfos: Kategorie, Ziel). Die Empfänger-Bestimmung erfolgt clientseitig über diesen Node (kein Per-Spieler-Backend-Call); der Backend-Zugriff auf die Inbox bleibt autoritativ.
- **FR-020**: Eine geöffnete Inbox MUSS sich bei eingehenden Events live aktualisieren (neuer Report erscheint; nicht mehr offener Report verschwindet).
- **FR-021**: Das System MUSS Live-Updates **versionsbewusst** verarbeiten (veraltete/Out-of-order-Events dürfen den lokalen Lese-Cache nicht zurücksetzen).

**Übergreifend / Architektur-Leitplanken**
- **FR-022**: Das Plugin DARF keine eigene Wahrheit über Reports halten — nur einen lokalen Lese-Cache der offenen Reports; alle Schreibvorgänge laufen über REST, Live-Lesen über Pub/Sub.
- **FR-023**: Alle Texte/Anzeigen MÜSSEN über Adventure-Components erfolgen (keine §-Farbcodes); kein NMS/Reflection; kein manuelles Inventory-Click-Handling (Menü-Framework verwenden).
- **FR-024**: Das Feature MUSS als eigenständiges Package mit **einem** Registrierungs-Eintrag angebunden werden; keine generische Klasse darf dafür geändert werden. Falls eine generische Änderung nötig würde, MUSS dies als Muster-Leck gemeldet werden (siehe Open Questions zu 403/429-Mapping).

### Key Entities *(include if feature involves data)*

- **Report**: Eine Meldung. Attribute: ID, Reporter (UUID), Ziel (UUID), Kategorie, Grund/Detail, Status, Erstellzeitpunkt, zuletzt-bearbeitet-von, Zeitpunkt der letzten Statusänderung, Chat-Kontext, Version. **Wahrheit liegt im Backend**; Client cached offene Reports lesend.
- **Kategorie**: Eine von fünf festen Klassen (Cheating, Beleidigung, Spam/Werbung, Teaming/Bug-Abuse, Sonstiges) — kanonische Wire-Werte, deutschsprachige Anzeige-Labels.
- **Status**: Einer von offen, in Bearbeitung, erledigt, abgelehnt — mit definierten erlaubten Übergängen.
- **ChatMessage**: Eine öffentliche Chat-Nachricht im Schnappschuss: Absender-UUID, Text, Zeitstempel.
- **Chat-Ringpuffer**: Flüchtiger, globaler RAM-Speicher der letzten ~20 öffentlichen Nachrichten (Quelle für den `chatContext`-Schnappschuss).
- **ReportChangedEvent**: Live-Ereignis (reportId, reporter, target, category, status, changeType CREATED|STATUS_CHANGED, Zeitstempel) — **ohne** Chat-Kontext.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Ein Spieler kann eine vollständige Meldung (Befehl → Kategorie → Grund → Bestätigung) in unter 30 Sekunden abschließen.
- **SC-002**: 100 % der erfolgreich abgesendeten Meldungen erscheinen im Backend als offener Report mit korrekter Kategorie, Reporter, Ziel und Grund.
- **SC-003**: Liegen vor der Meldung öffentliche Chat-Nachrichten vor, enthalten ≥ 95 % der erstellten Reports einen nicht-leeren, korrekt zugeordneten Chat-Kontext.
- **SC-004**: Ein neuer Report erreicht jedes online befindliche Team-Mitglied als sichtbare Benachrichtigung in unter 2 Sekunden nach Erstellung.
- **SC-005**: Eine geöffnete Inbox spiegelt Statusänderungen/neue Reports innerhalb von 2 Sekunden ohne erneutes Öffnen.
- **SC-006**: Alle definierten Fehlerfälle (403/404/409/422/429/5xx) führen zu einer verständlichen Nutzer-Meldung und niemals zu einem Absturz oder eingefrorenen Client.
- **SC-007**: Das Schließen einer LIVE-Inbox hinterlässt keine aktiven Beobachter (kein Leak), verifizierbar im Test.
- **SC-008**: Die Funktion ist ohne Änderung einer generischen Klasse angebunden (genau ein Registrierungs-Eintrag, neues Feature-Package), verifizierbar per Diff.

## Assumptions

- **Backend ist fertig & Source of Truth**: Der CREATE-/LIST_OPEN-/CHANGE_STATUS-Vertrag und der Kanal `mc:report:changed` stehen; der Client implementiert nur die Konsumentenseite.
- **Protocol-Artefakt**: `com.mcplatform:plugin-protocol:0.1.0-SNAPSHOT` enthält das Paket `com.mcplatform.protocol.report` (Endpoints, DTOs, Event, Codec, Channels). **Precondition**: `./gradlew build --refresh-dependencies`; das Paket war zum Spec-Zeitpunkt lokal noch nicht verifiziert auffindbar — vor der Planung bestätigen.
- **Wiederverwendung**: Bestehende Bausteine werden genutzt — REST-Client (`BackendClient`/`EndpointDescriptor`), version-aware `EventBus`, `FeatureRegistry`/`PluginFeature`, Menü-Framework (`MenuBuilder`/`MenuManager`/`MenuLiveBus`, paginierte Listen) und die Scheduler-Abstraktion (async I/O, Welt-Mutation zurück auf Main-Thread).
- **Chat-Kontext = globales Fenster**: Der Schnappschuss ist das globale öffentliche Chat-Fenster (jede Nachricht mit Absender-UUID), nicht nur Nachrichten des gemeldeten Spielers.
- **Team-Ping = Chat-Zeile + Ton** an alle online befindlichen Team-Mitglieder; Inbox bleibt die maßgebliche Arbeitsfläche.
- **Grund-Eingabe = Chat-Input-Prompt** (MENU_DESIGN-Fallback), da bislang kein Anvil/Sign-Input existiert; als neue feature-lokale Mechanik im `feature.report`-Package.
- **Berechtigungen backend-autoritativ**: Erstellen offen für alle; Inbox/Status nur Team (`report.view`/`report.handle`). UI-Gates (z. B. `mcplatform.report.view`/`mcplatform.report.handle` zum Ein-/Ausblenden) sind nur Komfort.
- **Ziel-Auflösung online-only**: `/report <spieler>` adressiert einen **online** befindlichen Spieler; offline Ziele sind in diesem Slice nicht unterstützt (siehe Edge Case). Begründung: keine Offline-UUID-Lookups nötig, einfachster Pfad für den 1:1-Migrationsumfang.
- **Out of Scope** (mit Backend abgestimmt): PNs im Chat-Kontext, Support/Tickets (#48), Lese-Sicht auf abgeschlossene Reports, Retention, Referenz Report→Punishment.

## Dependencies & Risks

- **Protocol-Verfügbarkeit (Blocker)**: `com.mcplatform.protocol.report` muss im Maven-Local-Artefakt vorhanden sein; sonst kompiliert der Client-Slice nicht. Vor Planung/Implementierung verifizieren.
- **403/429-Mapping (potenzielles Muster-Leck)**: Die bestehende Exception-Hierarchie (`BackendException`) bildet 400/404/409/422/5xx ab, **nicht** 403 und 429. Sauberes Behandeln dieser Codes könnte eine Änderung am generischen `HttpBackendClient` erfordern — was die Leitplanke „keine generische Klasse ändern“ berührt. Zur Planungszeit entscheiden: feature-lokale Inspektion von `statusCode()`/`responseBody()` (kein generischer Eingriff) **vs.** bewusste, abgestimmte Erweiterung der generischen Schicht.
- **Chat-Input-Prompt (neue Mechanik)**: Es existiert noch kein Texteingabe-UI; der Chat-Input-Prompt ist neu zu bauen (feature-lokal), inkl. sauberer Behandlung von Disconnect/Timeout/Abbruch.
- **Live-Abmeldung**: LIVE-Inbox muss sich beim Close zuverlässig abmelden (Framework-`MenuLiveBus`-Handle) — im Test absichern.
