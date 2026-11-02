# FARO V5 S3 — NOCHE_B 20:30 · A1 handoff durable (02/11/2026)

## Autoridad, alcance y estado
S2 CLOSED 8/8; S3 abierta solo en `work/s3-night-20261102`; V2 app OFF; S4 OFF; main/V4.4 sin modificaciones. Autorización hasta **22:00 AR**. N00 PASS A/B con salvedad de error genérico stale CAS y sin carrera simultánea; P00 PASS_SPEC_ONLY, 172 contratos intactos y +8 Guardados CANDIDATE. No pruebas físicas.

## Cambios funcionales reales A1
1. Commit `65628e5829a7a9f593a29f9d04ae2706cae1e091`: `FaroV5HomeReadCoordinator` exige `FaroV5HomeAuthorityStamp` por owner y verifica autorización/generación antes y después de la lectura; cambios de generación, revocación, excepciones o fuente incorrecta => UNKNOWN, no cero falso. Cinco pruebas adversariales nuevas.
2. Commit `b940f6f6f2a8cf326f96ee73c6b4f295ad690ed3`: primera fuente **real** de Inicio: `Avisos` lee el propietario N-032 existente (`AuxiliaryDeliveryStore.readNotices` de la generación activa). Cuenta NoticeIds únicos con `relevance=OPEN` y `attention!=DISMISSED`; `SEEN` permanece visible. No usa estado de entrega OS, no marca leído, no muta fuentes ni activa V2. `FaroV5CompositionRoot` crea binding y `FaroV5BootstrapActivity` lo entrega a `FaroV5SurfaceHost`. Los otros 3 indicadores siguen UNKNOWN. Cinco pruebas JVM nuevas.
3. Commit `823b5fd887559b65ad796ef715e0470525f09f80`: CI dirigido app Home notices, sin reejecutar innecesariamente la suite previa 39/39.

## Evidencia GitHub Actions exacta
- [#41546415283](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41546415283) SHA `65628e5829a7a9f593a29f9d04ae2706cae1e091`, SUCCESS, **44/44 XML**, log `S3_SURFACE_XML_PASS 44/44`.
- [#41546676175](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41546676175) SHA `823b5fd887559b65ad796ef715e0470525f09f80`, SUCCESS, **5/5 XML**, log `S3_HOME_NOTICE_XML_PASS 5/5`.
- Ninguna prueba física ni prueba end-to-end Android de la pantalla con datos reales. Los PASS son **solo** de las suites dirigidas a los SHA indicados.

## Bloqueos restantes A1 (no confundir con cero)
- **Pendientes**: N-031 `attention.read` existe en catálogo, no hay owner real de Asuntos conectado en composición. `ConversationWorkspaceEntry.hasPendingReview` NO equivale a todos los asuntos pendientes: no inventar una suma.
- **Actividad reciente**: N-015 `history.query`/`history.receipt.read` no tiene read owner de lista expuesto; `RealityStore` tiene lookup por receipt ID, NO historial consultable. No inferir actividad por conteo de archivos o mensajes.
- **Guardados**: Saved Views N-018 (4 operaciones oficiales) no son bookmarks MessageRef. Las +8 operaciones siguen candidatas sin bounded reopen; no contar bookmarks inexistentes.
- **Avisos**: source real ya enlazada, pero A4 pantalla de Avisos y una campaña física única siguen pendientes. La proyección actual usa lista de notices en memoria; futura optimización de COUNT SQL bounded podría reducir recursos, pero requiere tests y revisión de autoridad.
- Accesibilidad final, cobertura end-to-end y owner integration X01–X12 no certificadas.

## Investigación adversarial sin repetir trabajo
V4.4 Home podía provocar refresh Radar al abrirse; V5 `home.read` es **solo lectura**, sin refrescos con efectos. Los fallos de owner/revocación/generación no son cero. `NoticeRelevance` y `NoticeAttention` son distintos de estado de notificación Android. El diseño P00 sigue vigente y no se reabre por estos cambios.

## Próximo turno NOCHE_A 21:00
Recuperar HEAD/cola/writer actuales y esta evidencia; no repetir 44/44 ni 5/5 sobre SHA iguales. Mantener A1 **PARTIAL_CI_VERIFIED**, investigar owners canónicos N-031/N-015/Guardados con comparativa V4.4 READ_ONLY; implementar solo autoridad real demostrable. No iniciar A2/A3/A4 por dependencia si A1 no está aceptada. No modificar catálogo 172. Corte 22:00. Métricas por categoría: NO_REGISTRADO; trabajo CI en paralelo no sumar doble.
