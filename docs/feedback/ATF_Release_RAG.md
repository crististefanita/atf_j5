# Feedback · RAG pentru release

## Ordinea edițiilor publicate

**P2 · Responsabil: distribuția Rentz.** Reluarea verifică dacă sursa se află pe `main`. Adaptorul cere apoi `make_latest=true` la publicarea draftului. Dacă un candidat mai vechi a rămas în draft, commitul lui poate fi încă pe `main` după publicarea unei ediții mai noi.

- Confruntați versiunea candidatului cu edițiile stabile remote înaintea actualizării. Simulați două drafturi cu versiuni diferite și reluarea celui vechi după publicarea celui nou.
- Proba negativă cere zero cereri PATCH pentru draftul vechi. Vecinii valizi acoperă candidatul mai nou și reluarea aceleiași ediții deja publicate. [Adaptorul GitHub](../../atf-tests/src/main/java/atf/rentz/distribution/publication/GitHubPublication.java) · [Contractul de release](../rentz/ATF_Release.md#reluarea-publicării-restricționate)
