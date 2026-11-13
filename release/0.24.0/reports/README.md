# Rapoarte Rentz Lab 0.24.0

Rădăcina dovezilor este C:/work/j5-native-audit/rentz_back. [Manifestul](../release-validation.json) leagă fișierele de sursa testată prin cale, dimensiune și SHA-256.

| Verificare | Rezultat | Dovadă |
|---|---|---|
| Regresie | 467 teste Maven și 124 teste Android JVM, toate trecute. | components/opening-024-regression/component-suite-extent.html |
| Build | APK de produs semnat cu certificatul distribuției. | distribution/0.24.0-offline-exception/exception-build.json |
| Distribuție | Descărcare autentificată verificată integral. Accesul anonim a primit HTTP 404. | distribution/0.24.0-offline-exception/restricted-publication.json |
| Acceptanță nativă și calificare RAG | Acceptanța nativă rămâne de rulat. Publicarea acestei ediții folosește excepția punctuală autorizată. | [Procesul standard](../../../docs/rentz/ATF_Release.md) |
