# Feedback · RAG pentru release

RAG-ul local citează sursele și explică porțile. Verificatoarele deterministe decid starea dovezilor. [Procesul J5](../rentz/ATF_Release.md)

## Domeniul platformelor

**La introducerea unui release multiplatformă.** RAG-ul citează contractul platformei declarate și cere dovezile sale. Proba negativă prezintă numai APK și receipte Android pentru o livrare care declară și iOS. Răspunsul identifică platforma și dovada lipsă. [Contractul iOS](../rentz/ATF_Release.md#calificarea-ios) · [Feedback-ul iOS](ATF_Rentz.md#platforma-ios)

## Retenția spațiilor de lucru

**P2 · Responsabil: infrastructura J5.** Extindeți inventarul read-only la cache-uri și fixture-uri, cu aceleași motive de păstrare și dovezi pe fiecare cale. Inventarul pentru `target` are deja un contract propriu.

- Dovediți că nicio intrare de classpath atestată nu dispare când retrageți un cache duplicat.
- Diferențiați un fixture de guvernanță cu receipt util de o copie regenerabilă. Includeți o sursă de dovezi inaccesibilă și un producător care scrie în timpul inventarierii. [Contractul de retenție](../rentz/ATF_Infrastructure.md#inventarul-țintelor-maven)
