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
