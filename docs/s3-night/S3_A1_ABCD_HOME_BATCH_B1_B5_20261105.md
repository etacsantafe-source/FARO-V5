# FARO V5 — ABCD S3.A1 · preauditoría de batch owner-stamp (05/11/2026)

Unidad **S3_A1_HOME_BATCH_REVALIDATION**. HEAD revisado `07e03fa732e0041858cc09dc9aed3177b46a7ea9`; objetivo: cerrar un hueco concreto de revocación entre lecturas secuenciales de Inicio. **No** certifica fuentes N015/N031, conteos de Guardados, integración Home global ni cierre de S3.A1. Sin Android físico, sin egress, sin nuevo catálogo.

## B1 — Evidencia exacta
- `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5HomeReadCoordinator.kt`: `readIndicators()` lee cada indicador por orden, revalida `authorityStamp` antes/después de **ese** owner, y agrega de inmediato el conteo al mapa. Un owner temprano puede revocarse mientras se lee otro owner; la función devuelve el primero sin última comprobación global. No se presupone ninguna invalidación externa.
- `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5HomeReadCoordinatorTest.kt`: hay negativos de revocación DURANTE la lectura de un mismo owner, pero falta revocación de owner A MIENTRAS se consulta B.
- `FaroV5HomeNoticeBindingFactory.kt` conecta solo N032 a un owner real. N015 carece de timestamp de commit para historia global; N031 carece del owner combinado. `S3_A1_NOCHE_A_1600_OWNER_CODE_AUDIT_20261105.md` y `S3_A1_NOCHE_B_B3_B5_RESEARCH_20261105.md` contienen evidencia reutilizada; no repetirla.

## B2 — Comparación V4.4 / V5
V4.4 Home refresca datos al abrir, pero su UI no constituye autoridad para conservar un conteo tras revocación durante lecturas de fuentes independientes; no copiar ese refresco con efectos. V5 ya posee coordinador fail-closed y propiedad del sello por owner, sin invalidación ACL universal: **reutilizar**, no reescribir owners, base ni el drawer. Comparación registrada en `S3_A1_NOCHE_A_B1_B2_RECOVERY_20261105.md`.

## B3 — Alternativas investigadas
Reutilizar investigación específica A1 de `S3_A1_NOCHE_B_B3_B5_RESEARCH_20261105.md` (Android offline-first, Room multi-instance, OWASP privacy) sin nuevas dependencias: (1) **USAR** revalidación final por owner con sello propio; (2) **DESCARTAR** caché global y confiar solo en evento UI (no garantiza emisor ACL); (3) **DESCARTAR** Room multi-instance como bus genérico de revocación. Cambio mínimo, local, sin API paga.

## B4 — Diseño unificado DELTA ONLY
Modificar **solo** `FaroV5HomeReadCoordinator.kt` y su prueba dirigida. Al leer, conservar provisionalmente `(binding, stamp, count)` para propietarios autorizados. Después de recorrer indicadores, consultar nuevamente `authorityStamp` de **cada binding provisional** y agregar a la respuesta únicamente si coincide con su sello inicial. Mantener orden, fail-closed por propietario, excepciones/cancelación y procedencia. No contar pendientes, historia ni guardados sin propietarios reales, ni cambiar `FaroV5HomeReadPort` o navegación.

## B5 — Revisión adversarial PREVIA y pruebas nuevas
- Caso ABCD-A1-01: A/AVISOS lee valor válido; B/PENDIENTES revoca sello de A antes de devolver. **Esperado:** A ausente, B permanece si su autoridad sigue válida. Antes del cambio, A se filtraba indebidamente.
- Caso ABCD-A1-02: A tiene sello de generación 1 al terminar su propia lectura; durante B cambia a generación 2. **Esperado:** A ausente al finalizar lote, nunca se muestra valor anterior.
- Caso ABCD-A1-03: excepción de revalidación final se interpreta como no autorizado; cancelación se propaga, nunca se traduce a 0. Las garantías de ACL del owner y futuras invalidaciones siguen pendientes y no son suplidas por este cambio.
- No tocar wallet, secretos, logs, archivos de respaldo, servicios Android, N015/N031, G5 ni Room. No modificar CI esperado de otras suites; workflow de superficie acepta total variable y exige XML sin errores.
- **Criterio de B6:** tests nuevos JUnit reales 2+ y regresión surface exacta por SHA. **B7:** reportar CI y alcance, no declarar S3.A1 cerrado aunque pase.
- **Límite residual:** revocaciones posteriores al retorno requieren evento auténtico del propietario; revalidación final reduce ventana intra-lote, no crea emisor independiente ni garantía multiproceso.

**Dictamen:** B1–B5 VERIFIED para esta **subunidad de endurecimiento acotada**; B6 habilitable solo mediante permiso separado y preflight, B7 pendiente. Estado S3.A1 global: OPEN.
