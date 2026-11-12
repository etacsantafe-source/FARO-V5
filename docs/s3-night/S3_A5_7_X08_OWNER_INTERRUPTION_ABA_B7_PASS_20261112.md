# FARO V5 — X08 owner-interruption / durable pointer ABA, B7 PASS acotado
2026-11-12 AR. Unidad `S3_A5_7_X08_REAL_OWNER_INTERRUPTION_ABA_ANDROID`. Preauditoría `docs/s3-night/S3_A5_7_X08_OWNER_INTERRUPTION_ABA_B1_B5_20261112.md` (blob `98c438867b7c77eaaa8c1dbab35b1c18aa9db449`) + RED_FIX tipadas (último blob `6b296bd8c18bd7f4bf93546b642210ba2cb8fd53`). Último código válido `35ea27e171a48a53ec38dadb73dc1f582ac46c12`; ninguna modificación de implementación productiva en el último commit, solamente:
- `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X08OwnerInterruptionUiTest.kt`
- `.github/workflows/faro-v5-s3-a5-7-x08-owner-interruption-android-directed.yml`

## B6 — Código y CI final
Work publicó el ajuste por puente compilado `FaroV5SurfaceHost.install` en `35ea27e171a48a53ec38dadb73dc1f582ac46c12`, sin añadir dependencias Compose ni tocar owner/ports productivos. Run de prueba real API35 [43155126432](https://github.com/etacsantafe-source/FARO-V5/actions/runs/43155126432) job `130973270369`: COMPLETED SUCCESS, *todos* los pasos compile, inspección manifiesto/APK, instalación emulador, instrumentación, XML y artefacto SUCCESS. Guardia del SHA de código `43155126431` SUCCESS. Guardia del checkpoint precedente `43155130552` SUCCESS.

No ocultar errores previos: run `43153903965` FAILURE por ABI Compose `NoSuchMethodError`; run `43154265872` CANCELLED como superseded tras corrección; **no contarlos como PASS**, no repetirlos ni reinterpretarlos como green. Su causa y alternativa aprobada están registradas en `docs/s3-night/S3_A5_7_X08_COMPILED_SURFACE_BRIDGE_B1_B5_RED_FIX_20261112.md`.

## B7 — Auditoría independiente real
Artefacto GitHub `13916181288` descargado y leído con ZIP parser independiente; SHA256 recalculado `3f8da59dc5c68404c5a5a4754d06d0300fc280b97928a2c8f530ad4f7787dca9` idéntico al digest GitHub. Diez entradas, CRC/ZIP íntegro. Fuente `x08-owner-source-sha.txt` EXACTA `35ea27e171a48a53ec38dadb73dc1f582ac46c12`.

XML `x08-owner-android-junit.xml` parseado independientemente `tests=3 failures=0 errors=0 skipped=0`, exactamente estas tres pruebas, sin duplicados:
1. `x08FinanceGrantRevokedAfterCanonicalReadYieldsPartial`
2. `x08OwnerClosedAfterSnapshotCannotPublishOrNavigate`
3. `x08DurablePointerABARejectsRealityHitWithoutRegrant`

Log crudo: cada test un START=1 y un SUCCESS=0; `OK (3 tests)`, `INSTRUMENTATION_CODE: -1`. No falso positivo de XML construido solo desde nombres; correlación XML-log aprobada.

APKs extraídas del ZIP y SHA256 recalculados frente al checksum publicado:
- `app-v2CoreDev.apk`: `c4f04f7aee1ac4231a0adca2b1b466bbfca52c2085d103cda5d86ea537204e5b`
- `app-v2CoreDev-androidTest.apk`: `f0c6cf48a0e8ba124c291e9b0f1886a9545df8d8052309018acfc818669cc9e2`
Badging: `ar.com.faro.alpha.v2coredev` y `ar.com.faro.alpha.v2coredev.test`; permisos y manifiesto del APK DEV inspeccionados, sin `INTERNET`, `sharedUserId` o `FaroV5X03U02IsolatedAiActivity`. Firmas SUCCESS en CI (no recalculadas localmente).

Diseño de evidencia: tests usan grants por owner, snapshot de Finance CANÓNICO, revocación entre adquisición y segundo check, cierre real de owner, pointer A→B→A con secuencia durable. Revalidación prohíbe fuente vieja, evita falsa cobertura COMPLETE y rechaza regrant implícito. No alterar contratos de Search. Evidencia de código y asserts está en test/workflow de `35ea27e171a48a53ec38dadb73dc1f582ac46c12`; no implica verificar toda matriz de acceso.

Registro independiente JSON: `docs/s3-night/S3_A5_7_X08_ARTIFACT_VERIFICATION_20261112.json`.

**DICTAMEN B7: PASS_SCOPED_X08_REAL_OWNER_INTERRUPTION_ABA_ANDROID**, con 3/3, XML 3/0/0/0, hashes/APK/guard SOURCE SUCCESS, y permiso X08 consumido BLOCKED. **X08 GLOBAL / A5 GLOBAL permanecen OPEN**; X12 solo después de checkpoint documental y nuevo permiso. Ni APK final del Moto G15, ni prueba remota Groq Free, ni V2 productiva autorizadas. No volver a correr pruebas previas certificadas sin cambio material.
