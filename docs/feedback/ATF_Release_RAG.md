# Feedback · RAG pentru release

## Promovări concurente între workspaces

**P2 · Echipa de distribuție Rentz.** Jurnalul blochează o a doua scriere locală pentru același fișier. Două workspaces pot avea jurnale distincte, iar GitHub poate accepta o altă publicare între ultimele cereri `GET` și `PATCH`. [Regula de promovare](../rentz/ATF_Release.md#reluarea-publicării-restricționate)

- Verificați o coordonare comună per canal de distribuție, cu proprietarul operației și o regulă explicită de recuperare după întrerupere.
- Proba de componentă pornește doi operatori cu jurnale distincte și blochează primul flux după citirea finală. Criteriul cere o singură promovare la un moment dat și reluarea verificării versiunii la preluarea operației. [Jurnal și blocare locală](../../atf-tests/src/main/java/atf/rentz/distribution/publication/PublicationCommand.java)
