# Biblioteca UI Android admisă

## Pe scurt

Repository-ul Maven local conține `ro.cardtable:card-table-ui:0.1.21` și `ro.cardtable:card-table-names:0.1.0`, produse în J4. Rendererul Android și politica de nume Java sunt dependențe de produs independente de bibliotecile ATF din `lib` și de motoarele jocurilor.

- AAR, JAR, POM, metadata și surse au SHA-256 în [proveniență](provenance.json).
- Gradle rezolvă exclusiv grupul `ro.cardtable` din acest repository; build-ul nu folosește checkout-ul J4 sau o cale externă de calculator.
- `CardUiAdoptionTest` confruntă fișierele și clasele încărcate cu AAR-ul și JAR-ul calificate.
- Actualizarea cere o nouă admitere a producătorului și rerularea componentelor Rentz; nu se înlocuiesc discret octeții unei versiuni.

## Responsabilități

| Comun | Specific Rentz |
|---|---|
| Meniu, profil prin porturi și temă | Texte, limbă, preferințe și rute. |
| `GameTitleBar` și controlul opțional AutoPlay | `RentzGameHeader` proiectează progresul și contractul; Rentz păstrează starea și anularea AI. Oprirea rămâne accesibilă cât automatizarea este activă. |
| Lista, detaliile și panoul de progres la Realizări | Catalogul YAML, filtrele, eligibilitatea și stocarea progresului. |
| Chenare, geometrie, mână și cărți publice | Reveal, actor, legalitate, pas și secvențe. |
| Paleta marcajului și banda centrală dintre locuri | Alegerea cărților jucabile și a conținutului din bandă. |
| Selecție deterministă a numelor și colectare animată | Numele publice pe locuri, câștigătorul levatei și momentul publicării. |
| Temporizare comună după primul cadru vizibil | Adaptorul Rentz păstrează API-ul existent, delegând intervalul și verificările de scenă la J4. |
| Date vizibile și intenții | Punctaj, jurnal, lifecycle și aplicarea comenzii. |

[Integrarea și verificarea](../../docs/rentz/ATF_UiIntegration.md) · [Componentele aplicației](../app/README.md)

## Regresie și livrare către Rentz

Biblioteca Android și shell-ul Web au pachete și randări specifice platformei. Codul portabil stabilește ancorele, fețele cărților, intervalele și intențiile; adaptorul jocului stabilește datele, legalitatea și scorul. Cele patru șiruri Rentz se încadrează în banda liberă dintre ancorele comune.

1. Calificați Whist din același snapshot: componentele bibliotecii, afișajul, comenzile AutoPlay, licitarea, istoricul și realizările. Confirmați paritatea dintre JUnit și Extent; această probă offline nu este verdict de instalare sau randare Android.
2. Exportați sau reutilizați AAR-ul numai după verificarea inventarului din `card-ui-build.json`. Pentru Browser, păstrați runtime-ul, JAR-ul de teste, checksum-urile și `browser-build.json` împreună; verificați commitul și inventarul de intrări.
3. Comparați SHA-256 la producător, în pachetul de livrare și în acest repository. Intrări identice reutilizează aceeași versiune; intrări schimbate cer versiune nouă. Nu reconstruiți biblioteca doar pentru o modificare a adaptorului.
4. Rulați componentele Rentz pentru adopția binară, ancore la 3–6 locuri, antet și anularea AutoPlay. Testele Web folosesc contractul de viewport din JAR-ul de teste admis, plus secvențele și meniurile Rentz. [Calificarea Web](../../atf-ui-browser/README.md#compilare-și-verificări-locale)
5. Păstrați separat acceptanța pe dispozitiv pentru glyph-uri native, inset-uri, atingere și focus. Închideți criteriul din [feedback](../../docs/feedback/ATF_Rentz_UI_ToDo.md) numai cu identitatea versiunii verificate; testele JVM nu înlocuiesc această observație.

[Proveniență Android](provenance.json) · [Proveniență Browser](../../atf-ui-browser/lib/browser-build.json) · [Regresii de geometrie](../app/src/test/java/ro/rentzlab/app/ui/table/RentzCardSpaceTest.java) · [Regresii AutoPlay](../app/src/test/java/ro/rentzlab/app/lifecycle/AutoPlayComponentTest.java)
