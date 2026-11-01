# FARO Noche V1.5 — Activación expresa

- Fecha: domingo 2026-11-01, hora Argentina.
- Autorización del usuario: `Activar FARO Noche V1.5`.
- Rama exclusiva: `work/faro-noche-v15-20261101`, HEAD preparado `a5042e194489e75fb26f147ac4a9cf75faceb1e6`.
- Tarea A: `6ae6c5c552a88191be5f52fd0b6b133d`, cada hora 02:00–05:00 (cuatro turnos).
- Tarea B: `6ae6c5c740808191b748e0e79838e797`, cada hora 02:30–05:30 (cuatro turnos).
- Informe final: `6ae6c5d4ffe08191b8804bf4b8cb51d0`, a las 06:00 READ_ONLY, desactiva/verifica A y B.
- Límite absoluto de todas las escrituras y tareas de desarrollo: `2026-11-01T06:00:00-03:00`; no extender.
- Prioridad exclusiva `F2 → F3 → F4` dentro de S2.A6, según `AUTH_PROTOCOL.md`. F1 congelado y T4 físico diferido conjunto.
- Ninguna tarea F3 hasta que F2 quede listo automáticamente, ninguna F4 hasta que F3 quede listo automáticamente, salvo dependencia humana genuina documentada según protocolo.
- Seguridad: writer exclusivo, CAS HEAD exacto, READ_ONLY útil mientras busy. Sin Android físico ni APK, V2 OFF, no tocar main/V1/V4.4/S3/S4 ni producción.
- Este registro configura la activación: **no** declara iniciado el desarrollo ni certifica pruebas nuevas. La tarea A inicia según su reloj programado.
