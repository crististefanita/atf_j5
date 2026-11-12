# Rapoarte de calificare Rentz Lab 0.21.0

## Pe scurt

Sursele sunt fixate la commitul `086fd996906db8625cd1f9d1ecad5ea1962d7936`; APK-ul de produs este `RentzLab-0.21.0.apk`, SHA-256 `3c1356e0b6ce0ae7b11805649f4b713f94cfa5ef41a3dd3c089168ef9bf0ff40`. Rapoartele voluminoase sunt păstrate în depozitul extern `C:\work\rentz_back`, separat de istoricul surselor.

## Calificarea candidatului

- Regresie Maven și guvernanță: 69 suite, 569 de teste, fără eșecuri, erori sau skip-uri. Extent: `distribution/j5-score-alignment-0190-20261112/isolated/components/rag-p1-candidate-0210-final/component-suite-extent.html`.
- Android JVM: 24 suite, 119 teste, fără eșecuri, erori sau skip-uri. Receipt: `distribution/j5-score-alignment-0190-20261112/isolated/components/rag-p1-candidate-0210-final/android-jvm/result.json`.
- Interfață nativă: cinci jocuri complete consecutive, incluzând Rentz, pe 720 × 1280, densitate 360 și font 150%. Extent cu capturi PNG inline Base64: `distribution/j5-score-alignment-0190-20261112/isolated/android-acceptance/j5-0.21.0-native-5-games-20261112/android-extent.html`.
- RAG local, legat de sursa calificată: `distribution/j5-score-alignment-0190-20261112/isolated/distribution/j5-rag-failed-maven/candidate-0210-rag-final/report/release-rag-report.json`.
- Reproducerea controlată a Maven FAIL cu `game-core` primul: `distribution/j5-score-alignment-0190-20261112/isolated/distribution/j5-rag-failed-maven/probe-final-verification.json` și Extent `actual-failed-rag-final/report/release-rag-failure-extent.html`.

## Publicare și descărcare

După verificarea README-ului remote, raportul RAG al publicării și receipt-ul post-push sunt păstrate la `distribution/j5-score-alignment-0190-20261112/isolated/distribution/j5-rag-failed-maven/candidate-0210-rag-published/report/release-rag-report.json` și în subfolderul `publication/readme-publication.json`. Dovada autentificată a asset-urilor și refuzul accesului anonim sunt în `distribution/j5-score-alignment-0190-20261112/isolated/distribution/j5-rag-failed-maven/github-publish-0210/restricted-download.json`.

## Limite

Jocurile native au fost rulate cu APK-ul UI-sanity, semnat separat, nu cu APK-ul de produs. Manifestul ediției păstrează distinct identitățile și SHA-256-urile ambelor pachete.
