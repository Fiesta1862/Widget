package com.screenbuddy.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

object PixelArt {
    fun render(c: Character, scale: Int, grid: List<String> = c.sprite): Bitmap {
        val h = grid.size
        val w = grid.maxOf { it.length }
        val bmp = Bitmap.createBitmap(w * scale, h * scale, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val p = Paint()
        for (y in 0 until h) {
            val row = grid[y]
            for (x in 0 until row.length) {
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
}
