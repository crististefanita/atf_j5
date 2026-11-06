# Fixture-ul complet și replay-ul Whist Lab — feedback

Actualizat la 4 noiembrie 2026. Acest document urmărește exclusiv producerul
J5 al fixture-ului original Oh Hell și handoff-ul către testele de produs J4.

## Stare verificată

- Testele care pornesc sau inspectează Contract Whist sunt în J5.
- J5 are runner separat pentru partida de 19 mâini; sursele ATF necesare se
  compilează într-un staging temporar, iar fixture-ul și raportul rămân externe.
- Testul verifică versionCode 145, deține o rezervare Android cu token propriu,
  o validează înaintea fiecărei interacțiuni și nu începe un joc nou dacă există
  o salvare fără dovezi de continuare.
- Raportul Extent este publicat prin API-ul UI comun și auditat cu capturi inline.
  Filtrul consumerului refuză să actualizeze pointerul Whist Lab pentru pachetul
  original.
- Compilarea test-compile pentru cele 89 de surse Android a trecut în TMP. Nu
  s-au executat teste și nu s-au trimis comenzi ADB în timpul separării.

Aceste verificări nu sunt o acceptanță live. Partida completă și dimensiunea
raportului nu au fost rulate după mutarea testului.

## Riscuri rămase

| Prioritate | Risc | Criteriu de închidere |
|---|---|---|
| P1 | Rezervarea dispozitivului este cooperativă; un controller care o ignoră poate schimba starea jocului. | Toate runner-ele Android activează și validează același lease înainte de orice input. |
| P1 | Fixture-ul complet și raportul J5 nu au încă o execuție live nouă după separare. | Un run proaspăt dovedește 19 mâini, 436 mutări, 109 levate, zero skipped și raportul auditat. |
| P2 | Volumul final al capturilor inline nu este măsurat pe trace-ul complet. | Se inspectează raportul real fără omiterea capturilor și se păstrează heap/volum observate. |

J4 consumă fișierul fixture pentru a verifica propriul motor și UI-ul. J4 nu
pornește Contract Whist; clasele OriginalWhistState și OriginalMatchOracle
rămase în J4 sunt adaptorul de fixture și oracolul replay-ului, nu E2E-uri ale
aplicației-model.

Defectele neutre Extent/fallback aparțin J0. J5 păstrează doar contractul
consumerului aplicației-model și dovada că fixture-ul este complet.

[Runner J5](../../scripts/run-reference-match.ps1) ·
[Testarea Whist Lab în J4](../../../atf_j4/atf-test-android/README.md) ·
[Feedback J0 Reporting](../../../atf_j0/docs/feedback/README.md)