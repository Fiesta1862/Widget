package com.screenbuddy.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

object PixelArt {
    fun render(c: Character, scale: Int, grid: List<String> = c.sprite): Bitmap {
        val h = grid.size
        val w = grid.maxOf { it.length }
        val bmp = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        for (y in 0 until h) {
            val row = grid[y]
            for (x in row.indices) {
                val hex = c.palette[row[x]] ?: continue
                p.color = Color.parseColor(hex)
                cv.drawRect(
                    (x * scale).toFloat(), (y * scale).toFloat(),
                    ((x + 1) * scale).toFloat(), ((y + 1) * scale).toFloat(), p
                )
            }
        }
        return bmp
    }

    /** Center-crops an imported image to a square and downsamples it without smoothing. */
    fun pixelate(source: Bitmap, size: Int = 16): Bitmap {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        canvas.drawBitmap(
            source,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, size, size),
            paint
        )
        return output
    }

    /** Enlarges a pixel sprite using nearest-neighbour scaling. */
    fun scaleNearest(source: Bitmap, scale: Int): Bitmap {
        val output = Bitmap.createBitmap(
            source.width * scale,
            source.height * scale,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        canvas.drawBitmap(
            source,
            null,
            Rect(0, 0, output.width, output.height),
            paint
        )
        return output
    }
}
