# FARO V5 S3 — NOCHE_A 20:00 · CIERRE DURABLE 02/11/2026

## Estado comprobado
S3-N00 PASS A/B con limitación de stale CAS genérico (no se hizo carrera simultánea). P00 PASS_SPEC_ONLY R0–R3/H01–H04; 172 contratos oficiales intactos, +8 Guardados candidatos no aprobados. A1 **PARTIAL_CI_VERIFIED**, NO completada. S2 8/8 CLOSED, V2 app OFF, S4 OFF. Sin pruebas físicas.

## Trabajo funcional de este turno
- Investigación de composición real: `FaroV5BootstrapActivity.showShellSurface()` no pasa Home owner; `FaroV5CompositionRoot` expone Workspace/Timeline pero no implementaciones canónicas N-022 `home.read`, N-031 `attention.read`, N-032 `notice.read` ni owner de Guardados. Un catálogo con OperationIds NO demuestra implementación ni autoridad. La pantalla sigue mostrando «No disponible» y no inventa cifras.
- Nuevo `FaroV5HomeReadCoordinator`: agregación pura por owner inyectado, ausencia/ACL/errores => UNKNOWN, cero real distinto, cancelación propagada, comprobación estricta `expectedCanonicalSource` contra el valor devuelto para impedir cruces de dominio.
- Tests `FaroV5HomeReadCoordinatorTest`: cero/ausencia, error parcial, source mismatch, cancelación, owners vacíos. Se detectó que un test de expresión Kotlin devolvía excepción en vez de Unit y JUnit no lo descubría; corregido en commit `8956d2597ec9f7e8cf1b2d91b512bb76322c0231`.
- GitHub Actions dirigido por SHA: [#41542542171](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41542542171) SUCCESS **38/38 XML** para `e523dc2d35bbf2390d92005233d0c7d8d92e8e54`; [#41543063290](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41543063290) SUCCESS **38/38 XML** para `f8bd9f3a784e6ed3304cd05e27ed6c9c51524015` (un test de retorno no-Unit NO descubierto); [#41543680244](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41543680244) SUCCESS **39/39 XML** para `8956d2597ec9f7e8cf1b2d91b512bb76322c0231`. Logs y XML agregados verificados. Los PASS previos 31/31 y 34/34 no se repitieron.
- Investigación A2 READ_ONLY: AppSearch `SearchProjectionHit` devuelve sólo locator/stamp/rank y `SearchProjectionQuery` busca un namespace. La federación N-018, ACL owner y cobertura parcial no existen en ese índice. No reemplazarlo por FTS sin pruebas español/offline/rebuild.

## Bloqueo y próximo paso
A1 **BLOCKED_CANONICAL_HOME_OWNER_BINDING**: falta implementación real y composición de cuatro lecturas, identidad/dedupe de Pendientes, ACL/generación, asOf y lifecycle. El coordinator es seam testeada, NO un owner ni un binding. No marcar A1 PASS ni avanzar A2/A3/A4 por dependencia. Para B 20:30: recuperar este SHA, writer/queue, investigar cómo implementar N-022 sobre owners reales sin crear autoridad paralela; continuar A1. A3 +8 exige bounded reopen separado.

No escribir en main/V4.4, no activar app V2, no S4, no APIs pagas. Corte absoluto 22:00 AR. Writer liberado por CAS al cierre del turno; verificar estado remoto.
