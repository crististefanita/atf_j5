# Feedback · RAG pentru release

## Conservarea pachetului izolat și predarea rezervării

**P2 · Echipa de integrare Android · calificare nativă rămasă.** Sursa orchestrează conservarea, testul și restaurarea sub aceeași rezervare. Mock-urile și controalele RAG verifică refuzul proprietarului străin, copia coruptă, pachetul necunoscut și comparația octeților, independent de UID. [Mecanism stabil](../rentz/ATF_Release.md#conservarea-sesiunii-android)

- La următoarea acceptanță autorizată pe dispozitiv, porniți cu un pachet izolat preexistent, date cunoscute și afișaj salvat. Confirmați restaurarea octeților și a afișajului, apoi eliberarea numai a rezervării proprii.
- Păstrați receipt-ul nativ și Extent. Probele offline nu califică instalarea reală; nu declarați închiderea acestui ultim criteriu din mock-uri.

## Retragerea edițiilor pe canalul public

**P2 · Echipa de distribuție Rentz · calificare operațională rămasă.** Eligibilitatea publică, admiterea celor două Extent originale și retragerea coordonată sunt implementate cu refuzuri de componentă. Comanda RAG păstrează tagurile, arhivează metadata înainte de DELETE și scrie receipt separat. [Mecanism stabil](../rentz/ATF_Release.md#retragerea-edițiilor-publice)

- La o retragere reală autorizată, folosiți dovezile originale și verificarea README eligibilă, apoi păstrați `release-retirement.json` și Extent. Confirmați ediția curentă, tagurile și auditul după operație.
- Nu confundați un inventar fără ediții vechi sau un transport mock cu dovada unei ștergeri reale. Nicio ștergere GitHub nu este necesară pentru validarea offline a codului.

## Calificarea permisiunilor pe canalul GitHub

**P3 · Echipa de distribuție Rentz.** Lansatorul RAG verifică distinct identitatea, drepturile și operațiile Git pe canalul privat de test. Închiderea cere receipt-ul live `PASS` și raportul Extent asociat, cu cele trei identități și referința eliberată la final. [Fluxul de calificare](../rentz/ATF_Release.md#calificarea-permisiunilor-pe-github)

- Pregătiți canalul privat de test și accesul pentru două conturi cu scriere și un cont cu citire. Verificați autenticarea prin ID-uri GitHub distincte.
- Arhivați dovada cu refuzul scrierii pentru cititor, refuzul SHA-ului străin și promovarea coordonată. [Probe de componentă](../../atf-tests/src/test/java/atf/rentz/distribution/publication/GitHubPromotionQualificationTest.java)
