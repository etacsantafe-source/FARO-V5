# FARO V5 — M4: preauditoría negativa y alcance de M5 (04/11/2026)
**Unidad propuesta:** `M4_DEV_NEGATIVE_EFFECT_GATES`. **Baseline:** `3b586361740c50558bec129e60f35673adff2717`, rama `work/s3-night-20261102`.
**Estado:** B1–B5 documentados con evidencia de código, comparación y plan adversarial. **B6 NO IMPLEMENTADO; B7 PENDIENTE; PERMIT BLOCKED**. V2 productiva, Auto, Noche y S4 OFF. No modificar main/V4.4 ni activar red/IA/Apply/OS; sin pruebas físicas nuevas.

## B1 EVIDENCIA
- `FARO_V2_M2A_B7_PASS_20261104.md` (blob `edf12d3d4cc245db60fb44fde08c4347a4b12796`): M2A recovery **B7 PASS** para la JVM DEV; código final `d2af3017`, V2 CI #41890189016 **SUCCESS** (M2/M3 4/4, T4 7/7, M1 4/4, M2B 4/4); S3 #41890189058 25/25. La guardia más reciente #41891577331 SUCCESS para HEAD `3b586361740c50558bec129e60f35673adff2717`.
- Manifest DEV `v5/app/src/v2CoreDev/AndroidManifest.xml` blob `0387261d68d3db680cc39100535a1395aa7b2f51`: quita INTERNET, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, REQUEST_INSTALL_PACKAGES, providers/receivers/services y launchers normales; launcher DEV único y Application separada. El `build.gradle.kts` conserva dependencias de Apply, delivery y OkHttp en classpath; **un jar en classpath no demuestra ejecución**, ni es prueba de no-egress.
- `FaroV5V2CoreDevAuthority.kt` blob `7e1179f79915e800905ed6ab23515e8519b1f4af`: únicamente los cinco stores, Chat read/selection/composer, no-op de proyecciones OS y generation switch; no construye Gateway/modelo/Apply. `FaroV5V2CoreDevActivity.kt`: `FaroV5SurfaceHost.install` recibe solo cuatro puertos locales; click explícito y root `noBackupFilesDir`; sin servicios de producción.
- `.github/workflows/faro-v5-v2-core-directed.yml` blob leído en este corte: inspecciona packaged manifests exactos, paquetes distintos, backup false, sin `uses-permission` INTERNET y tres permisos OS, sin componentes adicionales, Application/launcher exclusivos. **Brecha concreta:** inspecciona sólo etiquetas `uses-permission`, no `uses-permission-sdk-23` ni otras formas de permiso; y su filtro `on.push.paths` no incluye cambios a `v5/app/src/main/AndroidManifest.xml` o `v5/surface/**` que pueden afectar el paquete o el host.
- `FaroV5V2CoreDevAuthorityTest.kt`: 4 métodos JVM comprueban five-owner/journal, selección durable y puerto **read** stale tras close, corrupción/fail-closed y USER/ACCEPTED sin mensaje FARO sintético; **NO** demuestra rechazo de selección/composer stale ni muerte real de proceso Android.
- `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`: BLOCKED y M2A B7 cerrado. Falta evidencia M4/M5, no inferir `V2_DEV_CORE_READY`.

## B2 COMPARACIÓN V4.4 / V5 / reuso
- Reusar comparación READ_ONLY de V4.4 documentada en `FARO_V2_RETRO_B1_B5_M0_M3_20261104.md`: V4.4 tenía Internet, boot receivers, FileProvider y MainActivity productiva; **NO** copiar permisos/autoridades. Reusar solo semántica del Chat durable y recuperación; aplicar/reconcile de V4.4 no es bootstrap de V2.
- V5 ya usa manifest aislado, cinco stores, journal, generation CAS, fencing y ports acotados. No agregar otro coordinador, no volver a ejecutar ensayos M1–M3 históricos si un SHA no cambia los contratos.
- La prueba negativa de permisos debe inspeccionar el manifest **empaquetado** (no la plantilla). El lifecycle del paquete aislado no equivale a una instalación física validada.

