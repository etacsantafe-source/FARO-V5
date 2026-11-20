# FARO V5 — W03-G B7 RED_FIX2 · Revision stale after Seen (19/11/2026)

Unidad: `S4_W03_G_GLOBAL_B7_ANDROID_DEV`. Código `3e6a5e7d66268472877058dc9e7d75f15ea8348c`; permiso padre READY `a2049584ca5d446f940cb50413018fef9d2406ac`.

## B7 — RED_FIX (misma unidad G, no certificación)

Guardia del SHA exacto `44649957690` SUCCESS. Compilación W03-E sobre ese SHA `44649957603` SUCCESS. Workflow instrumentado API35 `44649957655`, job `136002075687` **FAIL**, 1/1 ejecutado con 1 fallo y 0 skips. Java/Kotlin, APK y testAPK compilados; falló una aserción Compose a los 25s: `S4W03MattersAndroidTest.kt:91` exige «No hay avisos abiertos.» tras pulsar «Confirmar descarte». Log: `ComposeTimeoutException`. Artifact fallido con JUnit XML `14591421231` ZIP sha256:8561f409722c2834e4525c3bdc03fdcf354630aa0bac322e1eb31fd19de54b84.

**Causa probable reproducible**: test llama `Confirmar visto` y, sin esperar la escritura suspendida ni la siguiente revisión del owner, llama inmediatamente `Revisar descarte`/ `Confirmar descarte`. `FaroV5AvisosSurface` usa `scope.launch` y entrega al host el `ownerRevision` observado. El segundo clic puede portar la revisión anterior y el host CAS debe rechazarlo (comportamiento seguro). No deshabilitar CAS ni fabricar una respuesta PASS.

**RED_FIX** limitado a rutas G: publicar estado observado de atención de Notice desde la proyección canonical sin exponer datos sensibles nuevos, esperar «Visto» tras invalidación de owner antes de «Descartar» en UI test; verificar recibo de descarte y página vacía y asegurar ausencia de permisos OS. Si falla luego, inspeccionar JUnit XML/estado exacto antes de otro cambio. No alterar A-D, main, V2 productiva, ni W03-F opcional. Guard y writer global CAS antes de otra escritura funcional. El G global sigue OPEN.
