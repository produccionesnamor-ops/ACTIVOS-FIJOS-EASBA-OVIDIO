# ActivoFoto 1.0 — Proyecto Android

**Inventario fotográfico de activos fijos, con operación sin conexión.**

## Lo que hace esta primera versión

- Introducir un **código de activo fijo** (obligatorio) y una descripción (opcional).
- Abrir la cámara del teléfono para tomar una foto de alta calidad.
- **Incrustar el código y la fecha** en una banda dentro del propio archivo JPG.
- Generar un nombre de archivo identificable, por ejemplo `AF-00125_20261111_153025_a1b2c3.jpg`.
- Tomar varias fotos del mismo activo y agruparlas por código.
- Buscar activos, ver la galería, abrir, compartir y eliminar fotos con confirmación.
- Exportar un **ZIP** que contiene todas las imágenes y `inventario.csv` (código, descripción, fecha y nombre).
- Trabajar completamente **sin internet**; cuando hay internet, compartir el ZIP usando Google Drive, correo, etc. mediante el menú de Android.

## Cómo se usa

1. Abrir **ActivoFoto** y pulsar **Registrar fotografía**.
2. Ingresar el código, por ejemplo `AF-00125`; opcionalmente, una descripción.
3. Pulsar **Abrir cámara y tomar foto**, tomar y aceptar la foto en la cámara.
4. ActivoFoto guarda el JPG con código y fecha incrustados y abre la galería de ese activo.
5. Pulsar **Tomar otra foto** para seguir con el mismo activo, o volver e iniciar otro registro.
6. En **Inicio > Exportar ZIP**, compartir el respaldo usando Drive o cualquier app compatible.

## Abrir y generar el APK

Se entrega el **código fuente completo del módulo Android**, pero **NO un APK ya compilado**. Este entorno no incluye Android SDK/Gradle; la compilación y prueba física deben realizarse en Android Studio.

**Requisitos sugeridos:** Android Studio actualizado, JDK 17 y Android SDK 36.

1. Abrir esta carpeta `ActivoFoto_Android` como proyecto en Android Studio.
2. Permitir que Android Studio sincronice las dependencias por internet.
3. **Importante:** este archivo ZIP no trae el binario estándar `gradle-wrapper.jar` ni los scripts `gradlew` porque no se dispone de ellos aquí. Si Android Studio requiere wrapper, instalar Gradle 8.13 o usar un proyecto Android nuevo y ejecutar `gradle wrapper --gradle-version 8.13` en la carpeta raíz; alternativamente, copiar el wrapper generado por Android Studio. Consultar el apartado de Gradle del asistente.
4. Verificar que están instalados **SDK Android 36** y **JDK 17**.
5. Seleccionar un celular Android conectado por USB con depuración habilitada y presionar **Run**.
6. Para generar un APK de prueba: **Build > Build Bundle(s) / APK(s) > Build APK(s)**. El archivo suele aparecer en `app/build/outputs/apk/debug/app-debug.apk`.

> La versión 1.0 es una base de código que requiere compilación y pruebas; no afirmar que está probada en teléfonos todavía.

## Tecnología

- Kotlin + Jetpack Compose (Material 3)
- `SQLiteOpenHelper`: base de datos local, persistente, sin servidores.
- `ActivityResultContracts.TakePicture`: abre cámara del sistema.
- `FileProvider`: compartición segura de archivos.
- `ExifInterface` + `Canvas`: orientación de foto y sobreimpresión de etiquetas.
- `ZipOutputStream`: exportación a ZIP + CSV sin librerías extras.
- Soporte: **Android 8.0 o superior (API 26+)**.

## Alcance online/offline y seguridad

- **OFFLINE:** fotos, búsqueda, galería, base de datos y exportación ZIP funcionan localmente.
- **ONLINE:** se puede compartir el respaldo usando Drive u otras aplicaciones instaladas. **No incluye sincronización automática entre teléfonos** ni inicio de sesión en servidor. Para agregar esa función se necesita decidir servidor, cuentas, permisos y políticas de respaldo.
- Los datos están en almacenamiento privado de la app; **desinstalarla puede borrar el inventario local**. Antes de ello, exportar y resguardar el ZIP.
- Esta versión comprime fotos a un tamaño de hasta ~2400 píxeles en su lado mayor; puede disminuir la resolución de cámaras de alta gama.
- El código y la fecha se agregan a la copia procesada; no se guardan originales sin marcar.
- Los códigos se normalizan a mayúsculas; el CSV usa `;` como separador, conveniente para Excel regional.

## Estructura

```
app/src/main/java/bo/activofoto/app/
    MainActivity.kt     # pantallas y flujo de uso
    InventoryStore.kt   # activos y fotos en SQLite
    PhotoTools.kt       # captura, marca de agua, ZIP y compartir
app/src/main/AndroidManifest.xml
app/src/main/res/xml/file_paths.xml
```

## Mejoras siguientes

1. Lectura de código de barras y QR.
2. Registro de ubicación física, custodio, centro de costo y estado del activo.
3. Sincronización automática con servidor al recuperar internet; control de duplicados y usuarios.
4. Importación de lista maestra de activos desde Excel.
5. Reporte PDF/Excel e historial de auditoría.
6. Copia del ZIP en una carpeta elegida por el usuario y restauración de respaldos.

## Advertencia de calidad

Se revisó la estructura del proyecto y el flujo del código, pero en el entorno de entrega no hay un SDK de Android para ejecutar compilación o pruebas instrumentadas. Antes de un despliegue corporativo, probar cámara, orientación, guardado, permisos y exportación con los modelos reales de celulares.
