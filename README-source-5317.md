# O! LOVEStv · prueba independiente desde fuentes 5.3.17

Este laboratorio NO utiliza la implementación de O! LOVEStv Lab 0.1.x/0.2.x.
En vez de adaptar de nuevo el motor, compila la implementación **real** contenida
en el archivo de fuentes 5.3.17 aportado por el usuario, que también se conserva
en el repositorio público conectado.

## Fuente reproducible
- Repositorio fuente: Ayanabyte/Channelplus
- Commit fijado: 7d5509396d5cd2eac917f913b91c5d74f3e78247
- Overlay exacto 5.3.7: SHA256 fea649552eb21f7a5df958a20dbb1cf6915b186b701a346aca498be434b8173b
- Secuencia: los diez primeros pasos de construcción HbbTV del workflow fuente
  \`.github/workflows/channelplus-537-lovestv.yml\`, **en orden**.
  Esto incluye la actualización especial 5.3.17 para rearmar LOVEStv al salir.
- Biblioteca HbbTV/JNI originales se incorporan desde ese árbol fuente.
- Artefacto final: \`O-LOVEStv-Fuente-5.3.17-test.apk\`

## Qué cambia
Solamente el **applicationId** a \`com.ayanabyte.o.lovestv.src5317\`,
el título de la aplicación para Android TV a \`O! LOVEStv · Fuente 5.3.17\`
y la versión de pruebas \`0.0.1-source5317\`. La rama \`main\` de O! no se modifica.
La versión antigua con su paquete y datos permanece instalada.

Los nombres de clases y las dependencias internas del código fuente original
**no se reescriben**, porque cambiar esas rutas puede romper las bibliotecas
y la comunicación con el servicio TCL. Por tanto, esta prueba no pretende aún
ser una base de código limpia de todas las referencias históricas; primero
verificamos que la implementación original funciona en una APK independiente.
Después se podrá migrar por partes y renombrar con pruebas de regresión.
Conservar avisos y licencias del código heredado es obligatorio.

## Prueba
1. Instalar la APK Android TV 32-bit del release de esta rama (paquete independiente).
2. Conceder lectura de canales y abrir la TDT de Antena 3.
3. Esperar el indicador HbbTV/LOVEStv del radiodifusor y pulsar AZUL.
4. Verificar que se abre LOVEStv, se regresa al canal y puede abrirse de nuevo.
5. Si falla, capturar \`adb logcat -b crash -d -v threadtime\`.

No se ha validado en el televisor físico. Compilar y pasar las comprobaciones
de código no prueba que la AIT DVB o LOVEStv estén disponibles.
