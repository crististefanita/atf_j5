# Feedback și responsabilități

## Pe scurt

[Registrul Rentz](ATF_Rentz.md) păstrează lacunele active ale regulilor, modelului și integrării. O regulă stabilă are document responsabil în [aria Rentz](../rentz/README.md); rezultatele concrete rămân în depozitul extern.

- [ToDo pentru tabla Rentz și scor](ATF_Rentz_UI_ToDo.md) deține cerințele de prezentare, prioritățile, dependențele și criteriile de acceptanță prin componente și E2E selectiv.
- [Integritate și calitate](ATF_RentzQuality.md) deține criteriile încă deschise ale adaptoarelor și adopției binare. Validarea, lifecycle-ul și guvernanța sursei au contracte stabile și regresii la proprietarul lor; calificarea nativă rămâne separată.

## Închidere și transfer

- Închideți un criteriu numai cu sursă și verificare identificate.
- Promovați mecanismul stabil în documentul responsabil înaintea eliminării explicației din feedback.
- Păstrați separat regulile jocului, datele unei rulări și defectele bibliotecii comune.

## Biblioteci comune

`atf_j0` deține codul, testele native și împachetarea bibliotecilor comune. [Feedback-ul responsabilului](../../../atf_j0/docs/feedback/README.md) primește cerințele generice, cu JAR/hash, reproducere, așteptat/observat și criteriu de acceptanță; consumatorul păstrează verificarea sa locală.

## Materiale istorice

[Observarea Romanian Hard](ORIGINAL_ROMANIAN_LIVE_Feedback.md) și [partida-model anterioară](REFERENCE_MATCH_Feedback.md) rămân materiale Contract Whist. Nu intră în calificarea Rentz și nu sunt backlog-ul regulilor noului joc.
