# Feedback · RAG pentru release

## Calificarea permisiunilor pe canalul GitHub

**P3 · Echipa de distribuție Rentz.** Lansatorul RAG verifică distinct identitatea, drepturile și operațiile Git pe canalul privat de test. Închiderea cere receipt-ul live `PASS` și raportul Extent asociat, cu cele trei identități și referința eliberată la final. [Fluxul de calificare](../rentz/ATF_Release.md#calificarea-permisiunilor-pe-github)

- Pregătiți canalul privat de test și accesul pentru două conturi cu scriere și un cont cu citire. Verificați autenticarea prin ID-uri GitHub distincte.
- Arhivați dovada cu refuzul scrierii pentru cititor, refuzul SHA-ului străin și promovarea coordonată. [Probe de componentă](../../atf-tests/src/test/java/atf/rentz/distribution/publication/GitHubPromotionQualificationTest.java)
