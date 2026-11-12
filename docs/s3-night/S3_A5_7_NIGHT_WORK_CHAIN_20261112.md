# FARO V5 — Cadena Work nocturna por objetivo, 2026-11-12
Rama work/s3-night-20261102. Esta cola NO activa robot ni un trabajo de fondo: el usuario debe iniciar sesión Work manualmente. El modo Work puede seguir durante la sesion autorizada mientras tenga recursos; al interrumpirse guardar checkpoint y liberar writer.

## Resultados que NO se repiten
X03 U01/U02, X09, X10, X01 B7 PASS scoped; A3 M01–M05, X04/X05 Android, X07/X11 JVM y X06 Workspace Android, X08 canonical partial y X12 lifecycle, evidencias scoped anteriores. X03 global no live Free verificado, X01/02/06/08/12 global y A5 global siguen OPEN. No inflar.

## Encadenamiento
A. X02 S3_A5_7_X02_AUXILIARY_AVISOS_AUTHORIZED_DEV_READ: B1–B5 docs/s3-night/S3_A5_7_X02_AUXILIARY_AVISOS_B1_B5_20261112.md, paquete docs/s3-night/S3_A5_7_X02_WORK_EXEC_PACKAGE_20261112.json, cinco rutas. Tras guard y READY individual, Work ejecuta preflight 5 rutas, B6, CI Android API35 2/2, XML/APK, B7, consume permiso, checkpoint y writer RELEASED.
B. X06 S3_A5_7_X06_THREE_REALITY_OWNER_SEARCH_ANDROID: auditoria B1–B5 docs/s3-night/S3_A5_7_X06_THREE_OWNER_SEARCH_B1_B5_20261112.md, paquete docs/s3-night/S3_A5_7_X06_WORK_EXEC_PACKAGE_20261112.json, dos rutas SOLO test/workflow. Tras X02 B7, Work revalida base/contratos, emite permiso READY independiente y prueba Android 2/2 con 3 owners Reality8, XML/APK/B7. No reejecutar Workspace Android.
C. X08 ACL mid-read/ABA/owner-down: triage docs/s3-night/S3_A5_7_X02_X06_X08_X12_DELTA_TRIAGE_20261111.md. Resolver hook/fixture mínimo, B1–B5, scope individual, guard/READY, B6/CI/B7.
D. X12 UI adaptación/recreación integral: solo tras X08, B1–B5 de escenarios faltantes, permiso separado, CI API35/semántica B7. No duplicar pruebas parciales históricas.
E. A5 aceptación integral + UNA campaña física Moto G15 al final. X03 live Groq Free exige intervención humana; no pagar ni habilitar INTERNET en V2 CORE DEV.

## Protocolo para no detenerse por documentación
Siempre recuperar HEAD, writer, permission, AGENTS, pointer; salto de unidades B7 PASS previas. Un solo permiso READY / writer CAS. Si está READY Work programa B6+CI; si siguiente aún no READY, completa solo B1–B5 que faltan, publica preaudit+guard y READY propio; no usa permiso ajeno. Liberar writer durante CI. Si CI roja, auditoria RED_FIX antes del siguiente commit de codigo; no repetir runs identicos sin causa. Si ruta extra necesaria, STOP NEEDS_PREP concreto con delta, sin editar scope. Tras B7 cerrar permiso y publicar checkpoint nuevo. Ninguna API paga, main/V4.4/S2 intactos; V2 productiva/S4/Auto/Noche OFF; G15 final.
