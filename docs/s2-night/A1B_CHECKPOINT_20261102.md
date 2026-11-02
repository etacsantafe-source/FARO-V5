# FARO S2 Noche A1 · segundo checkpoint

Se agregó recuperación exclusiva de turnos PROCESSING tras reinicio del host debug (sin repetir la solicitud remota), protección contra cierre de Activity mientras inicializa, y prueba Android dirigida de launcher real con consentimiento denegado/Keystore local simulado (sin red). Se agregó job de emulador API35 con oráculo XML 1/1. **Todo sigue sin PASS nuevo hasta CI.**

Actions anterior #41387836208 estaba IN_PROGRESS al registrar; nuevo commit dispara un segundo run dirigido con concurrencia serial (cancel-in-progress false). No hay escritor GitHub simultáneo; branch exclusiva. Mantener S2 OPEN 6/8, V2 OFF y T4 físico pendiente. Próximo turno: consultar run nuevo, corregir rojos; después A5, recuperación, APK única. Writer NONE.
