# FARO V5 — T4 SAF, endurecimiento y automatización (01/11/2026)

## Autoridad y alcance
- Solicitud humana: corregir riesgos identificados y automatizar pruebas para reducir la intervención en Moto G15.
- Rama exclusiva: `work/t4-saf-automated-hardening-20261101`, desde `67f816b81a9c2ae7979b05448e38dff18ad5498c` (`work/t4-saf-integral-20261101`).
- Nada en `main` ni `src/main`, V1 intacta, `V2_ACTIVE_ENABLED=false`, S3/S4 no abiertos, ninguna FARO Noche activada. GitHub Actions dirigida (CI) no es FARO Noche.
- Se conserva APK V4 ya verificada físicamente; las nuevas correcciones debug NO son esa misma APK, no declarar PASS físico de V5 ni actualizar SHA descargable sin nuevo build.

## Remediaciones debug preparadas
1. La fuente sintética se genera y se prepara **después** del consentimiento nuevo del selector.
2. Se incorporan cinco fixtures sintéticos conocidos **no vacíos**: Reality (perfil ficticio), Commercial Evidence (precio ficticio), Web Evidence (fetch ficticio sin llamada de red), Auxiliary (aviso ficticio) y Workspace (conversación + receipt). Nunca se lee ni modifica FARO habitual.
3. Al restaurar, comparar cada uno de los cinco `backup.snapshot()` del target contra el archivo FAROBAK2 autenticado, más aserciones de contenido no vacío de cada owner; journal VALIDATED, V1 sentinel y V2 OFF. Un simple esquema válido vacío no equivale a este PASS.
4. Limpiar directorios privados abandonados `t4-saf-integral-*` al inicio de proceso (solo directorios de prueba, no SAF externo). No seguir enlaces simbólicos; fail-closed si falla cleanup.
5. La exportación interrumpida sigue pudiendo dejar un documento externo incompleto: la UI lo advierte, **no** borra automáticamente un URI elegido por el usuario.
6. No se resuelve aquí el bloqueo productivo de `FaroV5FiveStoreSafConsentActivity` por V2 OFF ni la propiedad de S5. Eso necesita integración separada y certificado F3 futuro.

## Automatización dirigida
- Workflow: `.github/workflows/faro-v5-t4-saf-automated-hardening.yml`, dispara al cambiar su archivo en esta rama. Solo tests, sin release ni publicación de APK.
- JVM **7 pruebas esperadas**: 4 de gate post-picker + 3 de limpieza (selección, idempotencia, symlinks). Oráculo exacto XML, 0 fallos.
- Android API35 **25 pruebas esperadas**: F2 recuperación (5); F3 consentimiento (6); F3 proveedores/cifrado/journal (8); T4 Activity (6).
- Las 6 T4 Android incluyen 4 callbacks UI (recreación, cancelación, URI inválida y resultado sin documento), 1 roundtrip real `content://` con contenido de los cinco owners y 1 archivo corrupto sin cambio de puntero. La inyección controlada del callback **no** sustituye un picker nativo humano.
- Los tests de proveedor usan MediaStore en emulador. No se automatiza consentimiento humano del teléfono ni se activan stores reales.

## Ejecuciones y significado
- #41377701187, SHA `f62f83a25de9ead9ac52be2b44d4dfcad1b82270`: FAILURE en compilación Kotlin de fixture Workspace por método interno `ConversationWorkspaceStore.create`; los tests Android no llegaron a ejecutarse, no es fallo de restauración.
- #41377974819, SHA `120269f34e8dc128a8bcedc84fe3ee4b80c4b384`: mismo origen de error porque fue disparado antes del arreglo, no se debe contar como un test de la implementación corregida.
- #41378210451, SHA `fcbb9ca6eb1b48f434c47e5cfb6d77712f3b73cf`: pipeline correctivo disparado; **PENDIENTE DE RESULTADO VERIFICADO** al registrar esta nota. El fixture cambió a `store.backup.restoreOrVerify(...)`, puerto público; verifica no vacíos para cinco owners.
- Nunca adjudicar 7/7 ni 25/25 antes de consultar los XML reales y estados GitHub Actions.
- Estado de certificación global permanece `S2.A6 3/8`, T4 general OPEN, V2 OFF. La exportación V4 tiene captura positiva y restauración V4 fue VERDE reportado por usuario, no confundir con la V5 modificada.

