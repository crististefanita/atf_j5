# Feedback · RAG pentru release

## Calificarea permisiunilor pe canalul GitHub

**P3 · Echipa de distribuție Rentz.** Lansatorul RAG verifică distinct identitatea, drepturile și operațiile Git pe canalul privat de test. Închiderea cere receipt-ul live `PASS` și raportul Extent asociat, cu cele trei identități și referința eliberată la final. [Fluxul de calificare](../rentz/ATF_Release.md#calificarea-permisiunilor-pe-github)

- Pregătiți canalul privat de test și accesul pentru două conturi cu scriere și un cont cu citire. Verificați autenticarea prin ID-uri GitHub distincte.
- Arhivați dovada cu refuzul scrierii pentru cititor, refuzul SHA-ului străin și promovarea coordonată. [Probe de componentă](../../atf-tests/src/test/java/atf/rentz/distribution/publication/GitHubPromotionQualificationTest.java)

## Vizibilitatea draftului după creare

**P2 · Echipa de distribuție Rentz.** GitHub poate confirma crearea draftului înainte ca inventarul să includă ID-ul primit. RAG păstrează jurnalul, iar o reluare verifică draftul înainte de încărcare.

- Verificați printr-un mock secvența POST reușit, inventar temporar fără draft, apoi ID vizibil. Cel mult o creare și niciun upload sau PATCH cât timp ID-ul, tagul și sursa nu sunt confirmate.
- Păstrați refuzul imediat pentru duplicate, ID diferit sau SHA străin. Dacă inventarul nu converge în limita declarată, păstrați jurnalul și raportul Extent pentru reluarea explicită. [Contractul publicării și reluării](../rentz/ATF_Release.md)
