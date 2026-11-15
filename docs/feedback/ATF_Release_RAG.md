# Feedback · RAG pentru release

## Calificarea permisiunilor pe canalul GitHub

**P3 · Echipa de distribuție Rentz.** Probele locale verifică actualizarea condiționată a referinței Git folosind două checkout-uri. Politica tagurilor și permisiunile din canalul GitHub se aplică în mediul de distribuție. [Contractul rezervării](../rentz/ATF_Release.md#rezervarea-canalului-de-promovare)

- Verificați pe un canal privat de test că două identități autorizate pot obține și elibera pe rând referința de coordonare. Înregistrați separat refuzul pentru o identitate fără drept de scriere.
- Criteriul cere controlul asupra referinței de coordonare și respingerea ștergerii cu un SHA străin. Păstrați proba de permisiuni separată de calificarea APK-ului.
