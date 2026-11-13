# Rapoarte de calificare Rentz Lab 0.22.0

Rapoartele originale rămân în `C:\work\rentz_back`, cu copii și digesturi verificate la publicare. Calificarea produsului este legată de sursa `a12e2388a6430ab5315daae31c27742b8b8b8008`; commitul care actualizează catalogul și README-ul este doar un commit ulterior de distribuție.

| Selecție | Rezultat și limită | Dovadă externă |
|---|---|---|
| Componente | 451 de cazuri în 45 de suite; zero eșecuri, erori sau teste sărite. | `components/20261113-j5-022-pinned-a12e238/component-suite-extent.html` |
| Android JVM | 121 de cazuri în 25 de suite; zero eșecuri, erori sau teste sărite. | `components/20261113-j5-022-pinned-a12e238/android-jvm/result.json` |
| Interfață nativă | Trei jocuri complete distincte — Rentz, Whist, Tricks — și 34 checkpoint-uri, inclusiv reluare, istoric și progres. Capturile PNG sunt inline Base64 în Extent. | `android-acceptance/20261113-j5-0.22-three-game-e2e-a12e238/android-extent.html` |
| APK de produs | Release semnat, versiune numerică și identitate Android validate. | `android-acceptance/20261113-j5-0.22-product-build-a12e238/build-qualification.json` |
| RAG | Porțile prebuild, candidate și native au trecut pentru sursa calificată. | `validation/20261113-j5-0.22-final-rag-a12e238/report/release-rag-report.json` |

Rularea nativă a folosit profilul logic 720 × 1280, densitate 360 și font 150%; configurația inițială a emulatorului a fost restaurată. APK-ul nativ de sanity și APK-ul de produs au identități separate; doar APK-ul de produs este publicat.