## B3 INVESTIGACIÓN DE ALTERNATIVAS
- Fuente oficial: https://developer.android.com/build/manage-manifests explica que AGP fusiona manifests de variantes y dependencias, incluso permisos implícitos por `targetSdkVersion`. https://developer.android.com/guide/topics/manifest/uses-permission-sdk-23-element confirma la forma alternativa `uses-permission-sdk-23`.
- **USAR/ADAPTAR:** endurecer aserciones del packaged manifest existente: **deny-by-default** para todas las formas declarativas `uses-permission*`, con diagnóstico del nombre y etiqueta; paquete DEV sin red ni OS. Validar `exported=true` solo en launcher DEV, `allowBackup=false` y ningún componente adicional. Añadir disparadores CI sobre los manifests comunes y código Surface que pueda cambiar la admisión/puertos. Sin sumar dependencias ni permisos.
- **ADAPTAR:** agregar negativas JVM sobre puertos de selección y Composer obsoletos, usando los mismos temp roots/fixtures existentes; exigir que no haya nuevas escrituras tras `Session.close()` ni tras drift del pointer.
- **REFERENCIA/POSPOSPONER M5:** inspección final del APK instalado con PackageManager/aapt y kill/restore Android en campaña física única; JVM no puede certificar proceso, WAL, señales del SO ni runtime real.
- **DESCARTAR:** solo grep de manifest fuente; permitir nuevos permisos por defecto; crear segundo owner/recovery; habilitar Groq/Apply para “probar”; activar `V2_ACTIVE_ENABLED` shipping; duplicar S2; agregar APIs pagas.

## B4 UNIFICACIÓN / DELTA ÚNICO
- Unidad M4 acotada a `.github/workflows/faro-v5-v2-core-directed.yml` y `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthorityTest.kt`. No alterar Activity, Authority, manifiestos, paquetes ni stores **en B6 salvo que una negativa reproducible descubra un fallo concreto y obligue a revisar B1–B5**.
- Orden: (1) verificar permiso M4 explícito en commit documental independiente, con blobs SHA de esta evidencia y `FARO-UNIT:M4_DEV_NEGATIVE_EFFECT_GATES`; (2) preflight PASS; (3) hacer deltas mínimos; (4) CI exacta y XML de los tests actuales+negativos, sin congelar un recuento predeterminado hasta conocer cuántos tests nuevos; (5) B7 independiente, volver a BLOCKED; (6) M5 solo después, con sus gates propios.
- La workflow seguirá `cancel-in-progress:false`; no relanzar CI del SHA `d2af3017` ya PASS. M5 mantiene una única prueba Android física consolidada, no pruebas por subetapa.

## B5 AUDITORÍA ADVERSARIAL PREVIA
1. **Egress/OS:** un manifest empacado que introduzca cualquier `uses-permission*` (incluida `uses-permission-sdk-23`) => STOP, sin permitir `INTERNET`, permisos de notificación/alarma, intents de salida, receivers, providers o services ajenos. No equiparar esto a auditoría dinámica de todo IPC.
2. **Identidad y paquete:** ID de V2 DEV distinto del habitual, launcher único y autorizado, no backup; fallo por cualquier manifest sin cardinalidad única, por permiso nuevo o por componente exportado imprevisto.
3. **Sesión stale:** guardar puerto antes del close; negar read/selection/composer luego del close y pointer drift sin tocar SQLite. No basta con que lance una excepción: verificar que contador/revisión/timeline antes/después no cambien. Si el contrato devuelve una respuesta tipada de rechazo, aceptarla solo con estado inmutable comprobado.
4. **Atomicidad/recuperación:** no mutar puntero V1 habitual; no borrar WAL/journal ni reconstruir base corrupta; cobertura M2A PREPARING/STAGED/VALIDATED B7 ya existe, no reproducirla sin cambio causal.
5. **Control de calidad:** tests dinámicos en `testV2CoreDev` y aserciones sobre manifest final en Actions; capturar SHA/run/XML. La ausencia de red y los `null` ports son defensas, no una certificación integral de OS/IA.
6. **Permisos de desarrollo:** solo M4 mínimo, sin V2 productiva, Auto/Noche, S4, Guardados +8, main/V4.4, secretos ni pagos; writer CAS y guard `seven-block-evidence`. Un futuro permiso READY no se autoaprueba con este documento.
7. **Salida de M4:** PASS solo con B6 CI exacta (manifest, JVM, negativos) y B7 diff/adversarial; si falla algo, RED_FIX explícito o STOP. M5 y el veredicto `V2_DEV_CORE_READY` permanecen PENDING.

## B6 IMPLEMENTACIÓN + CI
**NO EJECUTADO en este checkpoint.** Pendiente emisión de permiso separado y preflight validado.

## B7 AUDITORÍA FINAL
**PENDING.** M4 no se considera cerrado por este informe. M5 queda `OPEN`: validar integración, aislamiento/egress de artefacto y única campaña Android física (tras autorización interactiva); no declarar `V2_DEV_CORE_READY`.
