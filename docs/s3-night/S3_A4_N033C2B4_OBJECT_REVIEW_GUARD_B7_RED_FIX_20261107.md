# FARO V5 — S3.A4 N033C2B4 · B7 RED_FIX (auditoría de seguridad)
Fecha 2026-11-07 AR. Unidad `S3_A4_N033C2B4_OBJECT_REVIEW_GUARD`; commit funcional auditado `d2ac220d58023d225a82f90f74cf44a4f11ed8f1`. La guardia obligatoria #42394955153 SUCCESS del mismo SHA; CI dirigida #42394955364 **IN_PROGRESS al detectar el problema**, NO se declara PASS de tests en este dictamen.

## B7 — Hallazgo independiente: TOCTOU de Review humano
`FaroV5Planning8ApprovedObjectGate.hasCurrentApprovedObjectReview` usa `TrustedConversationApplyReviewPort.load(context)` antes de llamar al puerto de ACL. Después de consultarlo dos veces y de revalidar puntero, hostReady y descriptor, NO vuelve a consultar el Review en Workspace. Entre `load` inicial y la segunda consulta ACL, la persona puede descartar el Review o el owner puede cambiar generación/revisión. El método podría devolver `true` basado en una aprobación que ya no es la autoridad actual. No basta revalidar un epoch ACL para validar la decisión del usuario.

**Dictamen B7: RED_FIX** por falta de relectura de Review canónico post-ACL, aunque no exista wiring productivo. Este punto no permite activar V2 ni un propietario de ACL real. Mantener código inicial, CI y SHA para rastreabilidad.

## B7 — Corrección acotada y prueba adversarial obligatoria
- Añadir al final del guard una segunda `workspace.load(context)`, rechazar ausencia, cambios de revision/state, cambio de bytes de envelope o snapshot, y conservar chequeo de puntero/hostReady/descriptor/ACL. El resultado sigue siendo solo un Boolean efímero de preflight; NO permiso de ejecución ni cache reutilizable.
- Agregar a la clase NUEVA de test un caso nativo: al primer `epochOrNull` el dueño Workspace descarta un Review `APPROVED`; aun si ACL sigue reportando el mismo epoch, la compuerta **DEBE devolver false**. No modificar suites históricas. Actualizar únicamente el conteo XML del workflow de esta unidad si corresponde.
- Exigir B1–B5 correctivos y permiso READY nuevo en commit documental, con `last_code_review` de este SHA = RED_FIX antes del segundo commit funcional; mantener los tres paths iniciales. No ocultar/fusionar intentos. El estado final seguirá PASS_SCOPED hasta ACL real por objeto, enforcement dentro de transacción, Undo/retry y S3.A5 física.
- No borrar datos test, no consolidar versiones (S5), no activar V2, no tocar S2/S4/main/V4.4/172 catálogo.
