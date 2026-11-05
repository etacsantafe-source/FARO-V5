# FARO V5 - Cierre de alcance S3.A2 (05/11/2026)

## Decision de alcance aprobada
Se registra G5 CLOSED_AUTOMATED_SCOPE y S3.A2 CLOSED_AUTOMATED_SCOPE (5/5 gates), en la variante DEV aislada. No significa Android fisico, V2 productiva ni release.
El gate historico A2-G5 pedia CI + X01-X12 + campana fisica. El backlog de S3 asigna X01-X12 y UNA campana fisica al final a S3.A5. Usuario ratifico la separacion: validacion automatica A2 ahora e integracion final en A5. No se elimina ningun requisito.

## Evidencia B7 reutilizada, sin nuevos ensayos
- G1 CLOSED: docs/s3-night/S3_A2_G1_FORMAL_CLOSED_SINGLE_PROCESS_B7_20261104.md; CI 41957095077 (42/42).
- G2 CLOSED: docs/s3-night/S3_A2_G2_FORMAL_CLOSED_B7_20261105.md; CI 41959201283 (52/52).
- G3 CLOSED: docs/s3-night/S3_A2_G3_FORMAL_CLOSED_B7_20261105.md; CI 41962522487 (Surface 59/59 e in-chat 7/7) y 41962522467 (54/54).
- G4 CLOSED: docs/s3-night/S3_A2_G4_FORMAL_CLOSED_B7_20261105.md; CI 42011878467 (Reality 21/21), 42016712209 (27/27), 42040913961 (V2 core 10/10, T4 7/7).
- G5 automatizado PASS: docs/s3-night/S3_A2_G5_REAL_ACTIVE_HOST_B7_PASS_20261105.md (JVM 1/1 CI 42062273554) y docs/s3-night/S3_A2_G5_ANDROID_EMULATOR_PASS_B7_20261105.md (Android 1/1 CI 42071443306). Codigo bcb38e9ca36da7ae9df387d80bb2e911cbb71533; protocolo CI 42071443729 y checkpoint CI 42072933660 SUCCESS.

## Transferencia a S3.A5, sin declarar PASS
- X01-X12: pruebas adversariales integradas PENDIENTES; ejecutar al integrar S3, sin repetir suites ya aprobadas que sigan validas.
- Unica campana Moto G15 al finalizar S3: busqueda y navegacion fisica de Finanzas, Objetivos y Asuntos, controles de autorizacion y seguridad aplicables. En el telefono esas tablas estaban vacias; no hay positivos fisicos.
- La prueba Android realizada es de emulador con datos temporales y asserts acotados, no certificacion de UI completa ni salida a red. No activar V2 productiva ni alterar datos fisicos. S2 8/8 frozen, S4 OFF, Auto y Noche OFF, catalogo 172 sin cambios.

## Handoff
S3.A2 5/5 CLOSED_AUTOMATED_SCOPE. S3 una etapa A2 cerrada de cinco; resto abierto; ~40% desarrollo estimado historico sin recalculo automatico. Al reanudar S3 en chat nuevo, recuperar HEAD, writer y protocolo y atender siguiente etapa abierta, priorizando S3.A1. No iniciar nueva implementacion en este cierre.

DICTAMEN: B7_RECONCILED_A2_AUTOMATED_SCOPE_PASS; GLOBAL_INTEGRATION_AND_PHONE_DEFERRED_S3_A5.
