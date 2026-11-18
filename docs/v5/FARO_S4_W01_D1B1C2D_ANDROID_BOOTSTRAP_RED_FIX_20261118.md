# D1B1C2D — RED_FIX del primer arranque

## B1
SHA 8de423a7342e6ceefa27f89753be76a540bf1bd0 y guard 44292276241 SUCCESS. S3 A5 44292276235, job 134760211771, compiló y falló 8 veces en startOld (línea 27). Al mover constructor de FaroV5V2Planning8DevHost antes de crear puntero se crean bootstrap/journal y replay-fences, rechazados por requirePristineInitialBootstrapStorage. La auditoría determinista del mismo código encuentra otra incompatibilidad W01: acquire deja su archivo de lock regular vacío en el raíz antes de bootstrap.

## B2
Restaurar orden anterior primero bootstrap, luego construcción Planning8, salvo el gesto Planning8 ya iniciado que de todos modos exige puntero existente. Mantener la ruta de inicialización estricta compartida. Agregar entrada aislada que permite exclusivamente finance9-dev-root.lock regular, no symlink y vacío bajo el lease W01 vigente.

## B3
Reusar contrato de lease y ownership de D1B1C2D. La alternativa de bootstrap antes del lease permite competir entre procesos. Eliminar o trasladar el archivo de lock rompe la exclusión por inode. Se descarta relajar el control de raíz para rutas generales.

## B4
Delta exact2: v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt, v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/ActiveGenerationStorageLocator.kt. La ruta W01 llama la entrada aislada bajo el lease existente; el método Java/Kotlin original conserva la llamada estricta y signature. No editar pruebas, manifiestos ni rutas S3 certificadas.

## B5
Verificar inicializador legacy 8/8, bootstrap W01 real con lock, preservación de journal y rechazo de metadata ajena. Las pruebas Android dirigidas existentes ya arrancan raíz limpia, confirman corte, reabren y prueban segundo lease. Reusar regresiones que activan ambas rutas.

## B7
RED_FIX misma unidad: no se acredita estado Android. CI detectó regresión de primer arranque legacy. Scope adicional limitado a admisión del archivo de lock de esta raíz privada.
