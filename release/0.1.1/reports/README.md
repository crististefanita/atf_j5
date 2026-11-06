# Calificarea Rentz Lab 0.1.1

## Pe scurt

Dosarul califică snapshot-ul APK-ului și distribuția ediției PUBLISHED. Probele API și Android sunt distincte de navigarea din browser.

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
- Încă nu s-a executat UI-ul jocului pentru această livrare. Acceptanța API și transferul Android au dovezi separate; navigarea din browser rămâne nevalidată.

[Procesul livrării](../../../docs/rentz/ATF_Release.md) · [Strategia de calificare](../../../docs/rentz/ATF_Qualification.md) · [Catalog](../../README.md)

## Distribuția publicată

- [Descărcare API](distribution/download-live.html): șase comparații reale, HTTP 200, 771144 bytes, SHA-256 și toate CRC-urile confruntate. Checksum-ul publicat a fost descărcat și comparat separat.
- [Descărcare Android](distribution/mobile-download.html): 13 comparații pe API 28, transfer DownloadManager în 1624 ms, semnătură și metadata PackageManager; o probă izolată execută decoderul JSON din APK-ul descărcat. Nu execută interfața jocului și nu accesează datele sale.
- APK-ul complet este păstrat în Download pe emulator și în Downloads pe calculator. Helperul de probă a fost dezinstalat, iar hash-ul copiei Android a fost reverificat după cleanup.
- Browser MCP: pagina release-ului a afișat «Page not found» în sesiunea disponibilă. Această limită rămâne deschisă; PASS API/Android nu înseamnă PASS pentru downloadul din browser.
- Prima conexiune ADB a eșuat înaintea probei; diagnosticul rămâne FAIL, separat de transferul ulterior reușit. Rapoartele de pregătire păstrează cinci FAIL și o sesiune incompletă SKIP.
- Sursele APK: commit **705d73ab82dcbe9cea6707e072cf812a94476552**, cu 57 de intrări verificate inclusiv ca octeți Git. Modificările live ulterioare nu sunt atribuite acestui APK.
- 13 comparații APK, șase API și 13 Android sunt verificări de livrare; nu se adaugă la cele 270 de invocări JUnit primare.

## Verificarea prezentării

[Controlul de randare](rendering.json) identifică nouă HTML-uri prin SHA-256. Chromium offline a deschis scenariile, fazele și corpurile; ultimele rânduri de aserțiuni sunt vizibile, iar corpurile expandate sunt integrale, fără erori JavaScript. Cele patru rapoarte de componente neschimbate au fost păstrate din verificarea inițială; după corecția setării corpului au fost reverificate numai cele patru rapoarte de livrare/diagnostic.

Limita implicită de 100 de caractere a fost depistată și corectată în prezentarea dovezilor, fără replay. Limita aserțiunilor rămâne 240; dovada de livrare configurează explicit corpul integral.

- [Control permanent al corpului integral](distribution/release-body-retention.html): o componentă suplimentară trecută după publicare, cu vecini negativi pentru corp lipsă, gol și preview trunchiat. Nu schimbă populația inițială de 270; sursa testului aparține commit-ului ulterior al dovezilor.

Controlul suplimentar al corpului integral are propriul HTML, verificat și el prin click și vizibilitatea tuturor celor șapte rânduri de aserțiuni.
