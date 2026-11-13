# Feedback · RAG pentru release

RAG-ul local citează sursele și explică porțile. Verificatoarele deterministe decid starea dovezilor. [Procesul J5](../rentz/ATF_Release.md)

## Domeniul platformelor

**La introducerea unui release multiplatformă.** RAG-ul citează contractul platformei declarate și cere dovezile sale. Proba negativă prezintă numai APK și receipte Android pentru o livrare care declară și iOS. Răspunsul identifică platforma și dovada lipsă. [Contractul iOS](../rentz/ATF_Release.md#calificarea-ios) · [Feedback-ul iOS](ATF_Rentz.md#platforma-ios)

## Retenția spațiilor de lucru

**P2 · Responsabil: infrastructura J5.** Controalele creează fixture-uri și copii de compilare în depozitul comun. După rulare, inventarul curent verifică înregistrarea lor, dar nu arată care mai sunt necesare pentru reproducerea dovezii.

- Inventariați spațiile de lucru și cache-urile folosite de receipte, classpath-uri, procese active și worktree-uri Git.
- Lansatorul RAG își curăță directorul de lucru JVM. Extindeți inventarul la buildurile și fixture-urile vechi, cu proprietar și criteriu explicit pentru fiecare retragere.
- Verificați prin fixture-uri un director regenerabil, un director referit de un receipt, o rulare activă și o cale care traversează un link. [Contractul de retenție](../rentz/ATF_Infrastructure.md#curățare-și-probe)
