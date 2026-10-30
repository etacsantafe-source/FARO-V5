# FARO V5 — copia saneada en preparación

**Estado: PRIVADO / STAGING / NO PUBLICAR / SIN EJECUCIÓN AUTÓNOMA.**

Este repositorio nuevo es el destino de una migración por **instantánea nueva, sin importar el historial Git** del repositorio original. Todavía **no contiene el código de la aplicación** y no debe considerarse una versión funcional ni auditada.

## Base autoritativa de origen

- Repositorio original: `etacsantafe-source/FARO` (**permanece privado e intacto**).
- Rama: `work/faro-v5-design-clean-20261019`.
- SHA de referencia inicial: `44f1e6eb77102a71955b4fb113582d209d94ac69` (C138).
- Estado: S2.A6 abierto (3/8); V2 apagado; writer NONE; S3/S4 sin abrir.
- La fase de desarrollo de producto continúa en el repositorio original hasta que una migración verificada y autorizada establezca otro dueño.

## Reglas innegociables de migración

1. Jamás hacer público este repositorio antes de auditoría completa y aprobación humana explícita.
2. **No copiar** archivos `ci/faro-alpha-signing.p12.b64`, claves, credenciales, backups, diagnósticos, bases de datos o datos personales.
3. No ejecutar `git push --mirror`, `git clone --mirror`, ni copiar `.git/`, tags, branches, objetos o histórico del repositorio original.
4. El nuevo historial debe comenzar limpio. Revisar también la privacidad de los metadatos de autor de los nuevos commits.
5. Solo importar código de fuente revisado y archivos imprescindibles para construir V5. **No importar documentación o workflows históricos sin revisión**.
6. T0/T1/T2/T3/T4 y las certificaciones de C138 **no se transfieren automáticamente**: se deben validar los contratos y la compilación en el destino.
7. CIERRE Nube solo READ_ONLY mientras no exista autorización independiente no eludible; D-507/D-509 siguen vigentes en el repositorio original.
8. No crear, activar ni ejecutar automatizaciones nocturnas por preparar esta copia.

## Etapas

- **P0 (actual):** inicialización privada y barreras de exclusión.
- **P1 pendiente:** generar snapshot sin historial, escanear cada archivo seleccionado (incluidos binarios) y revisar metadatos/código.
- **P2 pendiente:** trasladar snapshot auditado, probar compilación en GitHub Actions y demostrar equivalencia con el SHA de origen.
- **P3 pendiente:** resolver separación de permisos de CIERRE Nube y protección de ramas en el destino.
- **P4 pendiente:** solo tras aprobación expresa del usuario, considerar el cambio de visibilidad y la transición de autoridad.

Una instantánea privada no equivale a autorización para publicarla.
