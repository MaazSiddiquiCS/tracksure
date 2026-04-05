package com.tracksure.android.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

fun encodeTrackingInviteAsQrBitmap(payload: String, sizePx: Int = 720): Bitmap? {
	if (payload.isBlank()) return null
	return try {
		val matrix: BitMatrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
		val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
		for (x in 0 until sizePx) {
			for (y in 0 until sizePx) {
				bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
			}
		}
		bmp
	} catch (_: Exception) {
		null
	}
}


