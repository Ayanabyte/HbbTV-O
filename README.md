# O! · LOVEStv Lab

Aplicación de prueba Android TV nueva e independiente para probar sintonización TDT con TvView y navegador HbbTV de TCL con botón azul.

No copia un DEX antiguo, ni librerías del fabricante, ni autorizaciones de apps de sistema. Las APIs del firmware TCL podrían negar acceso a aplicaciones externas: la compilación no demuestra funcionamiento.

## Prueba de aceptación

1. Abrir O! y conceder acceso a listas de TV.
2. Confirmar imagen de Antena 3 o elegir otro canal con OK.
3. Pulsar AZUL y verificar apertura real de LOVEStv dentro de O!.
4. Salir al mismo canal y pulsar AZUL de nuevo SIN reiniciar la aplicación.
5. En MENÚ / INFO puede verse y compartirse el diagnóstico; abrir TV TCL original solo permite comparar.

Paquete independiente: com.ayanabyte.o.lovestv.lab.
Compilación: rama o-lovestv-lab, GitHub Actions, APK de depuración ARM32.
Para el registro: adb logcat -d -v time -s OLoveTv:I '*:S'

La firma de depuración puede variar en compilaciones distintas. Si una actualización no se instala, desinstalar solamente esta aplicación de prueba antes de instalar la APK nueva.

## Mando TCL, versión 0.1.3

En modo TDT, OK, flecha ABAJO y ATRÁS abren SIEMPRE el menú con Diagnóstico como primera opción. No hace falta tecla MENÚ ni INFO. Cuando HbbTV falla, se muestra automáticamente un diálogo grande y navegable con «DIAGNÓSTICO», «COMPARTIR» y «CERRAR». El diagnóstico dispone de «COPIAR».

Si LOVEStv llega a abrirse, las flechas y OK se entregan a HbbTV, y ATRÁS sigue siendo la tecla de navegación/salida del servicio. El identificador de laboratorio de esta compilación es `com.ayanabyte.o.lovestv.lab4` para evitar conflictos de firma y distinguirla de las versiones anteriores. Sigue siendo una prueba experimental; no presupone que el motor TCL autorice el arranque.

## Exportar diagnóstico: 0.1.4

Mientras ves la TDT, OK / flecha ABAJO / ATRÁS abre el menú. Elige «GUARDAR diagnóstico .TXT en Descargas» o pulsa «GUARDAR .TXT» en el diálogo de diagnóstico. Android 11 guarda a través de MediaStore en `/sdcard/Download/O-LOVEStv-diagnostico-YYYYMMDD_HHMMSS.txt` sin solicitar permisos de almacenamiento. También se puede compartir. En sistemas anteriores a Android 10 se guarda en la carpeta externa específica de la aplicación. Este experimento utiliza `com.ayanabyte.o.lovestv.lab5`; el cambio no supone que HbbTV ya funcione.

## TVSDK 0.1.5 experimental

O! 0.1.5 registra su propio proceso ante TTvFunctionManager antes de inicializar el navegador HbbTV. Verifica la huella SHA-256 de la APK TCL G07/V138 instalada y la huella de su cadena firmada; no exporta el contenido ni lo empaqueta. Si la versión TCL cambia, aborta el registro y lo explica. El valor devuelto por systemAuthorized se registra en el diagnóstico como verdadero/falso, nunca la credencial. No se modifica el firmware, los certificados originales ni la TV TCL. Una autorización aceptada no demuestra por sí sola que DVB/AIT funcione.

El menú OK permite «Reintentar registro TVSDK». En «Ver diagnóstico TVSDK / HbbTV» se indica el estado de autorización y del navegador. Se puede guardar .txt en Descargas. Paquete independiente `com.ayanabyte.o.lovestv.lab6`. No actualiza ni reemplaza otras apps.
