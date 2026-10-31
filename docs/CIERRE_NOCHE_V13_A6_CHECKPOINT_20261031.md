# FARO V5 — Noche A V1.3 · A6 · 31/10/2026 03:40 Argentina

Estado al cierre: **WAIT_RESOURCE / HANDOFF / S2.A6 OPEN 3/8**. Rama privada exclusiva `work/cierre-noche-v13-20261030`. Writer epoch28 a liberar por CAS en este commit; verificación posterior obligatoria.

## Funcionalidad
- Código HEAD `cf8b2233b0ee04d304db1cc9718fac6164608138`: F3 separa consentimiento para exportar y restaurar, impide reutilizar consentimiento entre operaciones y lo consume al regresar de SAF o ante rechazo. Prueba Android actualizada. Archivos: `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5FiveStoreSafConsentActivity.kt`, `v5/app/src/androidTest/kotlin/ar/com/faro/v5/app/FaroV5F3SafConsentInstrumentationTest.kt`.
- F2 integración Maintenance + salud SQLite cinco owners `588c40f` ya pasó CI dirigida, pero **NO** es certificación F2 completa.
- F3 UI mínima de consentimiento/SAF `f154ff0` pasó CI dirigida, pero no prueba exportación y restauración completas; V2 sigue OFF y no existe entrypoint de restauración desde la UI sin operación/journal confiable.
- F1 remoto consentido con credencial real sin verificar. F4 pendiente. T4 físico NOT_RUN.

## Evidencia CI verificada
- [#41175361420](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41175361420), SHA `588c40fc59b4b3828db44796eb1aad4395dead82`: SUCCESS, Android emulador **6/6**, JVM **2/2**, SQLite **2/2**; wall **850 s (14m10s)**.
- [#41175543642](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41175543642), SHA `f154ff0b8ef2d5a1aff5151fc417b7f83add7dbe`: SUCCESS, Android emulador **7/7**, JVM **2/2**, SQLite **2/2**; wall **1255 s (20m55s)**.
- [#41175481044](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41175481044): CANCELLED por concurrencia previa, no por este turno.
- [#41177654138](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41177654138), SHA `cf8b2233b0ee04d304db1cc9718fac6164608138`: **FAILURE antes de ejecución**. Tres jobs runner_id=0, sin pasos, sin XML, logs 404 BlobNotFound; wall **4 s**. Causa externa aún no confirmada; NO atribuir a fallo Kotlin ni declarar PASS. No se repitió a ciegas.
- GitHub timing endpoint informa `billable.UBUNTU.total_ms=0` para esos runs, pero no se toma como contabilidad definitiva. Las duraciones anteriores son wall, no minutos facturados.

## Ocho criterios inmutables
1 Five owners CERTIFIED; 2 FBS2 restore CERTIFIED; 3 Journal/WAL CERTIFIED; 4 F1 OPEN; 5 F2 OPEN; 6 F3 OPEN; 7 F4 OPEN; 8 T4 físico NOT_RUN. **3/8**. V2 OFF; S3 y S4 sin abrir; sin cutover.

## Turno, exclusividad y siguiente acción
- Inicio efectivo `2026-10-31T06:40:44.005Z`; cierre observado `2026-10-31T06:43:32.800Z`; **2.81 min reales**, sin espera simulada. Writer epoch28 adquirido mediante CAS desde `bed7afa2` y código commit `cf8b2233b0ee04d304db1cc9718fac6164608138`; release CAS a comprobar después del commit de checkpoint.
- No quedan Actions en vuelo observadas. Hipótesis CI: problema de asignación de runner/recurso, sin evidencia concluyente. Investigar antes de repetir.
- Próximo turno: verificar asignación GitHub Actions; probar sólo F3 afectado si CI disponible; desarrollar entrada de restore con journal confiable sin V2 cutover; completar F2 y F1. No certificar S2 por pruebas parciales.
