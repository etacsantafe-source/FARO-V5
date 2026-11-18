# FARO W01 D1B1C2A — B7 de preparación Finance9 con datos existentes

Unidad `S4_W01_D1B1C2A_FINANCE9_DEV_PRESERVE_STAGE`. Código `896cce4385a5ad9aa075a57c64515c5e4a6adc70`, EXACT3. Guard READY 44227171992 SUCCESS.

## B6
El helper copia las cinco bases cerradas bajo lease de mantenimiento, comprueba huellas antes y después, usa la migración exacta existente para el Reality de la etapa y valida el journal sin publicar el puntero. La comprobación previa a promoción detecta cambios de los bytes fuente aunque el puntero conserve ID y sequence.

## B7
**PASS SCOPED**. [CI dirigida 44227604408](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44227604408) y [guard 44227604168](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44227604168) SUCCESS sobre el SHA exacto. Artefacto 14393697163 descargado e inspeccionado. Digest ZIP `45e3c59adec880d5e4b26d9db8ac85b39c29fcc13a281b3082b7e071c24bc53b` y XML `991b9a1ea8bc835580a9604d8d4601e68f758a7128d31361ba7f35eeacf51f83`. 7/7, sin errores ni omitidos.
- Se verifica cuenta y movimiento ARS de fixture, bytes de origen intactos, las cuatro bases complementarias copiadas, Finance9 sellado, puntero anterior y journal VALIDATED. Las negativas cubren lease ausente, ABA, journal competidor, sidecar colgante, copia interrumpida y cambios en la fuente con el mismo puntero.
- Se consume el permiso. La ejecución no abre otro grafo de runtime. Falta adquisición real del root y autorización duradera del switch para reinicio, antes de montar una sesión Android. El workflow automático M2 44227606744 se seguía ejecutando al comprobar esta evidencia. W1 OPEN, W2 STOP, V2 productiva OFF.
