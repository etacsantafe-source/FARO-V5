# FARO W01 D1B1C2C — B7 del grafo Finance9 único por lease

Unidad `S4_W01_D1B1C2C_FINANCE9_DEV_SINGLE_GRAPH_MOUNT`. Código `ed0134c3d22d9f667a45d33d6696947ee9522273`, EXACT4. Guard READY 44230028633 SUCCESS.

## B6
El runtime DEV verifica puntero completo, journal y replay fence antes de abrir schema9 exacto. Retiene un solo grafo por lease; intentos de apertura/recovery simultáneos son rechazados. `isCurrent` revalida metadata sin otra conexión. El grafo se cierra antes del lease.

## B7
**PASS SCOPED** para un único grafo activo DEV JVM. [CI dirigida 44230368221](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44230368221), [regresión C2B 44230368128](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44230368128), [M2 44230368146](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44230368146) y [guard 44230368145](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44230368145) SUCCESS sobre el SHA exacto. Comprobados los artefactos 14394149741 (5/5, XML SHA-256 `2bc4c8d14178a3e6b28a9822ece6c9dfebcff0c8f8f8e4aa141f928d649f51f4`) y 14395480560 (8/8 de regresión, XML SHA-256 `ec8d6d10ef60667524a1567de5abc30c71221779ad779ca945ced67ca84e0fc5`) con ZIP y XML descargados.
- Se comprobó grafo saludable exacto9, rechazo de apertura doble y recuperación concurrente, identidad falsa o desactualizada, replay faltante, invalidación por cambio de puntero, cierre del store con el lease y reapertura posterior.
- C2D necesita autorización sobre ADB en los runners de CI disparados por rutas Android compartidas. El puente JVM está listo; sesión Android, montaje UI y operaciones W1 siguen pendientes. Permiso EXACT4 consumido. W1 OPEN, W2 STOP, V2 productiva OFF. No se ejecutaron ADB ni pruebas físicas.
