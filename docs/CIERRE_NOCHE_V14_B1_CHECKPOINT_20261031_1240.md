# FARO V5 — Noche B V1.4 · checkpoint 31/10/2026 12:40 Argentina

**Estado:** S2.A6 OPEN **3/8** · V2 OFF · S3/S4 CLOSED · T4 NOT_RUN.
**Rama:** `work/cierre-noche-v14-20261031`. **Código HEAD:** `3b4ffd48e44f30bfcd70c8fc1e54497590de057d`.
**Writer:** epoch 35, owner B1, liberado por CAS de este checkpoint; confirmar HEAD y writer NONE tras publicar. No abrir otra rama.

## Precheck y recuperación de writer
Noche A terminó realmente a las 12:38:17 Argentina; su lease epoch34 expiró a las 12:37 y dejó `writer ACTIVE` durable. Noche B comenzó a las 12:41:50 y efectuó la espera/revalidación de cinco minutos desde el horario programado 12:40. A las 12:45:30 verificó A `completed`, HEAD inalterado `2b8b745cbe40e4e1121d302c7471f3dce9cd30f2` y lease vencido, sin otro escritor. Recuperó epoch35 por CAS commit `2d553bf2803f39d68a8ee7bc7d949fd10401e7fd`. No se adquirió el writer sólo por el vencimiento.

## Desarrollo funcional F3
- `7203539cd9a0adac84ca4d39dfa0689db3d49509`: la lectura SAF de importación y la verificación del destino exportado ahora abren el proveedor mediante `ContentResolver.openFileDescriptor(uri, "r", cancellationSignal)`, propagando cancelación al proveedor, además de comprobarla durante la copia.
- `fdaf85a3d0c6376f4248b7c1ef4f2909a774e1b1`: corrección del alcance `try/catch` para manejar `FileNotFoundException` comprobada de Java sin fingir PASS.
- `3b4ffd48e44f30bfcd70c8fc1e54497590de057d`: regresión de ida/vuelta cifrada de cinco owners con un proveedor Android real `content://` (MediaStore Downloads, datos sintéticos, borrado al terminar) además del `file://` de prueba; confirma V1 sentinel y V2 OFF. Esto NO equivale al picker SAF humano real ni al restore con journal.

## GitHub Actions (mediciones observadas)
| Run | SHA | Resultado | Cola / pendiente | Ejecución job | Wall total | XML |
|---|---|---|---:|---:|---:|---|
| [#41216435525](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41216435525) | `7203539` | FAILURE | 3 s | 353 s | 356 s | NOT_RUN: compilación Javac |
| [#41216467050](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41216467050) | `fdaf85a` | CANCELLED automático en cola | 69 s pendiente | 0 s | 69 s | NOT_RUN |
| [#41216555083](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41216555083) | `3b4ffd4` | SUCCESS | 266 s | 424 s | 691 s | **5/5 Android emulador, sin errores/skips** |

El primer ROJO fue diagnosticado con logs exactos: `unreported exception FileNotFoundException` en línea 222, corregido en `fdaf85a`; la prueba del último SHA pasó. La cancelación intermedia fue reemplazo automático de un run pendiente por GitHub concurrency; no cancelación manual. Los intervalos CI se superponen entre sí y con el trabajo; **no se suman como horas productivas**.

## Métricas
- Inicio efectivo de B1: `2026-10-31T15:41:50.392413Z`. Registro de cierre: `2026-10-31T16:05:45.562Z`. **Wall observado hasta preparación del checkpoint: 1435 s**. El tiempo de commit/CAS y verificación final se registra aparte como evento, no se inventa.
- Writer adquirido a las 12:45:30.225; intervalo hasta adquisición **219.8 s wall**, incluyendo precheck, espera/revalidación e investigación READ_ONLY superpuesta. No atribuir esos 219.8 s exclusivamente a espera.
- Commits de código a las 12:46:21, 12:46:45 y 12:47:53. CI última terminó 12:59:24.
- Investigación, código, pruebas, espera CI exclusiva, coordinación y tiempo muerto: **NO_REGISTRADO por categoría exclusiva** (no hubo cronómetro de actividades mutuamente excluyentes). No distribuir el wall arbitrariamente. CI corrida en paralelo.
- El turno estaba programado 12:40–13:10. Minutos 20–25 sólo se verificaron resultados ya iniciados. No se lanzaron nuevas implementaciones ni Actions después de las 13:00. El checkpoint y liberación se efectúan dentro de 13:05–13:07.

## Criterios inmutables
1 FIVE_OWNERS CERTIFIED; 2 FBS2_RESTORE CERTIFIED; 3 JOURNAL_WAL CERTIFIED; 4 F1 Android Chat E2E OPEN; 5 F2 activación/recovery OPEN; 6 F3 backup SAF/restore OPEN; 7 F4 integral OPEN; 8 T4 físico NOT_RUN. **3/8**.

## Próximo trabajo concreto
F3 aún necesita un **trusted maintenance restore coordinator** que prepare generación V2 en cuarentena y journal RESTORE PREPARING→STAGED bajo lease global, sin mutar V1; hoy la Activity exige operation/target preexistentes y no crea el journal. Luego integrar picker `ACTION_OPEN_DOCUMENT`, consentimiento específico, autenticación y validación de cinco owners, y probar Android E2E real con oráculo. La prueba MediaStore `content://` ya pasó en el último SHA: NO repetirla sin cambio material. F1 proveedor remoto consentido, F2, F4 y T4 físico continúan abiertos. No iniciar S3/S4.
