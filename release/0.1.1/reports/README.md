# Calificarea Rentz Lab 0.1.1

## Pe scurt

Dosarul califică snapshot-ul APK-ului, separat de distribuția ce urmează. Starea curentă este PREPARED; nu declară încă o descărcare publică reușită.

## Populație și domeniu

| Selecție | Invocări | Dovadă |
|---|---:|---|
| Motor JVM, Maven | 87 | [XML original consolidat](test-results/maven-core.xml) |
| Integrare, biblioteci și guvernanță Maven | 150 | [XML original consolidat](test-results/maven-atf.xml) |
| Componente Android JVM | 33 | [XML original consolidat](test-results/android-jvm.xml) |
| Reverificarea din build | 120, suprapuse | [XML original consolidat](test-results/build-recheck.xml) |

- Baza este **270** invocări primare unice, fără FAIL, erori sau skip-uri. Cele 120 din build și cele 26 de controale documentare repetate nu cresc totalul.
- Gradle repetă unele display names parametrizate în aceeași clasă: păstrăm invocările și ambiguitatea explicită, fără a le transforma în metode unice deduse. [Receipt-ul calificării](qualification.json) separă grupurile și această limită.
- Execuția inițială rămâne FAIL pentru trei README-uri modificate în paralel. [Reconcilierea](source-reconciliation.json) confirmă codul și populația neschimbate, 26 de controale live trecute și refuzul mutanților de cod, adăugare sau ștergere; nu rescrie rularea inițială.

## Comparații auditate

| Scop | Raport |
|---|---|
| Reguli Rentz: 13 componente narative | [Extent](component/rentz-rules.html) |
| Transport și payload: 19 componente negative/pozitive | [Extent](distribution/download-components.html) |
| Stări, candidat și navigare: 13 componente | [Extent](distribution/release-governance.html) |
| Octeți Git și staging fără .git: 2 componente | [Extent](distribution/release-git-policy.html) |
| APK real: 13 comparații pentru fișier și ieșirile SDK | [Extent](distribution/apk-validation.html) |
| Încercări anterioare și sesiune incompletă | [Diagnostic păstrat](diagnostics/preparation-attempts.html) |

- HTML-urile păstrează expected/actual și diagnosticele evaluate. XML-urile califică independent populația; nu reconstruim aserțiuni inexistente pentru celelalte unit-uri.
- [Intrările APK](apk-inputs.json) identifică sursa construirii; semnătura, pachetul și alinierea sunt confruntate cu binarul efectiv. Rapoartele și build-urile intermediare rămân în depozitul extern.
- Încă nu s-a executat UI-ul jocului pentru această livrare. Acceptanța API, transferul Android și navigarea browserului vor avea dovezi și limite distincte.

[Procesul livrării](../../../docs/rentz/ATF_Release.md) · [Strategia de calificare](../../../docs/rentz/ATF_Qualification.md) · [Catalog](../../README.md)
