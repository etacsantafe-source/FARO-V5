# S3.A4 · Local install read core — B7 RED_FIX focalizada
Fecha AR 2026-11-08. Verificación exacta del commit funcional `50eeed8401db4083eee0885dd671c90e81931b8a`.
## B1
- CI workflow dirigido [#42517567960](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42517567960) **FAILURE**, compilación de Kotlin principal y test correctas; 10 casos T2 ejecutados, **9 PASS / 1 FAIL**. Fallo `pointerAbaAndHostEpochChangeNeverRevive` `IllegalArgumentException` línea 181, prueba-only; XML exacto final no ejecutado. Protocol guard [#42517567816](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42517567816) **SUCCESS** sobre mismo SHA.
- Causa examinada: al modificar puntero real `A→B→A` el `sequence` aumenta. El mismo fixture `f` retenía `pinned` original y acto seguido `f.owner()` pretendía crear una sesión fresca contra ese puntero caduco; `principal(fresh)` es nulo correctamente. El comportamiento de fail-closed es correcto; el ensayo mezcló dos hipótesis.
## B2
- Reutilizar implementaciones certificadas de `FileActiveGenerationPointer` y `FaroV5AndroidSandboxPrincipal`; nunca debilitar el fence ABA ni permitir sesión nueva con secuencia antigua. V4.4 no tiene autorización equivalente que justifique cambios.
## B3
- Android app sandbox https://source.android.com/docs/security/app-sandbox (REFERENCIA para evitar login ficticio).
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html (USAR denegación por defecto).
- Comparadas alternativas: A) permitir reusar el puntero viejo (DESCARTAR rompe ABA); B) separar pruebas host epoch y ABA con distintas fixtures limpias (USAR, sin alterar fuente productiva).
- No se añaden dependencias, APIs pagas, datos o egress.
## B4
- Cambio mínimo a una única prueba: luego del bloque ABA de `f`, crear `val hostCase = Case("host-epoch")` y usar `hostCase.owner()` y `hostCase.hostEpoch` para comprobar revocación permanente del host. NO tocar `FaroV5LocalInstallReadAuthority.kt`, workflow, otras pruebas ni Surface.
## B5
- Sigue siendo negativo el ABA original y revocación host independiente; si algo reanima grant previo = FAIL. No imitar identidad humana con UID; no montar en shipping. El cambio es SOLO fixture; prueba 10/10 exacta en nuevo SHA y guardia obligatorio. No reejecutar suites históricas.
## B6
- **READY para RED_FIX en la misma unidad** `S3_A4_LOCAL_INSTALL_READ_POLICY_CORE`, path exacto `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5LocalInstallReadAuthorityT2Test.kt`, prerequisito: este B7 RED_FIX documentado en un commit previo de docs y `last_code_review` vinculado al SHA rojo.
## B7
- Disposición del SHA `50eeed84`: **RED_FIX** confirmado (9/10). NO PASS y A4 sigue OPEN; la siguiente ejecución sobre el SHA de arreglo será auditada por separado.
