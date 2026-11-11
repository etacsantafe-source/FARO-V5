# X09 — preparación READ_ONLY tras X03 U02
Fecha 2026-11-11 Argentina. Base inspeccionada `fcfbe151f55561a70acc9fb552760d96cea30235`. **Inventario específico / investigación parcial, NO B1–B5 completos, NO READY ni código autorizado.** U02 no puede reutilizarse como permiso para exponer Review. X03 global conserva pendiente de proveedor live gratuito y futura integración autorizada.

## Evidencia reutilizable, sin nuevas pruebas
- `FaroV5ActiveChatReviewSession.kt` blob `ba8ce6cb11688cf51e2e5cc714ff5bfaeacbe1e2`: puerto canónico generation/pointer/ready, gate único, solo Approve/Discard; no Apply.
- `FaroV5ModelReviewBridge.kt` blob `c73bbb23a789e668fa0730abc7390e934030f232`: propuesta a partir de USER ACCEPTED + FARO COMPLETED durables; payload no confiable, identidad/fingerprint/dependencias generados por host, allowlist actual profile.update_declared. No llamarlo planificador genérico de todas las operaciones.
- `FaroV5PlanReview.kt` blob `3c528b23aae7c1806f1144adbcee41a5e371e6f7`: ya soporta `reviewExpanded` + `onOpenReview`, aprobación explícita y Apply opcional separado. Reutilizar.
- Brecha concreta: `FaroV5ActiveChatScreen.kt` blob `6435b762e8e37d99abc7eabce4f629cb4b272dc9`, montaje línea 243 `reviewExpanded=true`, sin callback onOpenReview: la integración actual no verifica el gesto Revisar cambios. No certificar X09 por presencia del componente.
- Tests de referencia leídos, NO relanzados: `WorkspacePlanReviewT2Test.kt` blob `6463230bef6cfefb3695199e82191680f942ba1b` (persistencia, CAS, fingerprints, generación y aprobar no ejecuta); `FaroV5ChatToReviewedApplyFlowT2Test.kt` blob `2a5673cbd8ddf5e50dd28d57691a660981831efa` (bridge/review/receipt histórico, no aceptación X09 nueva).

## Fuentes primarias consultadas durante espera CI
- USAR https://developer.android.com/develop/ui/compose/side-effects : recomposición no debe ejecutar decisiones; eventos explícitos y trabajos ligados a composición. Traducir a negativas: abrir review no aprueba, recomponer/recrear no decide/reenvía.
- USAR https://developer.android.com/develop/ui/compose/state-hoisting : UI recibe estado y eventos desde owner; expansión visual no es autoridad durable. No duplicar Workspace/owner ni almacenar aprobación solo en remember.
- Falta investigación específica de alternativas/proyectos reales y B2 de alcance X09. No se declara B3 completo por estas dos lecturas. Sin código externo importado ni dependencia nueva.

## Decisiones que debe cerrar la preauditoría independiente
1. Seleccionar host X09 y root propios o integración autorizada; preservar U02 sin Review/Apply y S2 frozen. No abrir Internet en V2 CORE DEV ni reutilizar la Activity S2 con Apply. Comparar wiring nuevo vs helper de bootstrap compartido, sin duplicar owners ni refactorizar unidades cerradas por inercia.
2. Comparar modo explícito opt-in en superficie (default histórico S2 intacto) contra cambio global (riesgo S2; descartar salvo evidencia nueva). Si se elige opt-in, inventariar TODA propagación: SurfaceHost/AdaptiveShell/ShellNavigation/ActiveChatScreen + host/tests/workflow/manifest que efectivamente se necesiten. No repetir el hueco de scope U01.
3. Definir qué candidato se acepta: perfil allowlisted o conjunto de operaciones ya aprobado. No agregar contratos/catálogo por interpretación del texto del modelo.
4. Auditar revisión/fingerprint/revisión de Reality/contexto/generación, descarte, doble confirmación, lifecycle, stale callback y persistencia. X10 Apply/receipt sigue separado; aprobar no modifica Reality, no llama Apply y no fabrica receipt.
5. Criterios de prueba candidatos: candidato canónico requiere Revisar cambios antes de confirmar; confirmación/discard solo Workspace, Reality sin cambios; CAS obsoleto/generación sustituida niega; recreación no reabre autoridad ni reejecuta; sin Apply. Fixture sintética solo en test APK, sin Groq/credenciales reales y sin simular respuesta remota positiva.
6. Publicar B1–B5 exactos, matriz de rutas y permiso READY independiente en commit documental, guard y preflight TODAS las rutas. Antes de publicar cualquier commit funcional: trailer FARO-UNIT en **línea independiente** y gate local sobre SHA construido. Después CI exacta/API35/XML/APK/B7. Sin código X09 en este turno.

Orden preservado X03 → X09 → X10 → X01 → X02 → X06 → X08 → X12 → aceptación → G15 final. Este paquete solo habilita continuar preparación documental, no B6.
