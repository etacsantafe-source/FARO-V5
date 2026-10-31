# FARO V5 F1 — Noche A2 (19:20–19:50 Argentina)

- Programado **19:20**; ejecución efectiva **19:23:24**; writer epoch3 adquirido **2026-10-31T22:23:42.735Z**; checkpoint/liberación **2026-10-31T22:45:31.874Z**.
- Rama única `work/f1-noche-v14-20261031-1820`, base `ec0f896c`; último código `ea1886fc`. **F1 OPEN**, S2.A6 **3/8**, V2 OFF. No F2/F3/F4/S3/S4, main ni histórico.
- Tablero: Groq **PROBADO sintético**, Chat **IMPLEMENTADO**, Review/Atomic Apply **IMPLEMENTADO (último SHA CI ROJO)**, Delivery **IMPLEMENTADO**, integración **EN DESARROLLO**. Ningún subhito COMPLETO.

## Código y artefacto
- `3ba93f8`: corregida aserción de respuesta JSON estructurada persistida y reabierta; resuelve ROJO B1.
- `4c01b866`: CI F1 Android Keystore consentimiento en emulador y generación de APK debug con checksum y artefacto efímero.
- `dffe785a`: botón debug-only de prueba real Groq con texto fijo y consentimiento/Keystore; no toca Workspace, Reality ni activa V2.
- `ea1886fc`: cableado de callback de propuesta Review desde respuesta durable en CompositionRoot, **no compiló en Java** por interop Kotlin value class; no certificar.
- APK debug verificada **solo en SHA `dffe785a`**, artefacto [faro-v5-f1-debug-apk #13060368194](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41248158188/artifacts/13060368194), ZIP 23,597,857 bytes, expira 2026-11-03 19:39 Argentina. Incluye APK y SHA256. No es F1 completo ni último código.

## CI / XML / ROJO
- [#41248017466](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41248017466) SHA `3ba93f8`: **SUCCESS**, XML exacto 19/19 (10 transporte, 5 fábrica, 2 Review/Apply, 2 host). Cola 2s, job 211s.
- [#41248085970](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41248085970) SHA `4c01b866`: **CANCELLED** en espera por nuevo push, 59s pendiente, sin tests; evitar pushes sucesivos.
- [#41248158188](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41248158188) SHA `dffe785a`: **SUCCESS**, XML 19/19 JVM + **2/2 Android emulador Keystore/consentimiento**, APK `assembleDebug` SUCCESS y artefacto subido. Cola 102s, jobs 282s y 428s; CI paralelo no sumar como tiempo humano.
- [#41248313958](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41248313958) SHA `ea1886fc`: **FAILURE** en `FaroV5CompositionRoot.java:737`: `cannot find symbol method propose(ConversationId,ConversationTurnId)`; Kotlin `@JvmInline` probablemente cambia el nombre JVM de `propose`. Android job SKIPPED, XML final NOT_RUN. Diagnóstico de causa documentado, **sin corrección por límite minuto20**.

## Próximo B2
B2 19:50: FIRST fix Java/Kotlin value-class callback compile ROJO #41248313958 by exposing Java-callable no-value-class-argument callback() from FaroV5ModelReviewBridge and passing modelReview.callback() to FaroV5HostAssistantTurnDispatcher; compile and run directed CI once on exact SHA, inspect XML. THEN gate CompositionRoot.applyActiveReviewedPlan with V2_ACTIVE_ENABLED and extend existing Android Compose F1 test to produce structured model JSON -> durable Review -> human approval -> Atomic Apply -> denied-permission Delivery/reprojection without pre-staging review. Keep V2 OFF. Groq live and physical Android WAIT_HUMAN.

## WAIT_HUMAN y métricas
- Groq real **NOT_RUN**. Solo usar clave personal en Android y cuenta Free sin facturación; la ficha oficial de Groq también muestra tarifas para planes pagos. No enviar clave a GitHub ni chat. La prueba manual es transporte solamente.
- Dispositivo físico T4 **NOT_RUN**. APK disponible para prueba de transporte, no para afirmar F1 PASS. Guía: `docs/f1-noche/F1_PHYSICAL_TEST_GUIDE.md`.
- Wall observado al checkpoint: **1328s**; writer retenido **1309s**. Espera writer **0s**. Desglose exclusivo de investigación/código/tests/espera CI/coordinación/inactividad: **NO_REGISTRADO**; no inventar. CI se solapó con trabajo y no se suma a wall humano.
- Writer NONE solicitado por CAS del HEAD exacto; confirmar con lectura post-CAS antes de declararlo liberado.
