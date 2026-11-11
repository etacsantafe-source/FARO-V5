# FARO V5 — Work Turbo: ejecución por paquetes cerrados · V1 (2026-11-11)

## Alcance
Maximizar **B7 PASS verificables por sesión de Work**, no minutos declarados ni número de commits. El usuario observó que Work volvió a indicar 100% disponible y una regeneración de cuota en 4h; esto es observación de UI **no comprobada por el repositorio** y no permite prometer reinicios automáticos, duración o autonomía ilimitada. La sesión solo ejecuta cuando está activa/autorizada; no programar automatizaciones para eludir límites.

## Cola 3 posiciones, un único permiso READY
- **EJECUTABLE:** X03 U01 `S3_A5_7_X03_U01_EXPLICIT_CONSULT_DEV` tras permiso documental individual READY y preflight. Work solo hace B6 → CI exacto → RED_FIX si corresponde → B7 → checkpoint → RELEASED. No se autoriza U02 en su permiso.
- **PREAUDIT PARA SIGUIENTE:** X03 U02, gateway real y consentimiento: exige definir host de IA **distinto del V2 CORE DEV sin INTERNET**. No habilitar `INTERNET` en V2 DEV para acelerar. Reusar gateway S2 bajo otro aislamiento autorizado, o justificar alternativa segura mediante B1–B5. **STOP NEEDS_PREP para código hasta permiso individual**.
- **BANCO READ_ONLY:** X09 Review explícito (puertos `FaroV5ActiveChatReviewSession`, `FaroV5ModelReviewBridge`, `FaroV5PlanReview`) y X10 Apply/receipts (`FaroV5ActiveReviewedApplySession`, `FaroV5QuarantinedReviewedApplyDeliveryPort`, `ReviewedConversationApplyCoordinator`). Contratos identificados, no concluir B1–B5 ni READY sin auditoría específica. Orden global aprobado X03 → X09 → X10 → X01 → X02 → X06 → X08 → X12, luego aceptación y G15 físico.

## Ruta crítica antidesperdicio
1. Al iniciar Work: recuperar HEAD, writer real, permiso `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`, puntero y última CI. Si otra sesión certificó esta unidad exacta, saltarla sin recompilar y comprobar siguiente READY.
2. Antes de código: leer documento B1–B5 exacto, paquete JSON y scopes; ejecutar preflight `python3 scripts/quality/faro-protocol-gate.py --mode preflight --unit S3_A5_7_X03_U01_EXPLICIT_CONSULT_DEV --path ...` para TODAS las rutas permitidas; si STOP, devolver causa precisa al preparador. **No gastar tokens rehaciendo B1–B5 ya válidos**.
3. Adquirir writer CAS, programar un cambio funcional mínimo con `FARO-UNIT:S3_A5_7_X03_U01_EXPLICIT_CONSULT_DEV`, publicar SHA. **Liberar writer antes de CI**; no esperar CI bajo HELD.
4. CI por gradiente: comprobar sintaxis/Kotlin con la tarea más pequeña (si el runner lo permite), luego test dirigido + APK/XML de la unidad y guard del mismo SHA. Reusar cache y workflows existentes; **no rerun de suites aprobadas** salvo delta. Una build verde sin XML no certifica B7.
5. Ante fallo: obtener job/log/stack de SHA exacto, clasificar `KOTLIN_COMPILE | TEST_ASSERTION | UI_SELECTOR | INFRA_BOOT | PROTOCOL | PRODUCT_DEFECT`; documentar B7 RED_FIX ANTES del próximo commit funcional. Corregir una hipótesis causal por commit y verificar de nuevo. Si dos intentos consecutivos fallan por causas desconocidas, STOP `NEEDS_PREP`; no bucle ni relajación de tests.
6. Con CI verde: auditar cambios reales y artefactos XML/APK, B7 independiente, actualizar puntero+permiso (BLOCKED por cierre o READY solo con preauditoría de próxima unidad) en commit documental separado. Verificar HEAD final, guard y writer RELEASED. Entregar prompt corto con SHA y próxima acción.
7. Durante CI puede analizar la siguiente unidad **READ_ONLY** sin competir por writer ni publicar cambios. No arrancar Work paralelo ni auto-cancelar CI activa de otro writer.

## Medidas de productividad (solo observables)
Registrar por unidad: `unit_id`, `start_head`, `code_sha`, `ci_run_id`, `guard_run_id`, `XML PASS/FAIL`, `first_pass_or_redfix_count`, `scope_changed`, `B7`, `checkpoint`, `writer_epoch`, `blocked_reason`, `duration_if_observed`, `tokens_if_observed`. Objetivo operacional: reducir rondas de STOP/NEEDS_PREP, iteraciones duplicadas y espera con writer, nunca declarar «X03 completo» por U01.

## No negociable
S2 8/8 frozen; A3 M01–M05 PASS DEV no repetir; X04/X05 API35 2/2, X07/X11 JVM 4/4 y subcasos X01/X02/X06/X08/X12 PASS scoped sin duplicación; main y V4.4 sin tocar; productiva V2 OFF; S4/Auto/Noche OFF; sin APIs pagas ni credenciales en GitHub; un solo writer CAS; G15 físico solo al final; protocolo B1–B7, seven-block guard y permisos exactos.
