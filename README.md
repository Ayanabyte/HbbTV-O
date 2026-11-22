# HbbTV O!

Aplicación Android TV de diagnóstico para el televisor TCL. Comprueba por interfaces públicas si la app de TV TCL y su entrada de sintonizador están instaladas, prepara un informe y permite abrir la actividad de TV del sistema para que la persona elija un canal y pulse Azul en el mando.

La aplicación no contiene un sintonizador propio ni un motor HbbTV. El televisor original gestiona el canal, el motor y la navegación interactiva. Los resultados de LOVEStv que se anotan en el informe son indicados manualmente y se identifican como tales.

## Controles

- **Comprobar acceso** comprueba los paquetes TCL instalados y si `TVActivity` se puede resolver.
- **Prueba del dispositivo** registra modelo, Android, ABI del proceso y presencia de archivos de biblioteca conocidos, sin cargarlos.
- **Abrir TV TCL** abre la aplicación original. Allí, elige un canal y usa Azul si el canal ofrece un servicio interactivo.
- Al volver a O!, registra si la interfaz se abrió para incluir el resultado manual en el informe.
- El informe se puede compartir o guardar en el almacenamiento privado de la aplicación.

## Construir

Ejecuta `gradlew.bat assembleDebug`. La APK queda en `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions genera la misma APK en cada push y pull request.

## Límite comprobado

La delegación a la actividad de TV del fabricante no equivale a integrar HbbTV dentro de HbbTV O!. Consulta [las pruebas en TCL](docs/DEVICE_TESTS.md) para los resultados observados y la interacción manual pendiente.
