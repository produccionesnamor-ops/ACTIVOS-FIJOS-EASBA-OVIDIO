# Cómo obtener el APK instalable de ActivoFoto

El código Android está incluido aquí, pero **este paquete no contiene aún un APK**. Se incluye un flujo de **GitHub Actions** que, al ejecutarse en GitHub, compila un APK Android de depuración firmado automáticamente. Se llama `app-debug.apk` y sirve para instalarlo en celulares con Android 8.0 o posterior y probar sus funciones.

## Opción A — Generar el APK en GitHub (sin instalar programas)

1. En [github.com/new](https://github.com/new), inicia sesión y crea un repositorio privado, por ejemplo `ActivoFoto`.
2. Descomprime `ActivoFoto_Android_Con_Compilacion.zip` en tu computadora.
3. En el repositorio, selecciona **Add file > Upload files** y sube **todo el contenido** de la carpeta `ActivoFoto_Android`, incluyendo la carpeta oculta `.github` y el archivo `.github/workflows/generar-apk.yml`. Confirma con **Commit changes**. Para asegurarte de que GitHub conserve la carpeta `.github`, puedes verificar que aparezca en la lista de archivos del repositorio.
4. En el repositorio abre **Actions**, selecciona **Generar APK Android**, pulsa **Run workflow** y confirma. Si al subir los archivos se ha iniciado automáticamente el flujo, no hace falta iniciarlo de nuevo.
5. Abre la ejecución completada de **Generar APK Android** y, en **Artifacts**, descarga **ActivoFoto-APK**. Descomprime lo descargado: encontrarás `app-debug.apk`.
6. Envía `app-debug.apk` al teléfono, ábrelo e instala. Android puede pedirte habilitar temporalmente la instalación desde la aplicación que abre el archivo.

**Si falla la compilación:** abre la ejecución en Actions, entra en **Compilar ActivoFoto** y revisa el paso con error. El proyecto no ha sido compilado ni probado físicamente en este entorno, de modo que la compilación y pruebas son necesarias antes de usarlo con información real.

## Opción B — Compilar en Android Studio

Instala Android Studio y Android SDK 36; abre esta carpeta como proyecto de Android. Este código fuente incluye `gradle/wrapper/gradle-wrapper.properties`, pero no el archivo binario `gradle-wrapper.jar`. Si tu instalación lo exige, instala Gradle 8.13 y genera el wrapper usando `gradle wrapper --gradle-version 8.13`, o genera el APK usando Gradle 8.13 instalado directamente con `gradle :app:assembleDebug`.

Ruta del APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.

## Alcance y resguardo de fotos

Esta versión guarda fotos y base de datos **dentro de la aplicación en el celular**. Puedes usarla sin conexión y exportar ZIP más CSV para compartirlo cuando tengas internet. No hay sincronización automática entre celulares. **Exporta el ZIP de respaldo antes de desinstalar o cambiar de equipo**, ya que podrías perder el inventario local. Para instalación en muchos celulares o publicación en Play Store se necesitará preparar una versión de producción firmada con una clave privada propia.
