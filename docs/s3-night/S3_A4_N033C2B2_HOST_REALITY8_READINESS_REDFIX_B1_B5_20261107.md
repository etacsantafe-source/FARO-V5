# FARO V5 — N033C2B2 RED_FIX de aislamiento de tests — B1–B5
Fecha: 2026-11-07 AR. Unidad EXACTA `S3_A4_N033C2B2_HOST_REALITY8_READINESS`. La unidad previa no está certificada por completo: su B7 fue RED_FIX. Este expediente es documentación anterior al commit correctivo; no habilita V2 ni reinterpreta los resultados verdes.

## B1 — Evidencia y criterio de aceptación
Código `d4cbfb1a1c26c9a44e625029ad1fced24126b969`: probe de Reality8 readiness compiló y sus 14/14 XML pasan `#42389272813`; guard `#42389272718` SUCCESS. Workflow previo N033C1 `#42389272772` FAIL por conteo exacto: test `FaroV5ActiveReality8ReopenT2Test` 8/8 realmente pasados, script esperaba 6/6. `Gradle BUILD SUCCESSFUL` y cero fallos/errores/skip; evidencia en `docs/s3-night/S3_A4_N033C2B2_HOST_REALITY8_READINESS_B7_RED_FIX_20261107.md` B7. Los 2 casos nuevos en clase histórica elevan 6→8; 3 casos nuevos en clase host histórica elevan 3→6. **Aceptación**: restaurar los test históricos EXACTAMENTE (6 y 3), ejecutar los cinco nuevos en clases separadas (2 y 3), dirigir CI cuatro XML (6+2+3+3=14), requerir cero skips/failures/errors y CI N033C1 heredada verde; producción intacta.

## B2 — Comparación V4.4, reutilización y opciones
La semántica V4.4 de recordatorios/planes y reutilización V5 está documentada en `docs/s3-night/S3_A4_N031_REMINDER_MONTHLYPLAN_TYPED_CONTRACTS_B1_B5_20261106.md`; ninguna versión de V4.4 implementa estos scripts de pruebas. Se reutiliza prueba JUnit Kotlin y workflows GitHub existentes, sin portar código ni datos legacy. Opción A **ADAPTAR**: crear dos clases de tests nuevas y devolver las originales a byte idéntico del padre `69a0af9cab0077677b4efa4625a3574e1f760916`; nuevo directed ejecuta las cuatro sin modificar histórico N033C1. Opción B **DESCARTAR**: cambiar workflow N033C1 para que espere 8 porque equivale a cambiar prueba certificada de otra unidad. Opción C **DESCARTAR**: borrar/skippear tests nuevos para volver a verde: pierde negativos ABA/schema. Opción D **DESCARTAR**: ignorar el run fallido porque Gradle pasó. S5 conserva cleanup/version consolidation.

## B3 — Investigación de soluciones ajenas (vigente y adicional)
- GitHub Actions workflow syntax https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax : los filtros por rutas disparan workflows independientes que pueden validar la misma clase. **USAR** aislamiento por suite/clase y preservar contadores de workflow previos en vez de alterarlos.
- JUnit Jupiter tests https://docs.junit.org/current/user-guide/ : clases y métodos independientes permiten agrupar negativos nuevos sin cambiar las pruebas históricas. **ADAPTAR** a Gradle `--tests` por clase y JUnit XML exacto.
- Proyecto Android real Now in Android https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md : tests de capas/CI separados; **REFERENCIA** para separar scope, sin copiar código ni añadir dependencia/licencia.
- SQLite WAL https://www.sqlite.org/wal.html y Room migrations https://developer.android.com/training/data-storage/room/migrating-db-versions : preservar negativa de esquema7 activo y ABA; **USAR** tests nuevos existentes; jamás hacer reset/migración destructiva para volver CI verde.
- OWASP object-level authorization https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/ : readiness no es ACL/Review ni autoridad de usuario. **USAR** deny-by-default en pruebas, alcance readiness-only.
No nuevas APIs, dependencias, permisos, gastos ni licencia. Los enlaces documentan mecanismos; hallazgo de conteo se basa exclusivamente en log exacto del run.

## B4 — Delta correctivo de cinco rutas EXACTAS
1. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5ActiveReality8ReopenT2Test.kt` RESTORE bytes históricos 6/6 del padre anterior.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveGenerationGraphOwnerT2Test.kt` RESTORE bytes históricos 3/3 del padre anterior.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5ActiveReality8HostReadinessT2Test.kt` NEW, portar dos pruebas readiness schema8/7/ABA desde d4, sin duplicar en original.
4. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveGenerationGraphOwnerPlanning8ReadinessT2Test.kt` NEW, portar tres pruebas de certificación y schema8/7 desde d4.
5. `.github/workflows/faro-v5-s3-a4-n033c2b2-host-reality8-readiness-directed.yml` UPDATE para ejecutar cuatro clases, leer exactamente 6+2+3+3, total 14, 0 skips/fail/errors.
**No** tocar `FaroV5GenerationStoreGraph.kt`, `FaroV5ActiveGenerationGraphOwner.kt`, N033C1 workflow o implementaciones fuera del scope. New tests compile using existing public constructors and owner staging; all store roots are @TempDir.

## B5 — Auditoría adversarial anterior al commit y veredicto
- Reloj/A-B-A: el snapshot ID+sequence sigue verificado y el probe puede retornar false sin promulgar autoridad. Negativo schema7 nunca migra activamente; no borrar tests de corrupción. Readiness true nunca concede ACL, Review, Apply ni habilita shipping V2.
- Los dos XML históricos deben volver exactamente a sus valores previos, sin reescribir scripts antiguos o relajar conteos. Los nuevos 2+3 agregan pruebas, no reemplazan las originales; total N033C2B2 14.
- Peligro de aislamiento: clases nuevas requieren helpers/fixture propios; verificar Kotlin compilation de ambos módulos. Un error de compilación requiere B7 RED_FIX mismo unit antes de otro commit; no afirmar PASS por guard verde.
- Revisar HEAD/lease CAS y `FARO_PROTOCOL_WORK_PERMIT.json` READY exact paths + B7 RED_FIX del código anterior. No mezclar permiso/docs y código en commit; usar SHA y XML/guard. Writer liberado para CI, sin físico ni dependencias de usuario.
**Dictamen B5: READY_SCOPED_RED_FIX_ONLY**, condicional a permiso documental y guardia; no declarar N033C2B2 cerrada hasta B7 PASS independiente.
