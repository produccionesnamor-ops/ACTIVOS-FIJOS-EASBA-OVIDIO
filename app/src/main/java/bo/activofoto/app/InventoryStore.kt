package bo.activofoto.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.util.Locale
import java.util.UUID

data class AssetSummary(val code: String, val description: String, val photoCount: Int)
data class PhotoRecord(val id: String, val assetCode: String, val fileName: String, val capturedAt: Long)
data class ExportRecord(val code: String, val description: String, val fileName: String, val capturedAt: Long)

/** Registro local. No depende de servidores ni requiere permisos de almacenamiento. */
class InventoryStore(context: Context) : SQLiteOpenHelper(context, "activos.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE activos (
                codigo TEXT PRIMARY KEY COLLATE NOCASE,
                descripcion TEXT NOT NULL DEFAULT '',
                creado_en INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE fotos (
                id TEXT PRIMARY KEY,
                activo_codigo TEXT NOT NULL,
                nombre_archivo TEXT NOT NULL UNIQUE,
                fecha INTEGER NOT NULL,
                FOREIGN KEY(activo_codigo) REFERENCES activos(codigo)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_fotos_codigo ON fotos(activo_codigo)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Agregar migraciones explícitas al modificar el esquema en futuras versiones.
        check(oldVersion == newVersion) { "Se requiere migración de base de datos" }
    }

    fun registerPhoto(rawCode: String, description: String, photoFile: File, takenAt: Long) {
        val code = rawCode.trim().uppercase(Locale.ROOT)
        require(code.isNotEmpty()) { "El código es obligatorio" }
        require(photoFile.isFile) { "No se encontró la fotografía procesada" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("activos", null, ContentValues().apply {
                put("codigo", code)
                put("descripcion", description.trim())
                put("creado_en", takenAt)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            if (description.isNotBlank()) {
                db.update("activos", ContentValues().apply {
                    put("descripcion", description.trim())
                }, "codigo = ?", arrayOf(code))
            }
            db.insertOrThrow("fotos", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("activo_codigo", code)
                put("nombre_archivo", photoFile.name)
                put("fecha", takenAt)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAssets(): List<AssetSummary> {
        val items = mutableListOf<AssetSummary>()
        readableDatabase.rawQuery("""
            SELECT a.codigo, a.descripcion, COUNT(f.id)
            FROM activos a LEFT JOIN fotos f ON a.codigo = f.activo_codigo
            GROUP BY a.codigo, a.descripcion
            ORDER BY a.codigo COLLATE NOCASE
        """.trimIndent(), null).use { cursor ->
            while (cursor.moveToNext()) items += AssetSummary(
                cursor.getString(0), cursor.getString(1), cursor.getInt(2)
            )
        }
        return items
    }

    fun getPhotos(code: String): List<PhotoRecord> {
        val items = mutableListOf<PhotoRecord>()
        readableDatabase.rawQuery("""
            SELECT id, activo_codigo, nombre_archivo, fecha
            FROM fotos WHERE activo_codigo = ?
            ORDER BY fecha DESC
        """.trimIndent(), arrayOf(code)).use { cursor ->
            while (cursor.moveToNext()) items += PhotoRecord(
                cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)
            )
        }
        return items
    }

    fun getExportRecords(): List<ExportRecord> {
        val items = mutableListOf<ExportRecord>()
        readableDatabase.rawQuery("""
            SELECT a.codigo, a.descripcion, f.nombre_archivo, f.fecha
            FROM activos a JOIN fotos f ON a.codigo = f.activo_codigo
            ORDER BY a.codigo COLLATE NOCASE, f.fecha
        """.trimIndent(), null).use { cursor ->
            while (cursor.moveToNext()) items += ExportRecord(
                cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3)
            )
        }
        return items
    }

    fun deletePhoto(id: String) {
        writableDatabase.delete("fotos", "id = ?", arrayOf(id))
    }
}
