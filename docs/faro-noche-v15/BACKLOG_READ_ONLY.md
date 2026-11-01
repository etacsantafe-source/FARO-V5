# FARO Noche V1.5 — Cola de investigación READ_ONLY

**Sólo preparación:** buscar archivos, leer logs y contratos, describir hipótesis/diffs en checkpoint o memoria de sesión. Sin escribir código ni alterar GitHub si el writer está ocupado, si falta aprobación o fuera de ventana.

## F2 — prioridad absoluta hasta READY_FOR_T4
- Partir del PASS real [#41273805209](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41273805209) (3/3, SHA `736ebd63...`) y del checkpoint `docs/f2-manual/F2_S01_CHECKPOINT_20261101.md`.
- Inspeccionar si falta integración Android con ciclo de arranque, admission/recovery, fallas post-switch, fail-closed, rollback, idempotencia y ausencia de escrituras antes de recovery completo. Distinguir tests JVM que ya existen de E2E real con 5 SQLite.
- Elaborar la mínima matriz: activación válida, crash en corte de puntero y reanudación, WAL active reopen, target corrupt/incompleto, V1 protegido, no doble replay/fence, bloqueo de writes durante recuperación, retry.
- Antes de escribir, ubicar el código real de CompositionRoot, MaintenanceRuntime, StartupReadiness, ValidatedGenerationActivationCoordinator y tests ya existentes. **No activar V2**; aislar E2E en test para Android emulador.
- Si no queda trabajo autónomo funcional y T4/humano es lo único pendiente, congelar `F2_READY_FOR_T4` con SHA y pruebas, y sólo entonces habilitar F3.

## F3 — sólo cuando F2 queda listo automáticamente
- Decisión opción A: UI mínima S2 + picker SAF nativo + consentimiento específico; no asumir superficie definitiva S5.
- Reutilizar `FaroV5F3SafConsentInstrumentationTest`, `FaroV5F3QuarantinedSafRoundTripInstrumentationTest`, `FaroV5MaintenanceRuntime`, `FaroV5FiveStoreSafConsentActivity` y workflow `.github/workflows/faro-v5-v14-f3-saf-directed.yml`.
- Investigar brechas del flujo real de exportar/restaurar cinco almacenes cifrados autenticados, gestión de cancelación/URI, archive pinning, journal, integridad, recuperación tras crash, no exfiltración, no overwrites, restauración segura sin switch de producción.
- Tests dirigidos en emulador y XML por SHA. Pasar F3 a `READY_FOR_T4` sólo cuando no haya más trabajo automático seguro.

## F4 — sólo cuando F3 queda listo automáticamente
- Matriz de regresión integradora: F1 Chat/Groq synthetic/consent/Review-Apply-Delivery, F2 activación/recovery, F3 SAF backup/restore, 5 stores + WAL + journals; compatibilidad V1, V2 OFF, no doble efecto, recuperación/reintento, ausencia de escrituras Reality no aprobadas.
- Reutilizar evidencia preexistente por SHA y ejecutar un gate integrador con tests nuevos sólo ante cobertura incompleta. Registrar XML exacto y regresiones reales.
- Al quedar `F4_READY_FOR_T4` detener ejecución autónoma, guardar hoja de ruta para el paquete físico/humano T4 y cerrar antes de 06:00.

## Tareas mientras el writer está ocupado
1. Leer estado HEAD/lease y Actions en vuelo; registrar ID SHA.
2. Leer un único archivo o log relevante del hueco actual; identificar su causa y mínimo cambio.
3. Diseñar el siguiente lote de pruebas y criterios de éxito sin publicar diff.
4. Preparar reutilización de PASS exacto para no gastar minutos en tests repetidos.
5. Si el bloqueo persiste, dejar un checkpoint **sólo READ_ONLY** en respuesta de la automatización; no tocar el repo ni afirmar writer NONE sin CAS.

## No-hacer
- Ninguna tarea de S3/S4, ISO/IRAM, investigación comercial, pagos, IA live con datos, Android físico, release APK o cambios de producción.
- No iniciar F3 por un ROJO no resuelto de F2, ni F4 por un ROJO no resuelto de F3.
- No volver a explorar asuntos ya cerrados o repetir F1 27/27 salvo regresión atribuible al código nuevo.
