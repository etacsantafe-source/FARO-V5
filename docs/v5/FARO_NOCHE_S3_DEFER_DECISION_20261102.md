# Decisión humana — diferir programación Noche a S3 (2026-11-02)

**Decisión autorizada:** La programación de FARO Noche A/B se configura cuando comience S3. No es un criterio de cierre de la integración preparatoria Auto V2.

## Alcance de cierre previo a S3
- Mantener Noche A/B desactivadas.
- Validar funcionamiento integral del ejecutor y cola compartida, permisos y fallos seguros.
- No afirmar certificación de ejecución integral sin evidencia CI.
- Writer CAS y pruebas de concurrencia Git: Actions #41476966501 SUCCESS.
- No abrir S3 automáticamente por este registro.
- El primer trabajo de S3 incluye configurar horarios de Noche A/B y su verificación, con exclusividad compartida.

## Protección
El repositorio FARO histórico, main, S2 cerrado 8/8 y V2 de la app OFF permanecen intactos.