## Intervención humana estrictamente necesaria al final
1. Una instalación consolidada desde un APK **nuevo** solo si CI verde; comprobar firma/hash.
2. Un recorrido físico breve del selector SAF y consentimiento explícito con datos sintéticos (una exportación y una restauración), si se quiere validar la nueva V5 en Moto G15. Casos negativos y proveedor corrupto se cubren automáticamente con el emulador.
3. Las pruebas de Groq real o permisos productivos requieren intervención humana y política de egress; jamás capturar credenciales.
4. No se autoriza reactivar V2 para producir un verde de T4.

## Seguimiento
Si CI falla: identificar el paso exacto y el error en logs/XML; reparar **solo el código de debug/test afectado**, luego disparar una nueva CI reproducible sobre el SHA preciso. Si CI verde: registrar el run, nombres de tests y counts; no repetir 27 tests físicos anteriores ni crear una batería de APK por cada falla.

## Ampliación de autorización humana — cierre S2 (01/11/2026)
El usuario dijo: «Dale todos los consentimientos que necesite. Quiero terminar S2». Se interpreta como autorización para **desarrollar, corregir, ejecutar suites/CI y documentar S2 en ramas aisladas**, sin pedir confirmación por cada commit. **No** sustituye la interacción obligatoria del usuario ante Android SAF, credenciales Keystore, egress externo, firma/borrado de datos, habilitación de V2 ni decisiones que alteren datos reales. Mantener `main` intacta y S3/S4 sin abrir. No activar FARO Noche programada por interpretación extensiva de ese mensaje.

### Evidencia F4 reutilizada
- #41277545474, `work/faro-noche-v15-20261101`, SUCCESS en jobs `f4-jvm-36` y `f4-android-27`; define oráculos XML de **36 JVM + 27 Android**. No se solicitará repetición por mera formalidad.
- Comparación GitHub de `work/faro-noche-v15-20261101` con `work/t4-saf-automated-hardening-20261101`: **ahead 55 / behind 0**, 16 archivos modificados, confinados a workflows, docs y código/tests debug; **ningún `src/main` diferente**. Este dato no certifica F3/T4 físicos por sí mismo.
- El bloqueo real de F3 productiva (V2 OFF) permanece. Solución correcta: culminar certificación de contratos y consentimiento en cuarentena, luego decisión explícita y gate completo de activación; no usar flags productivos para pasar una prueba.

### Resultado CI de depuración a esta altura
- #41378210451: JVM 7/7 OK; Android NO RUN porque el workflow dejó `cd v5` y `./gradlew` en comandos de shell distintos. Log `/usr/bin/sh: 1: ./gradlew: not found` (error CI, no código Android).
- Corrección workflow a `script: cd v5 && ./gradlew ...` sobre SHA `11070a8c3e6d77f4f1eb58a6d9314ea1d4a60966`.
- #41378896809: EN CURSO cuando se escribió este checkpoint. No declarar Android PASS ni V5 físico PASS sin XML/cierre confirmado.

## Android emulado — hallazgo verdadero y corrección puntual

**Actions #41378896809, SHA `11070a8c3e6d77f4f1eb58a6d9314ea1d4a60966`:** JVM **7/7 PASS** (oráculo exacto); emulador ejecutó **25/25 casos**, **24 PASS / 1 FAIL**. Falló únicamente `FaroV5T4SafHardeningInstrumentationTest.mediaStoreProviderRoundTripVerifiesNonemptyWorkspaceAndAllFiveOwners`.

Log de error: `NoSuchMethodError: No static method readString(Ljava/nio/file/Path;)Ljava/lang/String;` en `FaroV5T4SafIntegralActivity.exportSelected`, al leer el marcador V1 sintético (Android API35). Importante: el provider sí recibió la operación antes de la aserción, pero NO se puede contar PASS. Los otros 24 casos fueron exitosos en ese SHA.

**Corrección:** las dos llamadas debug `Files.readString(path)` fueron reemplazadas por `String(Files.readAllBytes(path), Charsets.UTF_8)` en `FaroV5T4SafIntegralActivity.kt`, sin alterar producto. Commit de corrección `d5e178a5e34fd6c85fc5f9afe45d9a6e201aa446`.

**Reintento:** Actions #41379627980, SHA `0a2c1f4e8bcfa04820f4ede7513c041da936cfac`, disparada; resultado **no verificado todavía** al escribir. El workflow impide reintroducir `Files.readString(` en este harness. No dar 25/25 ni F3 productiva por cerrados hasta obtener XML final.

