# D1B1C2D — alcance del disparador de CI del RED_FIX

## B1
El workflow Android 44292276775 del primer SHA responde a su archivo propio y al test dirigido. Los dos archivos del RED_FIX no lo dispararían.

## B2
Reusar el mismo workflow API35 y exactamente las mismas pruebas de W01.

## B3
ADAPTAR sus filtros de rutas para correr automáticamente cuando cambie el arranque pertinente. DESCARTAR invalidar evidencia de SHA por reejecutar el intento anterior.

## B4
Agregar únicamente a .github/workflows/faro-v5-s4-w01-d1b1c2d-finance9-session.yml los disparadores para v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt y v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/ActiveGenerationStorageLocator.kt. Scope exact3 junto al RED_FIX ya auditado, sin cambiar test, runner ni emulador.

## B5
Último SHA debe emitir el mismo XML 2/2 y la regresión legacy. El resto de flujos preserva sus filtros. ADB autorizado solo en CI.
