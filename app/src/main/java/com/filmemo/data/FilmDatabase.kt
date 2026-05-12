package com.filmemo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FilmDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "filmemo.db"
        private const val DB_VERSION = 2

        // Films table
        const val TABLE_FILMS = "films"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_ISO = "iso"
        const val COL_STATUS = "status"
        const val COL_CREATED_AT = "created_at"
        const val COL_FINISHED_AT = "finished_at"

        // Exposures table
        const val TABLE_EXPOSURES = "exposures"
        const val COL_EXP_ID = "id"
        const val COL_FILM_ID = "film_id"
        const val COL_APERTURE = "aperture"
        const val COL_SHUTTER_SPEED = "shutter_speed"
        const val COL_EXP_ISO = "iso"
        const val COL_EXP_CREATED_AT = "created_at"
        const val COL_ORDER_INDEX = "order_index"
        const val COL_HAS_FLASH = "has_flash"
        const val COL_FLASH_GN = "flash_gn"

        // Current film
        const val TABLE_CURRENT_FILM = "current_film"
        const val COL_CURRENT_FILM_ID = "film_id"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_FILMS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_ISO INTEGER NOT NULL,
                $COL_STATUS TEXT NOT NULL DEFAULT '${Film.STATUS_ACTIVE}',
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_FINISHED_AT INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_EXPOSURES (
                $COL_EXP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FILM_ID INTEGER NOT NULL,
                $COL_APERTURE REAL NOT NULL,
                $COL_SHUTTER_SPEED TEXT NOT NULL,
                $COL_EXP_ISO INTEGER NOT NULL,
                $COL_EXP_CREATED_AT INTEGER NOT NULL,
                $COL_ORDER_INDEX INTEGER DEFAULT 0,
                $COL_HAS_FLASH INTEGER DEFAULT 0,
                $COL_FLASH_GN REAL,
                FOREIGN KEY($COL_FILM_ID) REFERENCES $TABLE_FILMS($COL_ID)
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_CURRENT_FILM (
                $COL_CURRENT_FILM_ID INTEGER NOT NULL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_EXPOSURES ADD COLUMN $COL_ORDER_INDEX INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_EXPOSURES ADD COLUMN $COL_HAS_FLASH INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_EXPOSURES ADD COLUMN $COL_FLASH_GN REAL")
        }
    }

    // Film operations
    fun insertFilm(film: Film): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_NAME, film.name)
            put(COL_ISO, film.iso)
            put(COL_STATUS, film.status)
            put(COL_CREATED_AT, film.createdAt)
            put(COL_FINISHED_AT, film.finishedAt)
        }
        return db.insert(TABLE_FILMS, null, values)
    }

    fun getAllFilms(): List<Film> {
        val films = mutableListOf<Film>()
        val db = readableDatabase
        val cursor = db.query(TABLE_FILMS, null, null, null, null, null, "$COL_CREATED_AT DESC")

        cursor.use {
            while (it.moveToNext()) {
                films.add(cursorToFilm(it))
            }
        }
        return films
    }

    fun getActiveFilms(): List<Film> {
        val films = mutableListOf<Film>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FILMS, null,
            "$COL_STATUS = ?", arrayOf(Film.STATUS_ACTIVE),
            null, null, "$COL_CREATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                films.add(cursorToFilm(it))
            }
        }
        return films
    }

    fun getFilmById(id: Long): Film? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_FILMS, null,
            "$COL_ID = ?", arrayOf(id.toString()),
            null, null, null
        )

        cursor.use {
            return if (it.moveToFirst()) cursorToFilm(it) else null
        }
    }

    fun finishFilm(id: Long): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_STATUS, Film.STATUS_FINISHED)
            put(COL_FINISHED_AT, System.currentTimeMillis())
        }
        val rows = db.update(TABLE_FILMS, values, "$COL_ID = ?", arrayOf(id.toString()))
        return rows > 0
    }

    // Current film operations
    fun setCurrentFilm(filmId: Long) {
        val db = writableDatabase
        db.delete(TABLE_CURRENT_FILM, null, null)
        val values = ContentValues().apply {
            put(COL_CURRENT_FILM_ID, filmId)
        }
        db.insert(TABLE_CURRENT_FILM, null, values)
    }

    fun getCurrentFilmId(): Long? {
        val db = readableDatabase
        val cursor = db.query(TABLE_CURRENT_FILM, null, null, null, null, null, null)

        cursor.use {
            return if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(COL_CURRENT_FILM_ID)) else null
        }
    }

    // Exposure operations
    fun insertExposure(exposure: Exposure): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FILM_ID, exposure.filmId)
            put(COL_APERTURE, exposure.aperture)
            put(COL_SHUTTER_SPEED, exposure.shutterSpeed)
            put(COL_EXP_ISO, exposure.iso)
            put(COL_EXP_CREATED_AT, exposure.createdAt)
            put(COL_ORDER_INDEX, exposure.orderIndex)
            put(COL_HAS_FLASH, if (exposure.hasFlash) 1 else 0)
            put(COL_FLASH_GN, exposure.flashGN)
        }
        return db.insert(TABLE_EXPOSURES, null, values)
    }

    fun getExposuresByFilmId(filmId: Long, page: Int = 1, pageSize: Int = 10): Pair<List<Exposure>, Int> {
        val exposures = mutableListOf<Exposure>()
        val db = readableDatabase

        // Get total count
        val countCursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_EXPOSURES WHERE $COL_FILM_ID = ?",
            arrayOf(filmId.toString())
        )
        val total = countCursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

        // Get paginated results
        val offset = (page - 1) * pageSize
        val cursor = db.query(
            TABLE_EXPOSURES, null,
            "$COL_FILM_ID = ?", arrayOf(filmId.toString()),
            null, null,
            "$COL_ORDER_INDEX ASC, $COL_EXP_CREATED_AT DESC",
            "$offset, $pageSize"
        )

        cursor.use {
            while (it.moveToNext()) {
                exposures.add(cursorToExposure(it))
            }
        }
        return Pair(exposures, total)
    }

    fun getAllExposuresByFilmId(filmId: Long): List<Exposure> {
        val exposures = mutableListOf<Exposure>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_EXPOSURES, null,
            "$COL_FILM_ID = ?", arrayOf(filmId.toString()),
            null, null,
            "$COL_ORDER_INDEX ASC, $COL_EXP_CREATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                exposures.add(cursorToExposure(it))
            }
        }
        return exposures
    }

    fun getExposureCountByFilmId(filmId: Long): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_EXPOSURES WHERE $COL_FILM_ID = ?",
            arrayOf(filmId.toString())
        )
        return cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    fun updateExposureOrder(exposureId: Long, orderIndex: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ORDER_INDEX, orderIndex)
        }
        db.update(TABLE_EXPOSURES, values, "$COL_EXP_ID = ?", arrayOf(exposureId.toString()))
    }

    fun deleteExposure(exposureId: Long): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_EXPOSURES, "$COL_EXP_ID = ?", arrayOf(exposureId.toString())) > 0
    }

    private fun cursorToFilm(cursor: android.database.Cursor): Film {
        return Film(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
            iso = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ISO)),
            status = cursor.getString(cursor.getColumnIndexOrThrow(COL_STATUS)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
            finishedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FINISHED_AT)).takeIf { it > 0 }
        )
    }

    private fun cursorToExposure(cursor: android.database.Cursor): Exposure {
        return Exposure(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EXP_ID)),
            filmId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_FILM_ID)),
            aperture = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_APERTURE)),
            shutterSpeed = cursor.getString(cursor.getColumnIndexOrThrow(COL_SHUTTER_SPEED)),
            iso = cursor.getInt(cursor.getColumnIndexOrThrow(COL_EXP_ISO)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_EXP_CREATED_AT)),
            orderIndex = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORDER_INDEX)),
            hasFlash = cursor.getInt(cursor.getColumnIndexOrThrow(COL_HAS_FLASH)) == 1,
            flashGN = cursor.getDouble(cursor.getColumnIndexOrThrow(COL_FLASH_GN)).takeIf { it > 0 }
        )
    }
}