## Cierre automatizado V5 — 32/32 PASS y artefacto único publicado

- GitHub Actions **#41379627980**, SHA funcional `0a2c1f4e8bcfa04820f4ede7513c041da936cfac`: `completed/success`, JVM **7/7**, Android API35 emulado **25/25**, XML exacto PASS. Incluye provider MediaStore real `content://`, cinco owners con fixtures no vacíos, reconstrucción/cancelación de Activity y rechazo de archivo corrupto.
- La regresión Android API35 por `Files.readString` fue corregida antes de esta ejecución y no reapareció.
- Release workflow **#41380364442**, SHA `61d8579e73c61b9839f54f615f6402fb4aa8f6a0`: `completed/success`; verificó por GitHub API identidad y éxito del run de 32 casos; verificó que no hay modificaciones de `v5/` desde el SHA probado; compiló, verificó `aapt` y firma debug y publicó **una sola APK**.
- Release prerelease: https://github.com/etacsantafe-source/FARO-V5/releases/tag/faro-v5-t4-saf-auto-v5-20261101
- APK: `FARO-V5-T4-SAF-AUTO-V5.apk`, `61,903,795` bytes, SHA-256 **`2619ce5ecc9ff1a34f9b460506147742022ef0eab14991e40869c8743afdd789`**.
- Android package **`ar.com.faro.alpha.t4saf5.v5dev`**, `versionCode=66`, `versionName=5.0.0-dev-debug`, `minSdk=29`, `targetSdk=36`.
- Debug signing certificate SHA-256: `789d0405571e36602777b28bea180e20190ca3d59c68d0119c1b51b9abfec3a8`.
- Asset URL: https://github.com/etacsantafe-source/FARO-V5/releases/download/faro-v5-t4-saf-auto-v5-20261101/FARO-V5-T4-SAF-AUTO-V5.apk
- SHA/firma y manifiesto inmutable: https://github.com/etacsantafe-source/FARO-V5/releases/download/faro-v5-t4-saf-auto-v5-20261101/T4-SAF-AUTO-V5-MANIFEST.txt

### Siguiente fase, limitada a hardware real
Instalar este APK **aislado** en Moto G15 y verificar **una exportación + una restauración SAF** con consentimiento nuevo y contenido sintético no vacío. No volver a ejecutar JVM/emulador en teléfono; no sustituir el APK V4 que ya tenía export/restore VERDE, ni quitar FARO habitual. Como dos paquetes tienen etiqueta parecida, abrir mediante `adb shell am start -n ar.com.faro.alpha.t4saf5.v5dev/ar.com.faro.v5.app.FaroV5T4SafIntegralActivity` para garantizar identidad.

**T4 global NO CERTIFICADO / S2.A6 3/8 / V2 OFF.** El PASS de un harness sintético, aunque sea físico, no equivale a F3 productiva. Los otros criterios 4–8 deben evaluarse contra sus contratos congelados, sin cambiar estados por inferencia. GitHub `main` sigue protegida sin cambios productivos.

## Campaña física SAF V5 positiva — Moto G15, 02/11/2026 ~00:08–00:15 ART

