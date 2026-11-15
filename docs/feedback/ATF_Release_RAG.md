# Feedback · RAG pentru release

RAG-ul local citează sursele și explică porțile. Verificatoarele deterministe decid starea dovezilor. [Procesul J5](../rentz/ATF_Release.md)

## Retenția spațiilor de lucru

**P2 · Responsabil: infrastructura J5.** Extindeți inventarul read-only la cache-uri și fixture-uri, cu aceleași motive de păstrare și dovezi pe fiecare cale. Inventarul pentru `target` are deja un contract propriu.

- Inventarul trebuie să urmărească legăturile de la manifestul ediției până la fișierele Extent, capturile originale, workspace-uri și classpath-urile atestate. Verificați același manifest după curățare.
- Produceți planul și jurnalul execuției cu o decizie pe cale. Proba de componentă păstrează un fixture referit și selectează unul regenerabil. Dovezile inaccesibile, worktree-urile active și schimbările de conținut după plan blochează operația. [Contractul de retenție](../rentz/ATF_Infrastructure.md#inventarul-țintelor-maven)

## Recuperarea publicării întrerupte

**P2 · Responsabil: distribuția J5.** README-ul și APK-ul sunt verificate de contracte versionate. Succesiunea mutațiilor GitHub depinde încă de pașii operatorului. O întrerupere după crearea draftului sau după upload cere reconcilierea înaintea reluării.

- Introduceți o reluare verificabilă pornind de la commit, tag, release ID și hash-urile APK/checksum. Folosiți răspunsuri simulate pentru tag existent, draft parțial, asset identic și push refuzat.
- Refuzați un asset cu octeți diferiți. Ieșirea trebuie să indice pasul reluabil și ultima dovadă confirmată, cu o singură intrare APK în README. [Contractul publicării](../rentz/ATF_Release.md#publicare-și-descărcare)
