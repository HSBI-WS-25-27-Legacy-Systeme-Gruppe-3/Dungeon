---
title: "Contributing"
---

Zur besseren Zusammenarbeit im Team gelten folgende Konventionen für Issues und Pull Requests. Bitte beachte diese bei der täglichen Arbeit mit dem Repository.

## Issues

* **Sprache**: Titel können deutsch oder englisch sein; Beschreibungen sind auf deutsch.
* **Beschreibungen**: sollen präzise und ausreichend ausführlich sein.
* **Referenzen/Anhänge**: ggf. relevante Stellen im Code deutlich machen (zB. mit `https://github.com/HSBI-WS-25-27-Legacy-Systeme-Gruppe-3/Dungeon/blob/7c8bf700f10c3551ce7e0be22773e830602facc6/game/src/engine/configuration/Configuration.java#L35-L48`)
* **Labels**: passend vergeben, falls nötig (zB. `Architektur`).

### Worfklow

Nehmt euch bitte Tickets, welche in der Spalte `Todo` hängen. Um ein Ticket zu "schnappen" , setzt ihr euch auf `assignee` und ändert den Status auf `In Progress`. Sobald PR das Ticket erstellt wurde, kann dieser hier (rechts unten unter `Development`) verlinkt werden. Sobald ihr fertig seid setzt ihr Status auf `In Review`, sodass andere den PR (nach Review) freigeben - nach dem Merge wechselt Status dann automatisch auf `Done`.

## Pull Requests

**HINWEIS**: Achtet darauf, Branches und PRs (Target-Branches) auf dem Fork zu erstellen, nicht auf dem originalen Dungeon Repo.

### Branches
* Ausgehend von `master` abzweigen (*keine* verschachtelten Branches). 
* **Benennung**: `<ticketnr>-<description>` (zB. `3-test-coverage`)

### Assignee & Titel
* **Sprache**: Titel und Beschreibung auf englisch (sofern es keine Sprachprobleme gibt).
* **PR-Titel**: `<ticketnr>: short summary`, beschreibt PR prägnant im Imperativ (**KORREKT**: `12: add test`, **FALSCH**: `12: tests added`)
* **PR-Beschreibung**: entsprechend der Konvention für Commit Messages
* **Assignee:** PR Ersteller setzt sich selbst als `Assignee`.
* **Labels und Projects** müssen **NICHT** manuell gesetzt werden (nur PR mit Ticket verlinken)

### PRs mergen
* standardmäßig immer **PR Merge* verwenden (nicht *PR Rebase*)
* bei kleinen PRs kann ruhig *PR Squash* verwendet werden (zB. PRs mit nur 1 Commit, bzw. insgesamt relativ geringer funktionaler Umfang)

### Drafts & Reviews

* Solange ein PR **nicht fertig** ist (nicht bereit für Review), diesen als **Draft** markiert werden (dann umstellen sobald bereit)
* sobald PR dann bereit alle (Änderungen abgeschlossen, Tests erfolgreich), umstelen auf **Ready for Review**
* Auch **nach einem Review** kann ein PR wieder als Draft gesetzt werden, falls weitere Änderungen nötig sind.
* ggf. gezielt spezifische Personen als **Reviewer anfragen**

## Commits

* **Sprache**: Titel und Beschreibung auf englisch (sofern es keine Sprachprobleme gibt).
* **Titel**: `<ticketnr>: short summary`, beschreibt Commit prägnant im Imperativ (**KORREKT**: `12: add test`, **FALSCH**: `12: tests added`)
* **Beschreibung**: Auflistung von Einzelheiten, falls nötig ggf. noch 1-2 Sätze als Block davor (nur falls allgemeine Erklärung notwendig)
* **Merge-Commit**: Bei Merge-Commit eines PRs ist `(PR #<nr>)` an den Commit-Titel anzuhängen (zB. `12: short summary (PR #2)`)

```
12: short summary

Nur falls tatsächlich notwendig, ein paar
kurze Worte ergänzen (max. 1-2 kurze Sätze).

- einzelheit
- einzelheit
```

## Sonstiges
* Die [Lizenzregeln](https://github.com/Dungeon-CampusMinden/Dungeon/blob/master/doc/license_rules.md) für Assets sind einzuhalten.

### IntelliJ: Star-Imports vermeiden

Checkstyle verbietet Star-Imports wie `import java.util.*;`. IntelliJ kann Imports automatisch
so organisieren, dass einzelne Klassen importiert werden.

Empfohlene Einstellungen:

1. Öffne `Settings > Editor > Code Style > Java > Imports`.
2. Setze `Class count to use import with '*'` auf einen hohen Wert, z. B. `999`.
3. Setze `Names count to use static import with '*'` ebenfalls auf einen hohen Wert, z. B. `999`.
4. Aktiviere `Use single class import`, falls die Option nicht aktiv ist.
5. Führe danach `Code > Optimize Imports` aus, um bestehende Imports in einer Datei aufzuräumen.
