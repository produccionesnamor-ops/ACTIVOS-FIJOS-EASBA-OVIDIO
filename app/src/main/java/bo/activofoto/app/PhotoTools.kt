package bo.activofoto.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val locale = Locale("es", "BO")
fun formattedDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(ms))
fun photoFile(context: Context, fileName: String): File = File(File(context.filesDir, "fotos"), fileName)
fun shareUri(context: Context, file: File): Uri = FileProvider.getUriForFile(
    context, "${context.packageName}.fileprovider", file
)

/** Genera una copia JPG con el código y fecha incrustados en sus píxeles. */
fun stampPhoto(context: Context, captured: File, rawCode: String, timestamp: Long): File {
    val code = rawCode.trim().uppercase(Locale.ROOT)
    require(code.isNotBlank())
    require(captured.length() > 0L) { "La cámara no guardó ninguna fotografía" }

    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(captured.absolutePath, opts)
    require(opts.outWidth > 0 && opts.outHeight > 0) { "Formato de foto no válido" }
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / sample > 2400) sample *= 2
    val decoded = BitmapFactory.decodeFile(captured.absolutePath, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: error("No se pudo leer la imagen")

    val orientation = ExifInterface(captured.absolutePath).getAttributeInt(
        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
    )
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val mirrored = orientation in listOf(
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL, ExifInterface.ORIENTATION_FLIP_VERTICAL,
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE
    )
    val matrix = Matrix().apply {
        if (mirrored) postScale(-1f, 1f)
        if (degrees != 0f) postRotate(degrees)
    }
    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (rotated !== decoded) decoded.recycle()
    val image = rotated.copy(Bitmap.Config.ARGB_8888, true)
    rotated.recycle()

    val width = image.width.toFloat()
    val height = image.height.toFloat()
    val margin = width * 0.038f
    val barHeight = maxOf(115f, height * 0.17f)
    val canvas = Canvas(image)
    val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(218, 20, 35, 51) }
    canvas.drawRect(0f, height - barHeight, width, height, overlay)

    val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 187, 83)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = maxOf(30f, width * 0.057f)
    }
    val label = "ACTIVO FIJO: $code"
    while (codePaint.measureText(label) > width - 2 * margin && codePaint.textSize > 23f) {
        codePaint.textSize -= 2f
    }
    canvas.drawText(label, margin, height - barHeight * 0.50f, codePaint)
    val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = maxOf(20f, width * 0.027f)
    }
    canvas.drawText(formattedDate(timestamp) + "  •  REGISTRO FOTOGRÁFICO", margin,
        height - barHeight * 0.18f, datePaint)

    val folder = File(context.filesDir, "fotos").apply { mkdirs() }
    val safeCode = code.replace(Regex("[^A-Z0-9_-]"), "_").take(48)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date(timestamp))
    val result = File(folder, "${safeCode}_${stamp}_${UUID.randomUUID().toString().take(6)}.jpg")
    try {
        FileOutputStream(result).use { stream ->
            check(image.compress(Bitmap.CompressFormat.JPEG, 91, stream))
        }
    } catch (e: Exception) {
        result.delete()
        throw e
    } finally {
        image.recycle()
    }
    return result
}

fun loadThumbnail(file: File): Bitmap? {
    if (!file.exists()) return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / sample > 720) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

fun exportInventory(context: Context, store: InventoryStore): File {
    val photos = store.getExportRecords()
    require(photos.isNotEmpty()) { "No hay fotografías para exportar" }
    val folder = File(context.cacheDir, "exportaciones").apply { mkdirs() }
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
    val result = File(folder, "ActivoFoto_Respaldo_$name.zip")
    val csv = buildString {
        append('\uFEFF')
        append("Codigo;Descripcion;Fecha;Archivo\n")
        photos.forEach { record ->
            append(listOf(record.code, record.description, formattedDate(record.capturedAt),
                record.fileName).joinToString(";") { csvCell(it) })
            append('\n')
        }
    }
    try {
        ZipOutputStream(result.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("inventario.csv"))
            zip.write(csv.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            photos.forEach { record ->
                val file = photoFile(context, record.fileName)
                check(file.isFile) { "Falta la fotografía: ${record.fileName}" }
                zip.putNextEntry(ZipEntry("fotos/${record.fileName}"))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    } catch (e: Exception) {
        result.delete()
        throw e
    }
    return result
}

private fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

fun shareFile(context: Context, file: File, mimeType: String) {
    val uri = shareUri(context, file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Compartir o guardar archivo"))
}

fun openPhoto(context: Context, file: File) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(shareUri(context, file), "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Abrir fotografía"))
}
