# Feedback și responsabilități

## Pe scurt

[Registrul Rentz](ATF_Rentz.md) păstrează lacunele active ale regulilor, modelului și integrării. O regulă stabilă are document responsabil în [aria Rentz](../rentz/README.md); rezultatele concrete rămân în depozitul extern.

- [Paritatea interfeței cu Whist Lab](ATF_Rentz_UI_ToDo.md) deține meniul, profilul, masa, cărțile și scorul Rentz, cu priorități și acceptanță prin componente și E2E selectiv. [Producătorul UI](../../../atf_j4/docs/feedback/ATF_CardUi_Extraction.md) deține extragerea prin refactorizare; adaptarea nu importă motorul sau regulile Whist.
- [Integritate și calitate](ATF_RentzQuality.md) deține criteriile strategice și documentare încă deschise. Validarea, lifecycle-ul, guvernanța sursei și [calificarea consumatorului](../rentz/ATF_Qualification.md) au contracte și probe proprii; calificarea nativă rămâne separată.

## Închidere și transfer

- Închideți un criteriu numai cu sursă și verificare identificate.
- Promovați mecanismul stabil în documentul responsabil înaintea eliminării explicației din feedback.
- Păstrați separat regulile jocului, datele unei rulări și defectele bibliotecii comune.

## Biblioteci comune

`atf_j0` deține codul, testele native și împachetarea bibliotecilor comune. [Feedback-ul responsabilului](../../../atf_j0/docs/feedback/README.md) primește cerințele generice, cu JAR/hash, reproducere, așteptat/observat și criteriu de acceptanță; consumatorul păstrează verificarea sa locală.

## Materiale istorice

[Arhiva Contract Whist](../history/contract-whist/README.md) păstrează separat observațiile și feedback-urile anterioare, cu inventar și retenție verificabile. Aceste instantanee nu intră în calificarea Rentz.

## Contract generic pentru guvernanță

**P2 · integrare împachetată solicitată responsabilului bibliotecilor comune.** Consumatorul adaptează numai validatorii de guvernanță, cu identitatea sursei și a adaptării în manifest; nu copiază implementările Core, Reporting sau Kit.

API-ul comun trebuie să primească rădăcina explicită, inventarul activ și politica de retenție, să expună diagnostice pe fișier și regulă și să permită controale negative fără alt reactor. Acceptanța cere același inventar, aceleași refuzuri și vecinul valid înaintea înlocuirii adaptorului local; versiunea și proveniența JAR/POM se verifică separat.

## Claritatea exemplelor de adopție

**P3 · responsabil: ghidurile bibliotecilor comune.** Exemplul pentru un consumator nou din ghidul lib trebuie să declare repository-ul Maven și zona de lucru selectate, inclusiv rolul izolării. O instrucțiune generică despre TMP nu trebuie interpretată ca autorizare a cache-ului implicit sau a unei copii neînregistrate.

Criteriul este un fragment autonom, cu precondiții și repository explicit, verificat prin consumatorul său. Păstrați distincte portabilitatea exemplului, blocarea cache-ului în ATF și acceptanța binarului; nu introduceți un helper local ascuns ca precondiție a ghidului comun.

## Inventar executabil și runtime comun

La împachetarea guvernanței, API-ul comun trebuie să păstreze inventarul AST pentru clasificare și selecție, cu rădăcină, source-set și apartenență la build declarate. Refuzul formelor neconforme și al octeților modificați ai bootstrap-ului are probe independente la consumator.

Calificarea runtime-ului cere lista ordonată a classpath-ului, inventarul recursiv de clase și resurse, atestarea la granițele testelor și verificarea aceleiași așteptări înainte/după lansare. Aria rămâne infrastructură de test; domeniul Rentz, observația jucătorului și semantica scorului nu se extrag în biblioteca comună.
