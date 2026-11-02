# FARO V5 — Handoff durable para chat nuevo (2026-11-02)

**Estado de decisión:** USER CANCELLED ALL SCHEDULED WORK / READ_ONLY / NO DEVELOPMENT AUTHORIZATION CARRIED FORWARD.
**Propósito:** preservar el contexto sin repetir auditorías ni iniciar trabajo en el chat nuevo sin conversar primero.
**Repo:** etacsantafe-source/FARO-V5.
**Rama de código de referencia:** `work/t4-saf-automated-hardening-20261101`.
**HEAD verificado en esta conversación:** `a942a53bd84ee849502d966ca9b52e002a194c6e`.
**main intacta:** `9bae214541f466a62e149853931b96abb8d74ed9`.
**Esta rama `docs/s2-handoff-chat-20261102` es SOLO documental**; no equivale a la rama de desarrollo y no autoriza merge ni cutover.

## Decisión humana más reciente

El usuario pidió explícitamente **CANCELAR TODAS LAS TAREAS**, discutir la naturaleza y activación de V2, y **continuar en un chat nuevo** con contexto durable. Este pedido revoca la campaña FARO Noche S2 que se acababa de programar y su extensión horaria. No activar ni reprogramar nada por inferencia; el siguiente chat comienza en conversación/READ_ONLY, sin commits de código ni nuevas Actions hasta una nueva orden.

Se desactivaron y comprobaron las tres únicas automatizaciones activas de las 25 encontradas:
- FARO Noche S2 A: `6ae806ec02f8819191a80603cd849297`, OFF.
- FARO Noche S2 B: `6ae8070b29d08191bccd0c04186147ea`, OFF.
- FARO S2 Informe 06:10: `6ae80721b12c8191830d7c52436db04c`, OFF.

**Resultado confirmado por Automations.peek:** 0 automatizaciones activas al cancelarlas. Desactivar impide disparos futuros, pero NO demuestra por sí solo que no haya otro proceso ya en marcha. Antes de cualquier eventual escritura futura, revalidar escritor/lock/leases y HEAD; nunca robar un writer ambiguo.
**No se ejecutó desarrollo nocturno de esta nueva campaña en la conversación.** La rama de trabajo prevista `work/s2-chat-finish-night-20261102` no existía al verificar con GitHub; no crearla automáticamente.

## Estado de S2 al handoff

Fuente canónica: `docs/s2/S2_A6_CURRENT.json` y `docs/s2/S2_A6_CERTIFICATION_DECISION_20261102.md` en la rama de código de referencia.

- S2.A6 **OPEN 6/8 CERTIFIED**. Certificados #1 FIVE_OWNERS, #2 FBS2_ISOLATED_RESTORE, #3 JOURNAL_REPLAY_WAL, #5 F2_ANDROID_ACTIVATION_RECOVERY, #6 F3_FIVE_STORE_BACKUP_SAF_CONSENT (solo alcance aislado) y #7 F4_INTEGRAL_REGRESSIONS.
- #4 F1_ANDROID_CHAT_E2E y #8 T4_PHYSICAL **OPEN / FROZEN_BLOCKED_BY_CHAT_UI**; congelados para no exigir recorridos físicos imposibles, no equivalen a PASS.
- A1–A5 cerraron **CONDITIONAL** históricamente y trasladaron el acoplamiento visible/real a A6. La selección/lectura de adjuntos A5 está implementada por partes, pero su recorrido usuario→adjunto→envío autorizado no quedó demostrado integrado; investigar al retomar sin presentar como certificado.
- La pantalla `FaroV5ActiveChatScreen.kt` existe con Composer, Timeline, Review y acción de Apply, pero no se dispone de un Chat funcional de uso normal. En `FaroV5CompositionRoot.java`, V1 activo no tiene Workspace quinto owner, puertos de Chat pueden ser nulos y asistente/Apply se bloquean por `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`. La superficie de certificación `FaroV5QuarantinedChatCertificationSurface.kt` monta Chat aislado con `applyRequestPort=null`. Hay fixture de instrumentación separado con Apply aislado.
- La pantalla de IA `FaroV5AiSettingsActivity.kt` configura consentimiento y clave Groq local en Keystore y tiene un probe debug con mensaje fijo; el usuario reportó Groq real VERDE y revocación VERDE, pero eso NO certifica Chat→Review→Apply→receipt→Delivery completo.
- Pruebas automáticas existentes: F1 #41266155404 27/27; F2 #41276096648 Android 5/5 y #41277220813 JVM 16/16; F4 #41277545474 36/36 + 27/27; F3/T4 SAF #41379627980 32/32. El usuario reportó exportar/restaurar SAF verdes en Moto G15. **No repetir pruebas SAF ni certificaciones ya válidas por formalidad**; solo repetir tests afectados por cambios reales. No exigir ISO/IRAM.
- `V2_ACTIVE_ENABLED=false`; S3 y S4 **NOT OPENED**; V1/Reality productiva no se tocan.

