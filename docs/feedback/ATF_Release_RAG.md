# Feedback · RAG pentru release

## Conservarea pachetului izolat și predarea rezervării

**P2 · Echipa de integrare Android.** Conservarea unui pachet de test preexistent și acceptanța folosesc rezervări distincte. Un alt operator poate ocupa emulatorul între ele. Refuzul comenzilor străine funcționează, dar reluarea și restaurarea au nevoie de orchestrare comună în surse.

- Integrați backup-ul verificat, absența inițială și restaurarea în aceeași rezervare sau într-o predare atomică explicită. Pachetul principal nu intră în aria de ștergere.
- Acoperiți cu mock-uri concurența dintre conservare, lansare și restaurare. Un proprietar străin nu primește comenzi, backup-ul rămâne intact, iar comparația fișierelor restaurate verifică octeții, separat de UID și metadatele arhivei.
- Închiderea cere control negativ, vecin valid și o acceptanță nativă cu restaurarea pachetului preexistent. [Transportul și rezervarea](../../rentz-reference/tools/RentzAndroid.psm1)

## Retragerea edițiilor pe canalul public

**P2 · Echipa de distribuție Rentz.** Publicarea în repository-ul sursei verifică descărcările autentificate și anonime. Eligibilitatea retragerii din verificatorul README rămâne însă definită pentru canalul restricționat; publicarea publică nu autorizează implicit ștergerea edițiilor precedente.

- Extindeți eligibilitatea printr-un contract public explicit: ultima ediție, tagul exact, linkul unic din README remote, ambele descărcări și cele două rapoarte originale trebuie confirmate înaintea retragerii.
- Testați refuzul pentru raport lipsă, SHA diferit, README vechi, ediție mai nouă și cont fără scriere. Păstrați tagurile și dovezile de audit; nu recreați un repository separat pentru APK.
- Închiderea cere probe de componentă și receipt de retragere separat de publicare. [Verificatorul README](../../scripts/verify-release-readme.ps1)

## Calificarea permisiunilor pe canalul GitHub

**P3 · Echipa de distribuție Rentz.** Lansatorul RAG verifică distinct identitatea, drepturile și operațiile Git pe canalul privat de test. Închiderea cere receipt-ul live `PASS` și raportul Extent asociat, cu cele trei identități și referința eliberată la final. [Fluxul de calificare](../rentz/ATF_Release.md#calificarea-permisiunilor-pe-github)

- Pregătiți canalul privat de test și accesul pentru două conturi cu scriere și un cont cu citire. Verificați autenticarea prin ID-uri GitHub distincte.
- Arhivați dovada cu refuzul scrierii pentru cititor, refuzul SHA-ului străin și promovarea coordonată. [Probe de componentă](../../atf-tests/src/test/java/atf/rentz/distribution/publication/GitHubPromotionQualificationTest.java)
