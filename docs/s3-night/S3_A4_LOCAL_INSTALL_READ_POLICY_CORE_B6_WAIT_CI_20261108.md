# S3.A4 · local-install read authority CORE — B6 WAIT_CI
Fecha AR 2026-11-08. Writer original epoch567 RELEASED; epoch568 adquirido por CAS para esta unidad. Base exacta `1c2bed55`.
- B1–B5: `docs/s3-night/S3_A4_LOCAL_INSTALL_READ_POLICY_CORE_B1_B5_20261108.md` (SHA blob d36da9c252076a74e2798e5ee4440303ef33f547).
- Permiso PREVIO READY en `67bca412dddf722179bdec97cfe7f29dfec595d3` con 3 rutas exactas y anterior B7 certificado.
- B6 código `50eeed8401db4083eee0885dd671c90e81931b8a` (dos Kotlin, un workflow dirigido); commit mensaje `FARO-UNIT:S3_A4_LOCAL_INSTALL_READ_POLICY_CORE`, CAS branch correcto.
- CI dirigida #42517567960: **IN_PROGRESS**, XML 10/10 aún **NO VERIFICADO**.
- Guardia protocolo #42517567816: **IN_PROGRESS**, no inferir PASS.
- Otros workflows automáticos del SHA: #42517567817 APK, #42517567827 Home, #42517567832 V2 core; estados IN_PROGRESS, no añadir pruebas por inercia.
- B7 NO realizado. La política es CORE in-memory con fixtures JVM, **NO Application Android certificado, NO montaje Shipping ni Productiva V2**, no entrega Actividad/Pendientes global.
- No abrir código siguiente hasta B7 PASS o RED_FIX documentado; si falla el mismo SHA, inspeccionar logs y corregir solo hipótesis exacta.
- Writer liberar temprano epoch569 mientras CI corre; coordinación separada de CI. S3.A4 OPEN, S3.A5 física NOT_RUN.
