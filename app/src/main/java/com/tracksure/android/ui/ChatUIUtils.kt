package com.tracksure.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat

object ChatUIUtils {
    // Helper to create bitmap markers if needed later
    fun getBitmapFromVector(context: Context, vectorResId: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