### Evidencia y trazabilidad
- APK identidad: `FARO-V5-T4-SAF-AUTO-V5.apk`, SHA-256 `2619ce5ecc9ff1a34f9b460506147742022ef0eab14991e40869c8743afdd789`, paquete `ar.com.faro.alpha.t4saf5.v5dev`, release `faro-v5-t4-saf-auto-v5-20261101`.
- El usuario mostró salida de Termux `FARO-V5-T4-SAF-AUTO-V5.apk: OK` (hash validado). La primera instalación no encontró dispositivo ADB; después de reconectar por depuración inalámbrica, el usuario comunicó `Ya abrió` tras el comando que instalaba y abría específicamente el componente V5. **No hay salida literal `Success` de esta segunda ejecución**, aunque se comprobó apertura de la Activity.
- **Captura física aportada por usuario** en Moto G15 aproximadamente a las 00:12 ART: Activity `FARO T4 - SAF integral aislado`, checkbox desmarcado y mensaje exacto `Falta consentimiento NUEVO después del selector.` → gate posterior al selector observado. La imagen no contiene URI ni nombre de archivo; no se sube al repo.
- Luego de marcar consentimiento y confirmar EXPORT, el usuario comunicó `Verdw` (interpretado como `Verde`) siguiendo la secuencia de exportación de `FARO-T4-V5-PRUEBA`. **`T4_V5_SAF_EXPORT_PHYSICAL=PASS_REPORTED_BY_USER`**. No hay captura del texto final completo.
- Luego de elegir el respaldo sintético para restaurar y conceder un consentimiento NUEVO, el usuario comunicó `Verde`. **`T4_V5_SAF_RESTORE_PHYSICAL=PASS_REPORTED_BY_USER`**. No hay captura del estado final completo ni log externo; registrar con esta granularidad.
- **Interpretación del verde de restore acotada al código V5 autenticado**: la rutina `restoreSelected` solo devuelve `VERDE` tras verificar FAROBAK2, journal `Validated`, igualdad semántica por cinco owners con archivo autenticado, presencia de datos no vacíos por owner, puntero anterior intacto, sentinel sintético V1 intacto y V2 OFF. Esta inferencia depende de que el verde reportado corresponda a esa ruta en esta APK; **no confundir la inferencia de código con una captura literal**.
- GitHub Actions automático `#41379627980` completed/success, **7/7 JVM y 25/25 Android emulado**; release `#41380364442` completed/success y firma/hash validados. Estos PASS son independientes de las observaciones físicas, no se suman como casos extra inventados.
- **Resultado de la campaña:** SAF real Android picker V5 aislado, export+restore principal **VERDE reportado por usuario**, con consentimiento posterior demostrado en captura. No solicitar repetir los mismos pasos de V4/V5 sin detectar una regresión concreta.

### Evaluación dirigida contra criterios congelados S2.A6
| # | Criterio congelado | Evidencia disponible | Decisión segura |
|---|---|---|---|
| 1 | FIVE_OWNERS | Certificación previa | CERTIFIED, sin cambio |
| 2 | FBS2_ISOLATED_RESTORE | Certificación previa | CERTIFIED, sin cambio |
| 3 | JOURNAL_REPLAY_WAL | Certificación previa | CERTIFIED, sin cambio |
| 4 | F1_ANDROID_CHAT_E2E | F4 36 JVM / 27 Android emulados; 27 físicos instrumentados PASS reportados + Groq debug/revocación PASS reportados, pero faltan receipts físicos claros de la cadena Chat→Review→Apply→Delivery/Keystore | OPEN; evidencia fragmentaria, no declarar certificación global |
| 5 | F2_ANDROID_ACTIVATION_RECOVERY | F2 5 Android automatizados dentro #41379627980 + F4 integración 27/27 anterior; pruebas de recuperación y puntero aislado | READY_FOR_T4 / OPEN formal; revisar criterio físico exacto antes de firmar |
| 6 | F3_FIVE_STORE_BACKUP_SAF_CONSENT | F3 14 Android automatizados dentro #41379627980; SAF V4 físico y SAF V5 **con fixture no vacío** reportados verdes, journaling/sentinels aislados | PASS de escenario aislado; OPEN formal para flujo productivo `V2_ACTIVE_ENABLED=false` |
| 7 | F4_INTEGRAL_REGRESSIONS | #41277545474, 36/36 JVM + 27/27 Android, SHA ancestro productivo sin `src/main` distinto | Gate automático PASS probado; criterio OPEN en estado congelado hasta valoración/registro formal de cierre |
| 8 | T4_PHYSICAL | SAF V5 verde reportado; 27/27 Android físicos instrumentados y Groq debug/revocación reportados anteriormente. No se tiene evidencia completa observada de campaña T4 F1→F4 integrada sobre un único APK ni de la transición de producción | EN_CURSO; no afirmar T4 global PASS/CERTIFIED |

**Contador durable sin cambios: S2.A6 = 3/8 certificados, OPEN.** Esta tabla **no descalifica** los PASS técnicos: explicita qué está aprobado y dónde sigue faltando autoridad/evidencia. F4 automático no se vuelve a correr por formalidad; F3 productiva no se habilita para fabricar un verde. S3/S4 no abiertos; `main` intacta, sin cambios a `src/main`.

### Decisión siguiente
El recorrido SAF sintético está hecho; **no pedir más selecciones SAF por ahora**. Priorizar recuperación de evidence/receipts físicos previos F1/F2/F4 contra el contrato de criterio 4/5/7 y delimitar si el criterio T4 exige una prueba integradora final. Si sigue faltando una operación productiva, revisar su autoridad técnica y ofrecer un plan seguro separado, nunca activar V2 silenciosamente.
