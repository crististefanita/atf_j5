# Feedback · RAG pentru release

RAG-ul local citează sursele și explică porțile. Verificatoarele deterministe decid starea dovezilor. [Procesul J5](../rentz/ATF_Release.md)

## Domeniul platformelor

**La introducerea unui release multiplatformă.** RAG-ul citează contractul platformei declarate și cere dovezile sale. Proba negativă prezintă numai APK și receipte Android pentru o livrare care declară și iOS. Răspunsul identifică platforma și dovada lipsă. [Contractul iOS](../rentz/ATF_Release.md#calificarea-ios) · [Feedback-ul iOS](ATF_Rentz.md#platforma-ios)

## Raportarea eșecului înainte de APK

| Prioritate | Contract și stare | Probă permanentă |
|---|---|---|
| P1 · Închis | La Maven `FAIL` înainte de Android JVM, păstrează `NOT_RUN`, release `BLOCKED` și Extent, inclusiv cu `game-core` primul. | `ReleaseGovernanceTest.failedMavenRetainsItsVerdictWhenAndroidJvmWasNotRun` |
| P1 · Închis | Evaluatorul RAG primește rădăcina externă canonică; un receipt Android JVM valid din worktree nu mai este clasificat eronat `BLOCKED`. | `test-qualified-runtime.ps1` și `ReleaseGovernanceTest.androidJvmAcceptsOnlyARegisteredComponentsWorkspaceForTheSameCheckout` |
| P2 · Închis | Worktree-urile împart rădăcina Git canonică; `validation` este colecție externă înregistrată; payloadurile versionate rămân hash-bound, în afara lintului de ghiduri active. | `test-workspace-governance.ps1`, inventarul infrastructurii și `ReleaseCatalog` |
