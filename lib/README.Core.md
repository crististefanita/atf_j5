# Core — contracte și mecanisme comune

## Pe scurt

`atf-core` oferă vocabularul și mecanismele reutilizabile ale ATF. Folosiți-l pentru a compune adaptoare și execuții, păstrând regulile aplicației în consumer.

| Aveți nevoie de | API de pornire |
|---|---|
| Cereri, răspunsuri și execuție HTTP | `ApiRequest`, `ApiResponse`, `ApiTransport`, `ApiExecutor` |
| Configurație capturată pentru o rulare | `AtfConfiguration`, `ApiSettingsSnapshot`, `AtfRuntimeSnapshot` |
| JSON strict și redactarea datelor sensibile | `JsonDocuments`, `SensitiveDataRedactor` |
| Eliberarea resurselor în ordine inversă | `CleanupRegistry` |
| Sincronizare selectivă cu verificarea baseline-ului | `AtfPromotionManifest`, `AtfPromotionGuard` |

## Exemplu minim

Fragment într-o metodă care poate propaga `IOException`; construiește datele fără a trimite un request:

```java
import atf.core.http.request.ApiRequest;
import atf.core.http.request.ApiUrlBuilder;
import atf.core.json.JsonDocuments;
import java.net.URI;

var request = ApiRequest.of("GET", "/orders/42");
var url = new ApiUrlBuilder(URI.create("https://example.test/api"))
        .build(request);
var body = JsonDocuments.read("{\"total\":19.90}");
var total = body.path("total").decimalValue();
```

`url` este `https://example.test/api/orders/42`. `JsonDocuments` respinge cheile duplicate și conținutul rămas după document; numerele zecimale nu trec prin `double`.

## Utilizare în ATF

- Injectați un `ApiTransport` în `ApiExecutor`; transportul real sau mock se alege explicit la compoziție.
- `HttpClientTransport` și `UrlConnectionTransport` propagă anularea către operația deținută; fallback-ul include body-ul în deadline. Anularea nu dovedește rollback și nu autorizează replay.
- Reutilizați snapshot-ul de configurație al rulării; oracolul, aserțiunile și verdictul de business aparțin consumerului.
- Păstrați modelul original pentru comparație; redactarea și preview-ul se aplică la publicarea diagnosticului.
- Core expune porturi neutre; [Console](README.Console.md), [Mock](README.Mock.md) și [Reporting](README.Reporting.md) au roluri distincte.

## Verificarea utilizării

| Acțiune | Rezultat observabil |
|---|---|
| Compilați fragmentul cu JDK 21 și dependențele din POM. | Tipurile și metodele se rezolvă din livrarea binară aleasă. |
| Executați fragmentul. | URL-ul are baza și calea cererii; `total` este numeric egal cu `19.90` prin `BigDecimal`. |
| Furnizați chei JSON duplicate sau conținut după rădăcină. | Parsarea refuză documentul, fără rezultat parțial acceptat. |

Exemplul construiește date locale; nu execută un apel HTTP. Verificarea transportului se face separat, prin adaptorul real ori mock selectat explicit.

---

Acest ghid descrie folosirea JAR-ului, nu sursa lui. Proveniența și hash-urile binarului sunt în [manifestul local](../library-provenance.json); lacunele și acceptanța consumerului J5 sunt în [registrul local](../docs/feedback/README.md#biblioteci-comune).