## ¿Qué es V2 y qué se requiere para activarla?

**V1/V2 se refieren a generaciones de almacenamiento/autoridad del host, NO a una IA diferente ni una aplicación extra.** V1 es la generación histórica protegida de cuatro stores; V2 es el grafo nuevo de cinco: Reality, Commercial Evidence, Web Evidence, Auxiliary y Workspace (Chat). V2 incorpora contratos de conversación y ciclo de vida con recuperaciones seguras.

La fuente `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5HostCertification.kt` mantiene `const val V2_ACTIVE_ENABLED: Boolean = false` y dice que no habilitar V2 hasta pasar la integración **completa de S2.A6 F1–F4 + T4 físico**. También es necesario para una habilitación PRODUCTIVA posterior un go/no-go humano explícito, transición/activación segura de generación con verificación de cinco owners y su salud, write gate único, PREPARED/COMMITTED y WAL/journal, rollback y recuperación/idempotencia tras crash, protección de V1 y sin doble autoridad, consentimiento/privacidad/egreso, y no eludir policies appCompositionPolicyCheck. **Cerrar los criterios técnicos aislados 8/8 NO constituye por sí solo permiso ni prueba de cutover de producción.** No cambiar el flag para hacer aparecer Chat mientras falten las garantías.

## Propuesta para conversar en el chat nuevo, SIN iniciar automáticamente

Objetivo del usuario: **terminar S2 de una vez, evitando pruebas físicas repetidas**. Priorizar integrar una pantalla Chat REAL utilizable en host aislado seguro con Workspace/Composer/Timeline y Groq solo tras consentimiento explícito en el teléfono, luego Review→Apply aislado→receipt→Delivery→reinicio, cerrar la conexión pendiente A5 de adjuntos conforme contrato, ejecutar pruebas dirigidas JVM/Android emulado y empaquetar una sola APK con autoprueba/guiado mínimo de Moto G15. Reutilizar los PASS previos. La validación física imprescindible deberá ser una única sesión final y muy corta. No confundir emulador con teléfono real ni inventar el PASS T4.

**Siguiente mensaje del usuario en chat nuevo:** `Continuar FARO V5` (suficiente). Recuperar este documento y el estado actual de GitHub, explicar V2 y **conversar primero**. No volver a solicitar que el usuario copie este handoff ni activación Noche sin autorización nueva.

## Referencias

- `docs/s2/S2_A6_CURRENT.json`
- `docs/s2/S2_A6_CERTIFICATION_DECISION_20261102.md`
- `docs/S2_A6_BATCHED_PHYSICAL_T4_DECISION_20261031.md`
- `docs/f1-auto/F1_FROZEN_READY_FOR_T4_20261101.md`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5HostCertification.kt`
- SHA código verificado: `a942a53bd84ee849502d966ca9b52e002a194c6e`.

**Criterio de honestidad:** este documento es handoff de conversación y decisiones, no certificación nueva, no código productivo ni evidencia de ejecución nocturna.
