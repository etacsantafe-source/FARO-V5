# FARO V5 — V1.4 Noche B · turno 15:40 del 31/10/2026

**S2.A6 OPEN 3/8**. V2 OFF; S3/S4 CLOSED; T4 físico NOT_RUN.

## Writer y recuperación
- Programado 15:40:00 -03; ejecución observada desde 15:41:18.702929 -03.
- Primer precheck 15:41:31; writer anterior Noche A epoch 39 ACTIVE, lease vencido 15:37:00.
- Espera real de cinco minutos y nueva lectura 15:46:20: HEAD sin cambio, writer persistía ACTIVE; ejecución A terminal confirmada 15:37:16.098264.
- Recuperación/acquisición CAS epoch 40 en [15a966d](https://github.com/etacsantafe-source/FARO-V5/commit/15a966d289f4017a348abe1633562a7cc463334f), sin escritores simultáneos.
- Esta misma confirmación de checkpoint solicita liberación CAS a writer NONE; **verificar el HEAD y el writer después de publicar**. No atribuir liberación sin verificación.

## Funcionalidad desarrollada
- [71a5124174a8c73af4f3a8820620a02eadd13591](https://github.com/etacsantafe-source/FARO-V5/commit/71a5124174a8c73af4f3a8820620a02eadd13591): F3 SAF, impedir que una cancelación que llega durante la última lectura del proveedor (EOF) pueda considerarse exportación externa verificada. Antes se comprobaba cancelación sólo antes de leer; ahora también inmediatamente después de cada lectura y antes de confirmar SHA-256. Si la operación se cancela tras abrir el destino, el coordinador mantiene resultado externo incierto, no SUCCESS.
- Se agregó regresión Android `exportVerificationRejectsCancellationArrivingWithProviderEof` y oráculo exacto actualizado de 12 a 13 pruebas.
- No se activó V2 ni se alteró V1, main, repositorio histórico, secretos, visibilidad ni permisos.

## GitHub Actions verificadas
- [#41231091263](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41231091263), SHA `71a5124174a8c73af4f3a8820620a02eadd13591`, job `124625285456`: **SUCCESS**; logs `F3_V14_PINNED_PROVIDER_JOURNAL_PASS 13/13`; Android emulador API35, no físico.
- Creada 18:47:13 UTC, job inició 18:47:15, terminó 18:54:52, run actualizado 18:54:53: **460 s wall**, **2 s cola**, **457 s ejecución efectiva**. Los segundos restantes son coordinación/actualización, no tiempo de trabajo adicional. CI se superpone con la sesión, no se suma dos veces.

## Métricas observadas (UTC)
- Inicio 18:41:18.702929, checkpoint preparado 18:55:18: **839 s de pared hasta checkpoint** (redondeo a segundo).
- Espera por writer desde primera observación hasta recheck: **301 s aproximados según reloj de inicio y recheck**. Hubo investigación durante la espera; NO sumar como trabajo adicional.
- Writer epoch40 poseído desde aprox. 18:46:26 hasta preparación del checkpoint: **532 s aproximados**.
- Desglose exclusivo investigación / escritura / pruebas / coordinación / espera CI / tiempo muerto: **NO_REGISTRADO**. No inventar porcentajes ni considerar la duración de CI como productividad humana independiente.
- La hora de liberación efectiva se verifica por lectura posterior; no se anticipa.

## Siguiente trabajo autorizado
- F3 sigue OPEN: integrar prueba positiva real del selector Android SAF con consentimiento, restauración con journal y verificación de cinco stores, sin cutover. Reutilizar el 13/13 de este SHA, no repetirlo sin cambio.
- F1 E2E proveedor remoto consentido/Keystore y Delivery, F2 activación/recovery Android, F4 regresiones integrales siguen OPEN; T4 físico lo hace usuario. Mantener 3/8 hasta evidencia completa.
