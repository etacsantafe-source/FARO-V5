# FARO V5 — W01 D1B1 · lectura local real sin biometría obligatoria — B1–B5
Fecha 2026-11-17. Estado: **PREAUDITORÍA DOC_ONLY, SIN PERMISO READY, B6 NO RUN, B7 NO RUN**. W1 OPEN, W2 STOP.

## B1 — Hechos de HEAD y decisión vinculante
HEAD `55c0041186f39ddb41fd33cec2cbcf04c81bd98c`. Decisión explícita del usuario `docs/v5/FARO_S4_W01_SECURITY_OPTIONAL_DEFAULT_OFF_USER_DECISION_20261117.md` blob `ac773266ae4fc34c79c53cbdf5558fc8259299b4`: consulta y ediciones locales sin huella/PIN; eventual opción Configuración OFF por defecto. No se autoriza eliminar ACL técnico, sandbox, integridad o el único writer.
D1B0 pantalla DEV B7 PASS scoped, `f2fa5f72814ec3e23d153241244c758b74205f0b`, 2/2 Android API35 `44173197061`; lector expresamente DENY. D0/D1A/D1B0 NO repetir.
**Brecha descubierta en el código real:** `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` blob `e6578207046f3a3c9288d8d6a67ea4575890e606`, `openValidatedV2Finance9Stage` líneas 249–286 exige destino NO activo; `openValidatedV2Internal` líneas 301–304 prohíbe Finance9 como ACTIVE. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveGenerationGraphOwner.kt` blob `d690f6ea59c6f94ad5c3e9dd966542b43994ad73`, `openExistingPlanning8ForCertifiedHost` exige schema8. La pantalla D1B0 no puede abrir Finance9 como si ya fuera banco activo.
`v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt` blob `78a394142ad68760d9449bca2dc5dc0de8f00cd6`, líneas 204–309 abre realmente el grafo ACTIVE validado, con puntero completo, journal/replay-fence y `financePersistence.read()`, el cual YA alimenta `FaroV5FinanceSearchReadSource` con `financeGrant` desde sesión aislada. Pero el launcher mantiene una Session viva al navegar a otra Activity: no crear segundo grafo sin cesión explícita.
`v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` blob `01e947579b462c8f83bd4dcee07915a73c3102f4`: puertos `finance9ObservedSourceReadForDevOnly` y `finance9OpeningProvenanceReadForDevOnly` rechazan schema != 9 y sólo tienen STAGED9; los DTO no autorizan render.
`FaroV5AndroidSandboxPrincipal.kt` blob `38890c7e851a3556fa358a7bed7f864a2c059c84` ofrece UID/paquete/puntero/host-epoch técnico sin biometría ni identidad humana. `S4W01FinanceReadAuthHost.kt` blob `26a12243f7d42f9722ded60782dab542b1eef6ec` es un host dormido sólo de firma biométrica, no invocar para acceso default.

## B2 — Reutilización y comparación
Reusar el único dueño activo de Reality y su `FinanceSnapshotReadApi`, más el gate técnico local/epoch/foreground y `FaroV5FinanceSearchReadSource` que YA prueban lectura local de Finance con ACL de sesión. Nunca compartir una autoridad de escritura ni SQL connection arbitraria con otra Activity. Del núcleo V4.4 conservar AtomicApply, receipts/recovery; no duplicar una segunda fuente de verdad ni migrar directamente ACTIVE a schema9. Reutilizar el contrato de pantalla W01 ya B7 y control de lifecycle de D1B0, sin rediseñar V2 ni S3.

## B3 — Alternativas y elección
A) Montar `openValidatedV2Finance9Stage` en pantalla ACTIVE: **STOP** por aserción de diseño y corrupción semántica del puntero, NO ejecutar.
B) Habilitar automáticamente BiometricPrompt/KeyStore para salvar gate: **RECHAZADO** por decisión del usuario.
C) Hacer `true`, hash de principal falsificado o `Finance9ReadPolicyIssueRequest` manual desde UI: **STOP** por bypass de permisos.
D) **ELEGIDA por etapas:** primero lectura local de Finance desde el grafo ACTIVE ya certificado, mediante su única sesión DEV y read port existente con verificación de paquete/UID/puntero/epoch, sin huella. Renderizar sólo datos confirmados por el propio source con marca de fuente y generación, no presentar Finance8 como Finance9. En paralelo, preparar ruta explícita de activación segura Finance9 (migración, restore journal, rollback y cutover), como otra unidad con CI y consentimiento de cambios locales, sin usar código STAGED como si ACTIVE.
Referencias Android previamente auditadas en D1A/D1B0 para lifecycle, UID, ContentObserver; no repetir benchmark ni pruebas D0/D1A/D1B0.

## B4 — Próxima unidad de código acotada
**D1B1A**: crear adaptador de lectura informativa para `FinanceSnapshotReadApi` DEL DUEÑO ACTIVE, que exige `hostReady`, puntero completo exacto, epoch propio, UID/paquete W01 desde Android Application y validación de revisión de snapshot antes/después. Rechaza sin fuente, sin foreground, si falta o cambia generación, o si se revoca autorización de la sesión. No concede Apply ni escribe base ni crea otra conexión; respuesta unavailable distinta de balance $0.
**D1B1B**: vincular ese puerto autorizado a la Activity W01 sin segundo grafo. Diseñar transferencia/sesión única entre launcher y Activity, stop/revoke y reentrada, antes de autorizar B6; regresiones Chat/Guardados y Android API35; no modificar actividad principal global.
**D1B1C**: lectura Finance9 activa sólo después de migración/activación formal verificada por SHA/journal/CI y aceptación, nunca por llamado directo a `openValidatedV2Finance9Stage`.
Las UI de cuentas, movimientos y proyección pueden reutilizar el mapper/contratos ya creados una vez autorizado el origen real. `security_lock_enabled=false` es requisito de UX, no bypass de integridad.

## B5 — Revisión adversarial inicial
- Riesgo STOP: Finance9 sólo STAGED9, no ACTIVE; no montar su lector actual ni presentar su DTO como dato de usuario.
- Riesgo STOP: launcher contiene `session` material y la segunda Activity no puede crear otro escritor/grafo. Necesita diseño de ownership único y handoff sin restaurar grants al regresar de background.
- Riesgo STOP: Read API es de otro contrato histórico; no etiquetar datos como Finance9 confirmados ni dibujar saldo desconocido como cero, no mezclar monedas.
- Riesgo STOP: no usar un fake biométrico o `readPolicyIssuer` desde UI para sustituir consentimiento; en la opción OFF basta **sesión técnica local** con doble validación de estado y aislamiento Android, no permiso de operaciones bancarias.
- Riesgo STOP: A-B-A de puntero, cambio de revisión entre lecturas, cierre de Session, fallo de SQL, ContentObserver perdido, cierre de app. Cualquier contradicción => UNAVAILABLE, no saldo.
- Ejecutar B5 independiente documentado antes de emitir READY, con rutas exactas de D1B1A y tests propios. **Ningún permiso funcional emitido en este documento.**

## B6
NO RUN, porque el permiso vigente sigue BLOCKED.

## B7
NO RUN. W1 abierto, W2 detenido, V2 productiva OFF. El siguiente trabajo es D1B1A con B5 independiente y permiso acotado propio, no repetir D1A/D1B0.