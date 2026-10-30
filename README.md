# FARO V5 — Código Android (copia privada saneada en evaluación)

Repositorio **PRIVADO**, creado para contener únicamente la nueva aplicación FARO V5 sin importar historial ni ramas antiguas.

## Origen y estado

- Origen privado: `etacsantafe-source/FARO`
- Rama de origen: `work/faro-v5-design-clean-20261019`
- Instantánea exacta: `44f1e6eb77102a71955b4fb113582d209d94ac69` (C138).
- `v5/`: 541 archivos del build independiente, incluyendo código fuente, pruebas y Gradle. Los 541 blobs y modos coinciden con origen.
- `scripts/v5/faro-v5-s0-t4-physical`: dependencia necesaria para una verificación física específica del build V5.

## Situación real

**Copia de código preparada, aún NO certificada en el repositorio nuevo.** S2.A6 abierto, 3/8 criterios; F1/F2/F3 en curso, F4 pendiente; V2 OFF, S3/S4 no abiertos. Se conservan las certificaciones históricas como referencia en el repositorio privado original, pero no se presume PASS de una compilación nueva hasta probarla.

El desarrollo canónico sigue en el repositorio antiguo hasta validar la migración y autorizar el cambio de autoridad.

## Compilación

Desde `v5/`, usar `./gradlew v5StructuralCheck` y después los gates dirigidos previstos para el bloque autorizado. Los tests físicos T4 requieren hardware real. No ejecutar Actions ni modo noche sin autorización.

## Seguridad

No hay copia del historial Git, V3, V4, el viejo `app/`, los documentos históricos completos, diagnósticos ni `ci/faro-alpha-signing.p12.b64`.

**NO hacer público** sin auditoría final de contenido, historial nuevo y permisos. No activar CIERRE Nube WRITE por el mero hecho de tener esta copia.
