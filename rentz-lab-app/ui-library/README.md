# Biblioteca UI Android admisă

## Pe scurt

Repository-ul Maven local conține `ro.cardtable:card-table-ui:0.1.6` și `ro.cardtable:card-table-names:0.1.0`, produse în J4. Rendererul Android și politica de nume Java sunt dependențe de produs independente de bibliotecile ATF din `lib` și de motoarele jocurilor.

- AAR, JAR, POM, metadata și surse au SHA-256 în [proveniență](provenance.json).
- Gradle rezolvă exclusiv grupul `ro.cardtable` din acest repository; build-ul nu folosește checkout-ul J4 sau o cale externă de calculator.
- `CardUiAdoptionTest` confruntă fișierele și clasele încărcate cu AAR-ul și JAR-ul calificate.
- Actualizarea cere o nouă admitere a producătorului și rerularea componentelor Rentz; nu se înlocuiesc discret octeții unei versiuni.

## Responsabilități

| Comun | Specific Rentz |
|---|---|
| Meniu, profil prin porturi și temă | Texte, limbă, preferințe și rute. |
| Chenare, geometrie, mână și cărți publice | Reveal, actor, legalitate, pas și secvențe. |
| Selecție deterministă a numelor și colectare animată | Numele publice pe locuri, câștigătorul levatei și momentul publicării. |
| Date vizibile și intenții | Punctaj, jurnal, lifecycle și aplicarea comenzii. |

[Integrarea și verificarea](../../docs/rentz/ATF_UiIntegration.md) · [Componentele aplicației](../app/README.md)
