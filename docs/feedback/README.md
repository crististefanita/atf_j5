# Feedback și responsabilități

## Pe scurt

[Registrul Rentz](ATF_Rentz.md) păstrează lacunele active ale regulilor, modelului și integrării. O regulă stabilă are document responsabil în [aria Rentz](../rentz/README.md); rezultatele concrete rămân în depozitul extern.

- [ToDo pentru tabla Rentz și scor](ATF_Rentz_UI_ToDo.md) deține cerințele de prezentare, prioritățile, dependențele și criteriile de acceptanță prin componente și E2E selectiv.
- [Integritate și calitate](ATF_RentzQuality.md) deține criteriile strategice și documentare încă deschise. Validarea, lifecycle-ul, guvernanța sursei și [calificarea consumatorului](../rentz/ATF_Qualification.md) au contracte și probe proprii; calificarea nativă rămâne separată.

## Închidere și transfer

- Închideți un criteriu numai cu sursă și verificare identificate.
- Promovați mecanismul stabil în documentul responsabil înaintea eliminării explicației din feedback.
- Păstrați separat regulile jocului, datele unei rulări și defectele bibliotecii comune.

## Biblioteci comune

`atf_j0` deține codul, testele native și împachetarea bibliotecilor comune. [Feedback-ul responsabilului](../../../atf_j0/docs/feedback/README.md) primește cerințele generice, cu JAR/hash, reproducere, așteptat/observat și criteriu de acceptanță; consumatorul păstrează verificarea sa locală.

## Materiale istorice

[Arhiva Contract Whist](../history/contract-whist/README.md) păstrează separat observațiile și feedback-urile anterioare, cu inventar și retenție verificabile. Aceste instantanee nu intră în calificarea Rentz.
