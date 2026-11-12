# FARO V5 · WORK TURBO — IME luego A5 offline sin Groq (12/11/2026)

**Orden humano actual** (prevalece sobre cadenas históricas X03/X09/X10/X01-X12 ya ejecutadas en alcance scoped):
1. `S3_G15_IME_COMPOSER_RESIZE_ANDROID` — CORREGIR bloqueo del composer en G15 Android 15 en APK aislada, CI Android35, B7.
2. `S3_A5_OFFLINE_REVIEW_APPLY_ACTIVITY_SINGLE_ROOT` — INTEGRAR solo flujo local Review→Apply→receipt→Actividad en un mismo root debug, con candidato canónico sembrado **solo en test APK**, CI Android35, B7 scoped.
3. Cerrar/documentar la aceptación parcial offline con matriz X01–X12 (READ_ONLY, no nueva CI si tests ya PASS). **X03 live Groq a futuro, fuera de esta misión.** A5 global, S3 integral OPEN mientras no se demuestre lo restante.

## Fuentes de verdad y documentos
- Base actual: `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`.
- IME B1–B5: `docs/s3-night/S3_G15_IME_COMPOSER_OCCLUSION_B1_B5_20261112.md`. Ejecución: `docs/s3-night/S3_WORK_IME_COMPOSER_EXEC_PACKAGE_20261112.json`.
- A5 offline B1–B5: `docs/s3-night/S3_WORK_OFFLINE_A5_REVIEW_APPLY_ACTIVITY_B1_B5_20261112.md`. Ejecución: `docs/s3-night/S3_WORK_A5_OFFLINE_INTEGRATION_EXEC_PACKAGE_20261112.json`.
- A5 historia de pruebas ya cerradas: `docs/s3-night/S3_A5_7_ACCEPTANCE_MATRIX_20261112.md`.
- G15 física DONE SCOPED no repetir: `docs/s3-night/S3_G15_X06_ISOLATED_NAVIGATION_FIVE_PATHS_PHYSICAL_20261112.md`, `docs/s3-night/S3_G15_A3_A4_PHYSICAL_BOUNDED_RETEST_20261112.md`.
- G15 V3 A3 send uncertain: LOCAL state `$HOME/faro-g15-isolated/a3-physical-v3-state.json`, **Work no accede a Termux ni limpia datos**.

## Contrato de ejecución sin esperas improductivas
1. **Antes de cada mutación**: GET HEAD / writer CAS actual, guard SUCCESS exacto y permiso READY individual. Nunca trabajar con HEAD de este documento si avanzó; revalidar. Writer único; RELEASED al cerrar cada unidad; no apropiarse de writer ajeno.
2. Para IME, si el permiso READY individual fue publicado por el chat manual, correr **preflight por cada ruta** del paquete exacto. B6: código/test/workflow mínimos, Android API35 con teclado real visible/oculto, comprobación binaria manifest/ID/INTERNET, log, XML, hash APK; corregir solo RED_FIX causal, no refactorizar otras rutas. Guard del código en mismo SHA. B7 documentar PASS o NEEDS_PREP/FAIL; consumir permiso.
3. En paralelo durante CI **solo READ_ONLY**, inspeccionar preparación A5 (no habilitar permiso A5 simultáneo al IME).
4. Tras B7 IME PASS y writer RELEASED, recuperar HEAD, comparar A5 preauditoría con diff real, usar los cinco paths exactos del paquete. Si validación B1–B5 sigue vigente, emitir READY **nuevo** en commit de **solo documentación**, verificar guard, ejecutar preflight por cada ruta, recién entonces B6. Si cambió la arquitectura de root/ACL, NEEDS_PREP delta documentado y no programar a ciegas.
5. A5 offline: no mandar HTTP, no llamar Groq, no requerir plan Free ni clave real, no instalar debug APK en G15, no escribir datos fuera de root debug, no cruzar UIDs. La fixture de candidato canónico vive solamente en test APK/emulador, NUNCA hacer pasar esa fixture por respuesta de modelo. Si falta generación/puntero real, NO fabricar sello ni ACL: STOP NEEDS_PREP, registrar menor delta.
6. Revisar artefactos efectivamente (ZIP XML y APK/manifest, no sólo run SUCCESS); guardia `seven-block-evidence` PASS del SHA, B7, checkpoint, writer RELEASED. Tras cada unidad, siguiente READY independiente. NO repetir M01–M05 ni X01–X12 PASS scoped.
7. Con A5 offline B7 PASS, actualizar matriz READ_ONLY y distinguir `offline integrated PASS scoped` de `Groq live X03 OPEN`; no publicar S3/A5 formal CLOSED sin los demás criterios.
8. Si Work se queda sin tiempo/cupo, terminar con checkpoint durable, writer RELEASED, estado de CI y siguiente acción exacta. **No hay ejecución fuera de una sesión activa de Work**; no activar robots/Auto/Noche/Work programado. Nada queda 'ejecutándose por chat' si Work termina.

## Prohibiciones y límites innegociables
`main`, V4.4, S2 y app instalada `ar.com.faro.alpha.v2coredev` intactas; `ar.com.faro.g15isolated.v2coredev` G15 solo usuario/chat manual. Sin APK androidTest en teléfono; `adb install`, `pm clear`, `uninstall`, re-firma privada y ADB físico NO para Work. No activar V2 productiva ni S4. No conectarse a Groq, no APIs pagas, no almacenar secretos, no cambiar catálogo180, sin datos personales; Reality/Evidence y Apply invariantes preservados.

## Informe al usuario
Conservar la señal de fin FARO tras verificar checkpoint y writer. Reportar código SHA, CI y XML PASS, APK SHA, B7, writer status/epoch, próxima unidad, S3 formal 0/5 si no cerró A1-A5. Si bloqueos, no declarar PASS. Termómetro cualitativo.
