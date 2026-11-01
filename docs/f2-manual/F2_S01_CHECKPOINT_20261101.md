# FARO V5 — F2 manual · S01 (2026-11-01, Argentina)

**Estado:** IMPLEMENTADO / CI EN VERIFICACIÓN. **NO CERTIFICADO**. T4 físico NO EJECUTADO.
**S2.A6:** 3/8 criterios certificados; F1 automático congelado y listo para T4. F2 sigue ABIERTO.
**Modo:** manual acompañado en chat. FARO Noche desactivado; sin nuevas automatizaciones ni ejecución nocturna.

## Autoridad y aislamiento
- Repositorio: `etacsantafe-source/FARO-V5`.
- Rama exclusiva de trabajo F2: `work/f2-manual-20261101`.
- Base: rama F1 congelada `work/f1-auto-v15-20261101`, SHA `3c6e82bb402414daed405d1181f285bdbf0cc267`.
- SHA funcional F2 a verificar: `a2bf5d9408ae82875ef5b504b436de5b5220d9d7`.
- No modificar `main`, FARO V1/V4.4, firma, producción, permisos, secretos o flags. `V2_ACTIVE_ENABLED=false`.
- Los escenarios V2 certificados EN TEST son aislados en `noBackupFilesDir` temporal, nunca el puntero activo de aplicación.

## Lote S01
1. Añadida instrumentación Android `FaroV5F2IsolatedActivationRecoveryInstrumentationTest` con **3** casos:
   - corte de ejecución después de publicar puntero temporal V2 y recuperación con cinco SQLite reales;
   - candidato V2 con Workspace ausente bloquea activación sin alterar V1;
   - activo V2 con WAL confirmado permite health read-only sin abrir la compuerta de escrituras.
2. Corregido `FaroV5V2RecoveryRuntimeHealthOwner.canonical`: tras cambio de puntero usa `openActiveV2WithSharedGate` (WAL-aware), no el preflight de staging sellado.
3. Workflow dedicado `faro-v5-f2-manual-directed.yml` para emulador Android API35, exact XML oracle 3/3, sin artefacto APK descargable y sin hardware físico.

## CI — evidencia a verificar
- Ejecución inicial de dos casos, SHA anterior `c29355823dcdbe6dafb74b687306f0421027850a`: Actions [#41267962131](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41267962131). Estado observado: IN_PROGRESS. **No reutilizar como PASS del código actual**.
- Ejecución de tres casos, SHA funcional `a2bf5d9408ae82875ef5b504b436de5b5220d9d7`: Actions [#41268105177](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41268105177). Estado observado: PENDING, oráculo XML todavía NO VERIFICADO.
- Sólo si el workflow termina SUCCESS, XML muestra exactamente 3/3 sin errores/fallos/omitidos y el SHA coincide, etiquetar `F2_S01_AUTOMATED_PASS`. De otro modo, analizar el ROJO sin reutilizar pruebas de otra revisión.

## Continuación
Verificar el resultado de Actions #41268105177 y su XML. Corregir cualquier ROJO comprobado en lotes pequeños; continuar F2 con la activación/recuperación de Android que falte. Mantener F2 OPEN hasta la aceptación integral y pruebas T4 diferidas. No pedir APK ni instalar ninguna prueba en el Moto G15 ahora.

## Verificación manual posterior (2026-11-01)
- [Actions #41267962131](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41267962131), SHA `c29355823dcdbe6dafb74b687306f0421027850a`: SUCCESS, XML exacto **2/2 PASS** (revisión anterior, sin test activo WAL).
- [Actions #41268105177](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41268105177), SHA `a2bf5d9408ae82875ef5b504b436de5b5220d9d7`: **ROJO de compilación** en `:app:compileDebugAndroidTestKotlin`. `BundledSQLiteDriver` no está disponible para el test Android; **las 3 pruebas NO se ejecutaron** y no se ejecutó oráculo XML. No es evidencia de falla de generación/recuperación.
- Corrección confinada al fixture de instrumentación, SHA `736ebd63ecbfb12187a43de8601d1223a4045a20`: usar `android.database.sqlite.SQLiteDatabase`, activar WAL y escribir dato de prueba con Android SDK; sin cambios de código de producción, sin dependencia nueva ni APK descargable.
- [Actions #41273805209](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41273805209), SHA exacto `736ebd63ecbfb12187a43de8601d1223a4045a20`: observado **QUEUED**, no PASS. Verificar finalización y XML **3/3**; si ROJO, resolver causa real sin reabrir F1.
- F2 **OPEN**; S2.A6 **3/8**; V2 **OFF**; T4 físico **NOT_RUN**. No activar FARO Noche ni avanzar S3/S4.
