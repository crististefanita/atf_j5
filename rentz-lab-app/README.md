# Laboratorul aplicației Rentz

## Pe scurt

Directorul deține modelul local independent al jocului, jucătorii Normal/Hard și [prezentarea Android minimală](app/README.md). [Motorul](game-core/README.md) rămâne verificabil fără dispozitiv; adaptorul grafic apelează același domeniu și păstrează separat jurnalul privat și observația jucătorului.

## Intrări

| Intrare | Scop |
|---|---|
| Maven din proiect | Motor, integrare cu Kit și publicarea verificărilor. |
| `algorithm-build` | Configurare Gradle exclusiv JVM pentru motor. |
| `settings.gradle` | Motorul și aplicația Android; SDK-ul este necesar pentru această selecție. |

Din rădăcina proiectului, în PowerShell 7, cu JDK 21, Maven și cache-ul pregătit:

```powershell
.\scripts\run-rentz-components.ps1 -Collection quick
```

Lansatorul alocă un workspace înregistrat și rezultate externe, apoi verifică identitatea sursei și populația executată. SDK-ul Android nu este necesar acestei selecții; [infrastructura](../docs/rentz/ATF_Infrastructure.md) definește directoarele, cache-urile și contractul izolării.

Rezultatul califică modelul și profilul declarat; [sursele regulilor](../docs/rentz/ATF_RuleSources.md) dețin limita față de aplicația originală.

[Protocolul Android](../docs/rentz/ATF_Android.md) explică build-ul izolat, identitatea APK-ului și acceptanța prin controale native. Instalarea nu este o condiție pentru evaluarea offline a jucătorilor.
