# Biblioteca UI Android admisă

## Pe scurt

Repository-ul Maven local conține `ro.cardtable:card-table-ui:0.1.1`, produs de J4 și admis în J5. Este dependență de produs, independentă de bibliotecile ATF din `lib` și de motoarele jocurilor.

- AAR, POM, metadata și surse au SHA-256 în [proveniență](provenance.json).
- Gradle rezolvă exclusiv grupul `ro.cardtable` din acest repository; build-ul nu folosește checkout-ul J4 sau o cale externă de calculator.
- `CardUiAdoptionTest` confruntă fișierele și octeții clasei încărcate cu AAR-ul calificat; motorul Whist lipsește din classpath.
- Actualizarea cere o nouă admitere a producătorului și rerularea componentelor Rentz; nu se înlocuiesc discret octeții unei versiuni.

## Responsabilități

| Comun | Specific Rentz |
|---|---|
| Meniu, profil prin porturi și temă | Texte, limbă, preferințe și rute. |
| Chenare, geometrie, mână și cărți publice | Reveal, actor, legalitate, pas și secvențe. |
| Date vizibile și intenții | Punctaj, jurnal, lifecycle și aplicarea comenzii. |

[Integrarea și verificarea](../../docs/rentz/ATF_UiIntegration.md) · [Componentele aplicației](../app/README.md)
