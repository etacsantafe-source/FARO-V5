# FARO V5 — Noche B2, integración real de pruebas A5 sin superponer CI

Fecha 02/11/2026, turno B. Rama exclusiva `work/s2-chat-finish-night-20261102`. Base CAS `38cbd4169e8583530aa16b40bf0f2dd923506f61`; fuente A2 de pruebas preparadas `dcc1f9653e1bfba727b3626fa77c04a5835da35e`.

## Código publicado

Se integraron los cuatro blobs preparados y revisados de A2: manifest de instrumentación, proveedor ContentResolver sintético local, tres pruebas Android de lectura acotada/cierre, duplicados y URI no-content, y workflow con oráculo exacto de cinco pruebas Android (F1 Chat/Review/Apply/Delivery/restart 2 + A5 reader 3). Son pruebas de app aislada, sin red, sin claves, sin consentimiento real y sin modificar V1.

## CI y límites

Actions #41390549337 sobre `a630634` seguía IN_PROGRESS en `assembleDebug` al control de B2. Para no crear ejecución superpuesta, este commit lleva `[skip ci]`. **No hay PASS todavía** de A5 ni de la integración del código A2. Cuando termine la ejecución anterior, provocar una sola ejecución dirigida del workflow actualizado, verificar XML A5 JVM 9/9 y Android 5/5 y compilar APK con identidad y firma comprobadas.

S2.A6 OPEN 6/8, #4 F1 y #8 T4 pendientes. Writer NONE al cierre, V2 OFF, main intacta, S3/S4 cerradas. No solicitar prueba física hasta APK verificada. Si el run previo falla, leer errores reales y corregir antes de otra CI.
