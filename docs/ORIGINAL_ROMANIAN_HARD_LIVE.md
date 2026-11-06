# Partida românească în aplicația originală

Runnerul dedicat vizează pachetul com.qsoft.android.whist, versiunea 4.5 cu
versionCode 145. Scenariul urmărește patru locuri, dificultatea Hard și
programul românesc de 24 de mâini cu 94 de levate.

## Rulare

Comanda implicită construiește o copie izolată și rulează componentele de
parser, decizie și audit offline.

.\scripts\run-original-romanian-hard.ps1

O execuție Android presupune un dispozitiv disponibil exclusiv. Reluarea unei
încercări folosește directorul acelei încercări și o destinație nouă pentru
dovezi.

.\scripts\run-original-romanian-hard.ps1 -WithAndroid -ExclusiveDeviceConfirmed -ModeLabel Romanian

## Ce verifică fiecare componentă

| Componentă | Responsabilitate |
|---|---|
| Decizia publică | Mâna proprie, cartea întoarsă, licitațiile, istoria publică și necesarul de levate. |
| Confirmarea inputului | Identitatea cărții acceptate sau valoarea licitației, actorul și progresul exact al stării. |
| Auditul independent | Distribuția, obligația de culoare sau atu, câștigătorul levatei, punctele și seria fiecărui loc. |
| Continuarea | Compararea stării observate cu ultima acțiune și confirmarea ei înainte de un nou input. |
| Verdictul final | Completitudinea celor 24 de mâini, auditul regulilor și poziția jucătorului se raportează separat. |

Originalul folosește +5 pentru seria pozitivă eligibilă. În Whist Lab valoarea
se alege înaintea jocului.

[Instrucțiunile originalului](ORIGINAL_INSTRUCTIONS_COVERAGE.md) ·
[Feedback privind reluarea](feedback/ORIGINAL_ROMANIAN_LIVE_Feedback.md) ·
[Testarea Contract Whist](ATF_Android_Whist_Testing.md)