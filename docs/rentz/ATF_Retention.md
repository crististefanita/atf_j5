# Retenția datelor de lucru

## Frontiera permanentă

Sursele versionate rămân în checkout-ul Git. Datele de lucru necesare continuării au o singură frontieră: `C:\work\rentz_back\.imp`. Alocarea respinge Windows TEMP și directoarele vecine care pot fi șterse.

```mermaid
flowchart LR
    G["Git: surse și reguli"] --> W["Workspace în .imp"]
    W --> Q["Calificare nouă"]
    Q --> E["Dovezi originale"]
    Q --> D["APK și WEB"]
    E --> R["Recitire RAG"]
    D --> R
    R --> C["Inventar și curățare"]
```

<details>
<summary><strong>Legendă — ce protejează fiecare etapă</strong></summary>

- **Workspace.** Materializează commitul fără a include build-uri în Git.
- **Calificare.** Produce dovezi pe sursa fixată și bibliotecile declarate.
- **Recitire.** Confruntă manifestele, receipt-urile și rapoartele originale înaintea curățării.

</details>

## Ce păstrăm

| Date | Rol | Condiție de eliminare |
|---|---|---|
| `infrastructure/signing` | Copie de recuperare a cheii canonice din `.atf-signing` | După verificarea unei alte copii recuperabile. |
| `learning` | Registru de expunere și corpus selectat pentru învățare | După import verificat; semințele expuse nu se uită. |
| `validation` | Dovezi originale ale livrării, probe relevante roșu/verde și inventarul migrării | După politica explicită de audit, nu ca intermediare. |
| `distribution` | Octeții ediției curente și receipt-urile descărcării | După verificarea succesorului și a recuperării din GitHub. |
| `infrastructure/workspaces` | Surse necomise sau runtime încă referit de o dovadă | După salvarea lucrului și inventarierea dependențelor. |
| `infrastructure/cache` | Dependențe regenerabile | După eliberarea rezervărilor; se poate reface din bibliotecile declarate. |

## Mutare și curățare

1. Inventariați fișierele, worktree-urile și modificările necomise. Nu confundați datele nerecuperabile cu un build repetabil.
2. Copiați datele selectate și verificați SHA-256 la ambele capete. Pentru lucrul paralel păstrați patch-ul binar, fișierele și commitul de bază.
3. Păstrați receipt-urile istorice nemodificate. Căile lor originale descriu execuția trecută; mutarea nu le face automat reluabile.
4. Calificați o rulare nouă direct în `.imp`. Recitiți dovezile cu RAG și verificați descărcarea ediției curente.
5. Ștergeți doar rădăcini fizice verificate, fără reparse points și fără rezervări active. Păstrați cheia, datele unice și dependențele încă referite.

[Infrastructură](ATF_Infrastructure.md) · [Procesul de release](ATF_Release.md)
