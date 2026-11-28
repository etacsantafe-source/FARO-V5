# FARO V5 · W05-A2D1B · B7 RED_FIX del fixture Android

## B7 · Evidencia, hipótesis y delta

- SHA `292375db037b3d4f4268fccd924e20b6d4e9e34d`. Guardia 46097302008 SUCCESS. CI 46097302050 compiló el APK y ejecutó el caso API35 con fuente 200%. La prueba falló 0/1 durante preparación, antes de SmartCompra. SQLite devolvió código 14 en el open `READWRITE | NOFOLLOW` de `RealityFinance9StagedFilePort.kt:29`. Artefacto 15244560101 disponible.
- El fixture usa la ruta Android de `noBackupFilesDir` sin resolver sus ancestros. Hipótesis verificable: NOFOLLOW rechaza el alias del directorio privado, aunque el archivo final sea regular. El host de producción DEV ya obtiene `toRealPath()`; la suite Android W04 también usa raíz privada real.
- B6 exacto: resolver el directorio privado de la aplicación con `toRealPath()` antes de crear el fixture en `v5/app/src/w05AndroidTest/kotlin/ar/com/faro/v5/app/S4W05StockReadAccessibilityAndroidTest.kt`. Mantener la ruta relativa al sandbox, UID, política NOFOLLOW, grants y assertions. Repetir CI exacta API35 y XML. Si vuelve a fallar, verificar stack y fase antes de atribuir causa.
- A2D1B sigue READY para la misma unidad, A2D2 sigue pendiente. 3/5 B7.
