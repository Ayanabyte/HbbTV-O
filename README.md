# HbbTV O!

Aplicación independiente para Android TV. Sintoniza TDT con TvView y, cuando hay imagen, inicia el servidor HbbTV y el navegador instalados en el TCL. La compatibilidad depende de que el firmware permita iniciar esos componentes desde la aplicación.

## Mando

- OK abre la lista de canales. CH+ y CH− cambian de canal.
- Azul se envía al servicio HbbTV del canal actual. Las flechas y OK pasan al servicio durante la navegación.
- Atrás se envía primero al servicio. Cuando este devuelve el control, la app conserva el canal y prepara otra sesión.

El identificador de instalación es `com.ayanabyte.hbbtvo.live`. GitHub Actions compila la APK de depuración con Gradle. La prueba de aceptación en la TV consiste en abrir LOVEStv con azul, salir al mismo canal y abrirlo una segunda vez.
