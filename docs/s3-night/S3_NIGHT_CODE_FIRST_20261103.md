# FARO S3 — Noche recuperación CODE-FIRST, 3–4 noviembre 2026

Nueva autorización del usuario del 03/11/2026 07:12 AR: «Próximos toda la noche. Hay que recuperar», interpretada como próxima noche 03/11 23:00 a 04/11 07:00 en America/Argentina/Cordoba. Ventana nueva; revocación anterior sigue histórica. Sin ejecución antes del inicio. S2 CLOSED 8/8, S4 OFF, V2 app OFF, main/V4.4 intocables, 172 contratos oficiales, +8 Guardados solo REVIEWED_PROVISIONAL_NOT_APPROVED, sin APIs pagas y sin pruebas físicas hasta única campaña final.

## A/B y hard stop
A: 23:00, 00:00, 01:00, 02:00, 03:00, 04:00, 05:00, 06:00; B a las y media de cada hora desde 23:30 hasta 06:30. Cada slot: 0–20 trabajo seguro, 20–25 cierre, 25–27 último límite de checkpoint/release, 27–30 READ_ONLY. Writer propio se libera inmediatamente al terminar escritura o si runtime termina temprano. Hard stop 07:00, informe 07:10 READ_ONLY. La plataforma NO garantiza que un turno dure 20 minutos.

## Misión 1: MISSION-A1-ACL-UI
OPP-A1-ADV ya inventariada: NO redactar por tercera vez la hipótesis.
Código: v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5HomeIndicators.kt y FaroV5AdaptiveShell.kt.
Test: v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5HomeIndicatorsContractTest.kt.
Comprobar seam REAL de generación/ACL; escribir test de regresión que muestre Avisos=3, revocación manteniendo instancia readPort, recomposición, resultado No disponible en UI/accesibilidad. Si no existe infraestructura Compose, comprobarlo específicamente, crear prueba JVM útil separable o registrar bloqueo NUEVO con archivo, causa y siguiente experimento. No inventar eventos de autoridad, fuente/contador ni declarar defecto reproducido sin test. Cambio funcional mínimo y test dirigido; push en v5/surface/** dispara workflow CI de superficie. Certificar solo con run_id+SHA+XML reales.

## Alternativas concretas
OPP-N031 investigar/validar owner attention.read y ACL, no hasPendingReview como contador. OPP-N015 history.query paginado con receipt canónico, no lookup por ID. OPP-GUARDADOS solo investigación READ_ONLY y matriz de test MessageRef, sin implementar +8. OPP-A2 solo diseñar pruebas offline/FTS español, no iniciar funcionalidad antes de gates. Evitar repetir CI PASS por SHA.

## Preflight y control remoto
Leer queue.json CLOUD refs/heads/faro-v5-night-queue, writer.json refs/heads/faro-v5-writer-lock, rama work/s3-night-20261102, RUN_STATE, puntero y backlog reales. Exigir queue ENABLED, activation_authorized=true y 03/11 23:00 <= hora actual < 04/11 07:00. Writer HELD ajeno/CAS incierto -> READ_ONLY, nunca takeover/force sin expected_sha; CI en progreso no es writer HELD. Para código: acquire writer CAS, revalidar owner/epoch/HEAD, commit CAS en rama S3 y release propio con CAS. Releer RELEASED. No duplicar Actions ni efectos UNKNOWN. 2 ROJOS -> hipótesis nueva, STOP funcional.

## Productividad y watchdog
A las 00:20 AR 04/11 una tarea separada inspeccionará las 3 primeras sesiones y cambios verificables en código/tests posteriores al baseline de activación, o bloqueo NUEVO durable con prueba. Si solo hay informes repetidos sin producción: desactivar A/B y detener la cola de forma segura por CAS si writer libre; no interrumpir writer/CI en curso ni cancelar Actions; reportar. No se repite ocho horas si primeras sesiones están ociosas. Duración observable ≠ minutos de trabajo: métricas de scheduled/started/last-write/checkpoint/released/completed y tiempos por categoría reales o NO_REGISTRADO.

## Tests y certificación
El selector scripts/v5/faro-night-mission-gate.py tiene 17 tests Python escritos y registrados en checkpoint previo. Nuevo workflow CI dirigido verifica los 17 y guarda XML; PASS no se declara hasta ejecución GitHub exacta. Ni ese CI ni un contrato documental certifican scheduler autónomo. V22-01..19 y X01..12 NOT_RUN salvo evidencia nueva. Unidades sólo PASS con implementación, run_id/sha/XML; bloqueo nuevo tiene estado BLOCKED, no PASS.

## Estado inicial
PREPARING. No declarar ACTIVE hasta que queue/automations/HEAD/writer y autorización coincidan.
