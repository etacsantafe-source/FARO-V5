# FARO V5 — W03-C Recordatorios civiles · B7 PASS dirigido DEV
Fecha 2026-11-19. Unidad S4_W03_C_REMINDER_CIVIL_DUE. **PASS_SCOPED_W03_C_DEV_JVM_10_OF_10**. No equivale a entrega OS, Review/Apply de recordatorios W03-D, Android W03-E o cierre W03 global.

## B1
Modelo existente ReminderRule + ReminderOccurrence sin reescribir; el nuevo ReminderCivilDue es SOLO proyección read-only, no estado durable ni aviso. W03-A B7 33/33 y W03-B B7 8/8 PASS no repetidos. Estado previo HEAD 3b1bed4029c4e572ff019ed3a4f4c43509222c44, writer RELEASED 1296.
Evidencia específica B1–B5 docs/v5/FARO_S4_W03_C_REMINDER_CIVIL_DUE_B1_B5_20261119.md blob 9f6b5f089681ff38287e47b06c5377e7fe2dd7c3; commit 179428a2e5683fc46a9b6ecd01561e2e5c30b3c4; guard 44553464609 SUCCESS.

## B2
Comportamiento V4.4 de recordatorios conservados y fingerprint de ocurrencia comparado sin importar código anterior. Reutiliza ReminderRule existente, Java Time JDK21 y tests JVM dueño Reminder. ReminderModel y SPI/SQLite no modificados.

## B3
JDK21 ZonedDateTime gap adelantado, overlap offset anterior, YearMonth clamp 31/29/28. Fuentes primarias: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/ZonedDateTime.html y https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/YearMonth.html . Se descartan intervalo fijo 24h/30d, cron remoto, librerías externas y materialización al leer.

## B4
Permit READY commit 784ad7044a6499a0d5d9be15e97bb7b977fa3ddd, guard 44553708628 SUCCESS, tres rutas EXACTAS: ReminderCivilExpansion.kt, S4W03ReminderCivilExpansionTest.kt y faro-v5-s4-w03-c-reminder-civil.yml. Commit funcional 66d63fe189f3cdf5c85ab290d7b3e849e6d197d0, mensaje FARO-UNIT:S4_W03_C_REMINDER_CIVIL_DUE en línea propia. Writer HELD epoch1299 durante escritura y RELEASED antes de CI en commit writer c4f4bf31ae8e2d1ce54d013180ea1dfb16f647dc.
Motor puro ONCE/DAILY/WEEKLY/MONTHLY, ancla civil, 370 días máximo por consulta, página <=128, offset <=10000, ID SHA-256 domain-separated que no depende de título/fecha updatedAt, sin dependencia servidor ni persistencia. Expansión mensual restaura día 31. Zona DST resuelta por Java Time con política documentada.

## B5
Pruebas verificadas (10): T-C01 ONCE/DAILY/WEEKLY; T-C02 clamp fin de mes y restauración del día 31; T-C03 febrero bisiesto/no bisiesto; T-C04 gap NY pasa 02:30 a 03:30; T-C05 overlap NY único a offset temprano; T-C06 disabled y no materialización; T-C07 identidad estable por título/cambio zona distingue ID; T-C08 paginación ordenada sin duplicados; T-C09 entradas y límites inválidos rechazados; T-C10 anchor de 1970 y ventana UTC semiabierta para zona Asia/Tokyo.
NEGATIVAS: no valida scheduler, permisos POST_NOTIFICATIONS, almacenamiento de ocurrencias y ACK, carrera/concurrencia, cambios futuros de tzdb ni comportamiento de app G15. Cambiar zona no reescribe ningún historial persistido; tampoco se ha probado la conciliación de históricos de zona entre reglas y proyecciones. No declarar más garantías.

## B6
Workflow dirigido W03-C GitHub Actions 44554497464, HEAD exacto 66d63fe189f3cdf5c85ab290d7b3e849e6d197d0, estado completed SUCCESS, job 135666777599 SUCCESS. Preflight de tres rutas PASS sobre READY del padre. Build Gradle :owner:reminders:test dirigido SUCCESS (24 tasks). JUnit XML confirmado por logs: W03_C_REMINDER_CIVIL_XML_PASS 10/10, totales (tests=10,failures=0,errors=0,skipped=0). Artefacto w03-c-reminder-civil-xml id 14545545334 digest sha256:b51eb065d90daff68993b24cdbe0174fa84c1647ab91608a033c382098ba375d, no expirado.
Guard W03-C 44554497245 SUCCESS. Regresiones disparadas del mismo SHA: S3 N031 44554497243 SUCCESS y S3 N033B1 44554497353 SUCCESS. NO se vuelve a certificar W03-A/B ni W01/W02.

## B7
**PASS_SCOPED_W03_C_DEV_JVM_10_OF_10** exacto por commit/run/XML/guard/artefacto. Solo unidad de proyección civil. Consumir READY a BLOCKED, poner last_code_review PASS propio, puntero de continuidad W03-D Review/Apply+Aviso local. Sin cambios de main, V4.4, W01/W02/W03-A/B, sin notificaciones OS, pago, nuevas bases, Internet, AI paga o cambios en G15. V2 productiva/Auto/Noche OFF.
El cierre de checkpoint global de este turno requiere guardia documental final SUCCESS y writer remoto RELEASED epoch1300; no declarar antes de verificarlos.
