# Rapoarte de calificare Rentz Lab 0.23.0

Rapoartele originale și receipts sunt păstrate în `C:\work\rentz_back`; manifestul le leagă prin cale, dimensiune și SHA-256 de sursa calificată `0c8fb052d8558208bba122ca6591e67bd28d2b68`. Commitul de distribuție este ulterior și nu schimbă APK-ul.

| Poartă | Rezultat | Dovadă externă |
|---|---|---|
| Regresie și componentă | 461 de teste Maven în 45 de suite și 123 teste Android JVM în 25 de suite; zero eșecuri, erori sau sărituri. | `components/20261113-j5-0.23-regression-final/component-suite-extent.html` |
| Interfață nativă | Trei jocuri complete consecutive — Rentz, Tricks și Whist — 34 checkpoint-uri, istoric și scor pentru patru locuri, reluare și verificare independentă. Capturile originale sunt inline Base64 în Extent. | `android-acceptance/20261113-j5-0.23-three-games-retry/android-extent.html` |
| APK de produs | `RentzLab-0.23.0.apk`, semnat, non-debuggable, 926590 bytes; SHA-256 și certificatul sunt în manifest. | `android-acceptance/20261113-j5-0.23-release-build/build-qualification.json` |
| RAG de candidat | Porțile locale, catalogul, raportul de componente, raportul nativ și buildul sunt legate de sursa calificată. Confirmarea README-ului publicat este receipt separat. | `validation/20261113-j5-0.23-rag-final/report/release-rag-report.json` |

Prima încercare nativă s-a oprit înainte de primul joc din cauza unui ANR temporar al System UI; nu este numărată ca joc. Reîncercarea pe același APK a trecut toate cele trei jocuri. Rularea nereușită este păstrată separat în `android-acceptance/20261113-j5-0.23-three-games/`.
