# O! TDT Lab 0.1.4
Prueba independiente para la entrada TDT de los TCL Android TV 11 de 32 bits o con soporte ARM de 32 bits. Selecciona Antena 3, espera la imagen y pulsa azul. Menú vuelve al selector. Comprueba si puedes navegar por LOVEStv, volver al directo y abrirlo una segunda vez. El diagnóstico se puede guardar en Descargas desde la pantalla inicial.

Si la lista está restringida, «Probar último canal» usa el último ID con vídeo o, en el primer arranque, 10032330, observado anteriormente en la TCL de prueba. El botón de ID manual permite usar otro canal de esa instalación sin volver a buscar canales.

La actividad mantiene una sola TvView. Tras recibir vídeo abre el motor nativo con la identidad de esta app y monta el navegador sobre el reproductor. El firmware debe exponer y autorizar los servicios al paquete. La aceptación de una tecla o el arranque del motor por sí solos no certifican que el canal haya mostrado LOVEStv.

Compilación: Linux, Android SDK 35, build-tools 35.0.0, JDK 17, clang, lld y zip. Ejecuta `bash build.sh` con ANDROID_HOME configurado. Para actualizar con una firma de prueba persistente utiliza LAB_KEYSTORE con la ruta de un almacén propio de pruebas (contraseña de desarrollo android). La compilación de CI crea una firma efímera.
