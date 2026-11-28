# FARO V5 · W05-A2D1B · B7 RED_FIX de selector Compose

## B7 · Evidencia y delta

- SHA `f9cdb366a042014c8686688cb9ee2eed84528488`, guardia 46116916256 SUCCESS. Run 46116916162, job 140892700653: compilación JVM/APK SUCCESS y API35 FAILURE 0/1. El fixture superó SQLite, la prueba leyó Stock a 200%, saldo 5 e historial en vertical y reabrió luego de rotar. La primera aserción fallida está en la línea 83, búsqueda de saldo dentro del contenedor desplazable elegido por índice 0. Artefacto 15252516123.
- A 200% y en horizontal el shell puede tener lista de navegación desplazable además de los datos. El fixture ya espera que aparezca Yerba. Seleccionar por semántica desplazable con descendiente de Stock o Yerba. Mantener la aserción de visibilidad de saldo e historial, rotación y redacción al pausar.
- Delta exacto de la misma unidad en `v5/app/src/w05AndroidTest/kotlin/ar/com/faro/v5/app/S4W05StockReadAccessibilityAndroidTest.kt`. A2D2 pendiente hasta A2D1B B7 PASS.
