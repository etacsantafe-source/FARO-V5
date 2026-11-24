# FARO V5 · W05-A1B3C2A · RED_FIX B0 pre-auth Evidence2 · 2026-11-24

Unidad S4_W05_A1B3C2A_FBS2V8_EVIDENCE2_SQL11_STAGED_REPLAY_DEV, rama work/s3-night-20261102. No stock, V2 productiva, pagos, Auto/Noche.

## B1 — Evidencia XML real de CI

Código RED_FIX previo 5a99cdfcb32c296b872938280d221740f6fb0e4b, guardia 45512507305 SUCCESS, CI 45512506948 job 138863534575 FAILURE tras compilar Kotlin. Artefacto XML auténtico GitHub 14976906809, ZIP 1967 bytes y SHA256 recalculado 2ca4068bc73a50d5bba807771ee5157a39ef0b3b1cdeaf49b9a213f4641f6743. Dos suites originales: Evidence2 nuevo tests6 failures5 errors0 skipped0; Evidence1 anterior tests10 failures0 errors0 skipped0. Las cinco fallas Evidence2 se explican por Blocked(reason=TARGET_NOT_QUARANTINED) en vez de STAGED VERIFIED, y los tests de crash no llegan a su fase. Causante exacto identificado por lectura: FaroV5Household11V8BaseReplay.restoreUnderLease() inicia con requireTargetProfile(target,expectedActive,null), que usa rama ELSE Evidence1. Un target STAGED9 Commercial Evidence2 válido es rechazado **ANTES** de autenticar FBS2v8, aunque el método posterior YA admite Evidence2 autenticado. C2A nunca activa un pointer. No confundir diez regresiones Evidence1 PASS con certificación Evidence2.

## B2 — Reuso y comparación

El B0 A1B3B0 B7 PASS cubre Evidence1 y contenía adaptación posterior al perfil2, pero la comprobación inicial null no estaba cubierta por una prueba real Evidence2 SQL11. No repetir su restauración de cinco stores, no tocar C1A/C1B/C1C ni V4.4. Las matrices V4.4 P02/P03 de backup no trasladan autoridad de producción. El flujo de FBS2v8, B0 después de autenticar, H10/H11R y SQL11 queda intacto.

## B3 — Opciones evaluadas

A ADOPTAR: antes de decodificar el archivo, verificar SIN escritura el target STAGED sellado bajo pin/gate, comprobando los dos perfiles existentes con los validadores tipados del GenerationStoreGraph. Debe coincidir **exactamente uno** (Evidence1 xor Evidence2), luego autenticar v8 y exigir igualdad exacta con el perfil certificado por el archive (comprobación posterior del B0 sin cambios). A no degrada Evidence2 ni asume perfil del ciphertext no autenticado.
B DESCARTAR: eliminar toda comprobación pre-auth o inferir perfil de un byte del contenedor no autenticado.
C DESCARTAR: elegir Evidence1 como fallback si falla Evidence2; bloquearía restauración typed o permitiría downgrade inseguro.
D DESCARTAR: modificar C1A/C1B, permisos productivos o schema SQLite activo.

## B4 — Dos rutas exactas RED_FIX

1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8BaseReplay.kt : sustituir pre-auth requireTargetProfile(...,null) por requireOneSealedFinance9Profile que comprueba los perfiles 1 y 2, exige XOR y revalida puntero exacto/host; mantener la comprobación estricta post-auth del perfil original, sin hacer ninguna escritura nueva.
2. .github/workflows/faro-v5-s4-w05-a1b3c2a-evidence2-stage.yml : agregar la ruta B0 a la lista de disparo del workflow, conservar parent preflight exact1/2/3 y las pruebas dirigidas de seis Evidence2 + diez Evidence1 con XML original.

No modificar otras rutas, tests existentes, manifest, main, V4.4, GraphOwner, C1B/C1C. Si se identifica dependencia adicional real, STOP y permiso nuevo.

## B5 — Auditoría adversarial

R01 Evidence1 STAGED9 conserva B0 y 10/10 regresiones. R02 Evidence2 STAGED9 pre-auth coincide con un solo perfil sellado; auth FBS2v8 con schema2 typed fija perfil de forma vinculante después; seis tests Evidence2 deben pasar. R03 cualquier stage con cero perfiles válidos o validación ambigua bloquea; no creación, migración ni selección de pointer en pre-auth. R04 no falsear typed terms ni convertir ABSENT→UNKNOWN, KNOWN→UNKNOWN. R05 host revocado, pointer ABA, wrong key, nonce reemplazado, sidecar, symlink, foreign target siguen fallando sin Apply. R06 crash SQL9/10/11 reentra solo tras pin original. R07 suite JUnit XML original 16/16 sin failures/errors/skips; guardia protocol real sobre parent READY exact2.

## B7 — Disposición de código precedente

SHA 5a99cdfcb32c296b872938280d221740f6fb0e4b → **RED_FIX_EVIDENCE2_B0_PREAUTH_WRONG_PROFILE**, NO B7 PASS. CI real 45512506948 mostró Evidence2 1/6 y Evidence1 10/10 PASS, se mantiene la evidencia de esos resultados. El permiso vigente de la misma unidad requiere B7 RED_FIX con este documento y READY exact2 antes de publicar código; luego ejecutar CI y XML. El cierre de C2A y el cutover C2B/C2C son posteriores e independientes. A1 global permanece OPEN y stock OFF.
