# Feedback și responsabilități

## Pe scurt

[Registrul Rentz](ATF_Rentz.md) păstrează lacunele active ale regulilor, modelului și integrării. O regulă stabilă are document responsabil în [aria Rentz](../rentz/README.md); rezultatele concrete rămân în depozitul extern.

- [Interfața Rentz](ATF_Rentz_UI_ToDo.md) urmărește diferențele rămase de interacțiune, geometrie și scor, cu probe de componentă și acceptanță nativă selectivă. [Biblioteca grafică](../../rentz-lab-app/ui-library/README.md) și adaptoarele Rentz au verificări separate.
- [Integritate și calitate](ATF_RentzQuality.md) deține criteriile strategice și documentare încă deschise. Validarea, lifecycle-ul, guvernanța sursei și [calificarea consumatorului](../rentz/ATF_Qualification.md) au contracte și probe proprii; calificarea nativă rămâne separată.
- [Criteriile RAG rămase](ATF_Release_RAG.md) urmăresc probele de acceptanță suplimentare. [Ghidul de release](../rentz/ATF_Release.md#ordinea-calificării-android-curente) descrie porțile curente.
- [Extinderea realizărilor](ATF_Rentz_Achievements_ToDo.md) urmărește obiectivele candidate, sincronizarea și probele de accesibilitate rămase. [Integrarea actuală](../rentz/ATF_UiIntegration.md#realizări-și-profil-local)

## Închidere și transfer

- Închideți un criteriu numai cu sursă și verificare identificate.
- Promovați mecanismul stabil în documentul responsabil înaintea eliminării explicației din feedback.
- Păstrați separat regulile jocului, datele unei rulări și defectele bibliotecii comune.

## Biblioteci comune

Producătorul ATF deține codul, testele native și împachetarea bibliotecilor comune. O cerință către producător include versiunea JAR, SHA-256, reproducerea, așteptarea, observația și criteriul de acceptanță. [Contractele binare și probele locale](../../lib/README.md)

## Materiale istorice

[Arhiva Contract Whist](../history/contract-whist/README.md) păstrează separat observațiile și feedback-urile anterioare, cu inventar și retenție verificabile. Aceste instantanee nu intră în calificarea Rentz.

## Contract generic pentru guvernanță

**P2 · integrare împachetată solicitată responsabilului bibliotecilor comune.** Consumatorul adaptează numai validatorii de guvernanță, cu identitatea sursei și a adaptării în manifest; nu copiază implementările Core, Reporting sau Kit.

API-ul comun trebuie să primească rădăcina explicită, inventarul activ și politica de retenție, să expună diagnostice pe fișier și regulă și să permită controale negative fără alt reactor. Acceptanța cere același inventar, aceleași refuzuri și vecinul valid înaintea înlocuirii adaptorului local; versiunea și proveniența JAR/POM se verifică separat.

## Claritatea exemplelor de adopție

**P3 · probă de portabilitate.** [Ghidul J5](../../lib/README.md#integrare) indică acum zona de lucru și repository-ul Maven explicit. Rămâne verificarea fragmentului într-un consumator autonom, cu cache dedicat, apoi importul IntelliJ din același cache. Acceptanța binarelor J5 are propria poartă.

## Inventar executabil și runtime comun

La împachetarea guvernanței, API-ul comun trebuie să păstreze inventarul AST pentru clasificare și selecție, cu rădăcină, source-set și apartenență la build declarate. Refuzul formelor neconforme și al octeților modificați ai bootstrap-ului are probe independente la consumator.

Calificarea runtime-ului cere lista ordonată a classpath-ului, inventarul recursiv de clase și resurse, atestarea la granițele testelor și verificarea aceleiași așteptări înainte/după lansare. Aria rămâne infrastructură de test; domeniul Rentz, observația jucătorului și semantica scorului nu se extrag în biblioteca comună.
