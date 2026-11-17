# FARO V5 — D1B1A adversarial B5 independiente
2026-11-17. Unit `S4_W01_D1B1A_ACTIVE_FINANCE_SNAPSHOT_READ_FENCE_DEV`, DOC_ONLY / NO READY.
## B5
- **FALSIFICACIÓN DE TESTS:** constructores reciben callbacks que se pueden falsificar. Ningún resultado del test certifica UID real Android, presencia humana ni ACL durable. El futuro host debe obtener `AndroidSandboxTechnicalEvidence` de `FaroV5AndroidSandboxPrincipal` con `AndroidPlatformSandboxFactsReader(application)` exclusivamente, y fuente del mismo grafo/sesión previamente certificado.
- **OWNERSHIP:** en D1B1A jamás abrir RealityStore, SQL, GraphOwner, ContentProvider, Activity, aplicar cambios ni emitir read-policy. No exponer read DTO a UI ni almacenar como cache.
- **DOBLE MUESTREO:** validar puntero completo, host epoch, session ACL epoch, foreground, ownerReady, UID, package y sandbox antes, entre y después de dos llamadas de read. Los snapshots deben ser estructuralmente iguales, no solamente revision. Un fallo deshabilita la instancia irrevocablemente; volver al puntero anterior no revive. Cierre durante read niega.
- **FINANZAS:** no presentar Finance8 como Finance9. El source activo es FinanceSnapshot previo a schema9. `ledgerMinor` puede lanzar overflow: denegar. Limitar cuentas y entradas. No sumar importes de monedas distintas. No convertir ausencia en saldo 0.
- **REGRESIONES:** no invocar BiometricPrompt/KeyStore, no PIN, y no desactivar sanidad del sandbox, sin red/egress/pagos reales, no tocar S2/S3/V4.4/main ni W2, no repetir D0/D1A/D1B0.
- **ALCANCE EXACTO:** B6 sólo 3 rutas del B4 del documento B1–B5; JUnit 10 casos, compile W01 y XML. No afirmar emulador o G15 si sólo pasan JVM.
**Dictamen: GO CONDICIONAL a READY exact3 tras guard de docs, sin código permitido aún.**