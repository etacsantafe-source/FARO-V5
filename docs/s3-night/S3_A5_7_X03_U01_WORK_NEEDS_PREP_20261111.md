# X03 U01 — WORK NEEDS_PREP: modo explícito no atraviesa el host

Fecha: 2026-11-11 Argentina. Unidad `S3_A5_7_X03_U01_EXPLICIT_CONSULT_DEV`.
Estado: **NEEDS_PREP_SCOPE_PROPAGATION**, B6 NOT_STARTED, B7 NOT_RUN. No hay commit funcional ni nueva certificación Android.

## Evidencia recuperada
- HEAD remoto y local: `20cf981a9c080e972da93a2bb6ecd3017223a8cc`.
- Guardia de ese SHA: run `43092664255`, completed/success.
- Writer inicial RELEASED epoch 737; adquirido por CAS epoch 738 para este cierre documental; liberar antes de CI.
- Permiso original READY, blob `ec47c3a68d4fb541f62743dffb0a490a778b8192`, unidad y cuatro rutas exactas verificadas.
- Preflight real conjunto de las cuatro rutas: `FARO_PROTOCOL_PREFLIGHT_PASS S3_A5_7_X03_U01_EXPLICIT_CONSULT_DEV 20cf981a9c080e972da93a2bb6ecd3017223a8cc`.
- Esto valida el permiso documental; no certifica la suficiencia del alcance para el producto.

## Causa precisa y delta material
La Activity no monta `FaroV5ActiveChatScreen` directamente. Ambos accesos reales (apertura y regreso desde Guardados) llaman `FaroV5SurfaceHost.install`. El host llama `FaroV5AdaptiveShell`, que llama `FaroV5ShellScaffold` en ShellNavigation, que finalmente monta ActiveChatScreen. Ninguno de esos tres puntos ofrece/transmite un modo de solicitud al asistente.

El B4 preparado exige opt-in EXPLICIT_CONSULT en DEV y default AUTOMATIC_ON_SEND para callers existentes. Añadir solamente un parámetro al Screen y probarlo directamente deja DEV usando el default: sería una prueba aislada sin integración del modo en el producto. La vía de propagación explícita requiere tres rutas adicionales fuera del permiso. No se afirma imposibilidad de todo diseño imaginable; se identifica la brecha de la solución preparada y se detiene antes de adoptar una alternativa no auditada.

| Ruta adicional candidata | Blob revisado |
| --- | --- |
| `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt` | `626266d393249078df31e8123afd247aff221992` |
| `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AdaptiveShell.kt` | `fde0c1bee1d0bb04b01322f1075a8abcc94c0f00` |
| `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` | `911f7e5ae8510ae351eb68da35e835acfb065a63` |

No se sustituirá el shell completo desde la Activity, no se inferirá autoridad del packageName y no se agregarán registros globales/marcadores ocultos para esquivar el alcance. Esas alternativas requieren su propia auditoría y añaden riesgos de lifecycle o regresión de navegación/Guardados.

## Preparación mínima de desbloqueo (sin permiso nuevo emitido)
1. Auditar solo este delta B1–B5, conservando los B1–B5 vigentes de la unidad. Propuesta: propagar un parámetro opcional tipado de modo por las tres rutas anteriores, default compatible, opt-in en los dos montajes DEV. Alcance candidato total: siete rutas; NO autorizado por este documento.
2. Verificar llamadas posicionales/JVM overloads, default S2 y key de lifetime; mantener owner/puertos canónicos y ausencia de provider DEV. No cambiar manifest, Gradle, Apply ni almacenamiento.
3. Precisar fixture con Workspace real dentro de la ruta androidTest autorizada, puerto fake test-only, y negativas de duplicado/cambio de conversación/recreación/revocación. No declarar que esta viabilidad ya está probada.
4. Emitir paquete y permiso READY revisados en commit documental separado, después de verificar la guardia. Ejecutar preflight para las siete rutas antes de B6.
5. B6 → SHA funcional → liberar CAS → Android API35 dos casos + XML/APK/manifest y guard mismo SHA → B7 independiente. No repetir suites certificadas sin delta.

## Dictamen B7 del turno
**NOT_RUN / NEEDS_PREP**, no RED_FIX de código: no se publicó código ni existe SHA funcional nuevo que auditar. Android, compilación, XML y APK: NOT_RUN. No disparar CI Android sin cambio funcional útil. Permiso ahora BLOCKED por esta brecha; last_code_review anterior se conserva. X03 global OPEN. Cero B7 PASS nuevos.

## U02 — preparación inicial READ_ONLY, no B1–B5 completos
Ruta corregida del host existente: `v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5F1ConsentedQuarantineHost.kt`, blob `14517a5716d10202a10a1f4a76aa641c3b28a713`. El documento original lo citaba junto a fuentes main; no copiarlo como si fuera un host del build DEV activo.

Fuentes leídas: ese host, `FaroV5QuarantinedV2ChatHostBinding.kt`, `FaroV5TrustedModelGatewayFactory.kt`, `v2CoreDev/AndroidManifest.xml` y conexiones de SurfaceHost. El host F1 obtiene owner de consentimiento/Keystore y crea gateway solo si admitido; binding verifica que la generación staged NO sea activa, incluso después de abrir. Factory revalida consentimiento y referencia de credencial antes del acceso al secreto y durante reasoning. Estas verificaciones existentes son evidencia de reuso, no certificación nueva del modelo remoto.

U02 debe seleccionar host aislado distinto de V2 CORE DEV; candidato a investigar: host debug F1 existente en generación staged, con gesto explícito y sin activación del pointer. Falta verificar variantes/manifiestos finales, UID/storage, lifecycle/revocación, recuperación PROCESSING sin replay, política de costo cero y criterio de prueba con modelo real. No se promete disponibilidad gratuita actual de proveedor ni se hace llamada externa. No se emite READY U02 ni se declara IA real completa. Orden preservado X03 → X09 → X10 → X01 → X02 → X06 → X08 → X12.

## Límites y métricas observadas
main/V4.4 intactas; V2 productiva, S4, Auto/Noche OFF; DEV sin INTERNET; físico NOT_RUN final únicamente. S3 ~40% estimado heredado, 0/5 cierres formales; no cambiar porcentajes por documentación. Duración y tokens: no medidos. Bloqueo documental concreto, no incidente de CI; first_pass/redfix count no aplicable. Transporte Git local permitió lectura pero rechazó push por falta de credencial; se usó conector GitHub con expected_sha para CAS y publicación, sin solicitar ni exponer credenciales.
