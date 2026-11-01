# FARO V5 — Próximo chat: T4 manual con el usuario

## Continuidad durable

- El usuario pidió **preparar mejoras V1.6, NO activar otra noche, y hacer T4 acompañado en chat nuevo**.
- Repositorio público `etacsantafe-source/FARO-V5`.
- Rama FUNCIONAL congelada para preparar y ejecutar T4: `work/faro-noche-v15-20261101`, HEAD `0750ee530703141016c6cd59a5312758d915ad1c`. No mezclar la rama documental V1.6 para atribuirle nuevos PASS.
- Rama DOCUMENTAL de preparación V1.6: `docs/faro-noche-v16-prep-20261101`. V1.6 no está activa ni tiene automatizaciones.
- F1, F2, F3, F4: `READY_FOR_T4_AUTOMATED` (F1 frozen). F2 21 casos distintos; F3 14/14 SAF por identidad de blobs; F4 63/63 (JVM 36, Android 27) Actions #41277545474 con XML y SHA `0f8e4705632b92215eb6b06a83eb01a5fd939c1e`.
- S2.A6: **3/8 CERTIFIED**, faltan criterios que requieren T4. T4: NOT_RUN. V2 OFF. No S3/S4. No cambios main, V1/V4.4 ni Reality real.
- Plan detallado T4: `docs/faro-noche-v15/T4_SINGLE_CAMPAIGN_PLAN.md`. Decisión de agrupar físicas: `docs/S2_A6_BATCHED_PHYSICAL_T4_DECISION_20261031.md`.
- Todas las tareas A/B/informe de V1.5 comprobadas OFF al preparar V1.6.

## En el chat nuevo

La frase habitual **`Continuar FARO V5`** debe bastar para recuperar desde estos archivos, sin que el usuario pegue un prompt largo. Se entiende que el siguiente objetivo es **T4 manual acompañado**, no activar automatización ni repetir gates automáticos.

1. Recuperar estado real HEAD y T4 plan con GitHub READ_ONLY. Revisar sólo delta nuevo desde SHA congelado.
2. Preflight del Moto G15 **con el usuario**, identificar APK instalada, firma, versión, almacenamiento/backup y entorno de prueba aislado, sin borrar datos, desinstalar ni sobrescribir firmas incompatibles.
3. Preparar **una sola** APK debug reproducible desde el SHA funcional exacto sólo cuando sea necesario y el usuario confirme la intervención física. Registrar SHA-256 de APK, certificado, package y concordancia con el instalado. No generar múltiples APK por F.
4. Ejecutar secuencia T4-0, T4-F1a/b/c, T4-F2a/b, T4-F3a/b/c y T4-F4 en orden con el usuario, una operación por vez; registrar PASS/FAIL/NOT_RUN y evidencia real. Groq real y SAF requieren consentimiento explícito; jamás credencial en logs o GitHub. En cada ROJO pausar la acción afectada y reparar de manera segura.
5. Mantener V2 OFF, pruebas aisladas con cinco stores. No producción, cutover, S3/S4 ni certificar hasta evidencia suficiente.
6. Registrar cierre durable T4 con estados exactos. El usuario no pidió iniciar T4 en el chat de preparación.

## No repetir

No reejecutar F1 27/27, F2 3/3 + 5/5 + 16/16, F3 14/14 ni F4 63/63 sin una regresión real o código afectado. No volver a pedir la decisión F3 opción A, ya aprobada. No crear ni activar Faro Noche V1.6 sin permiso nuevo.
