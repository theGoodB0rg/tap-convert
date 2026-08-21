package com.tapconvert.feature.image.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

object ExifTransformer {

    fun extractOrientation(inputStream: InputStream): Int {
        return try {
            val exif = ExifInterface(inputStream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    fun extractOrientation(file: File): Int {
        return try {
            val exif = ExifInterface(file)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    data class OrientationTransform(
        val rotationDegrees: Float = 0f,
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false
    )

    fun getOrientationTransform(orientation: Int): OrientationTransform {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> OrientationTransform(rotationDegrees = 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> OrientationTransform(rotationDegrees = 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> OrientationTransform(rotationDegrees = 270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> OrientationTransform(flipHorizontal = true)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> OrientationTransform(flipVertical = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> OrientationTransform(rotationDegrees = 90f, flipHorizontal = true)
            ExifInterface.ORIENTATION_TRANSVERSE -> OrientationTransform(rotationDegrees = 270f, flipHorizontal = true)
            else -> OrientationTransform()
        }
    }

    fun createRotationMatrix(orientation: Int): Matrix {
        val matrix = Matrix()
        val transform = getOrientationTransform(orientation)
        if (transform.rotationDegrees != 0f) matrix.postRotate(transform.rotationDegrees)
        if (transform.flipHorizontal) matrix.postScale(-1f, 1f)
        if (transform.flipVertical) matrix.postScale(1f, -1f)
        return matrix
    }

    fun applyOrientation(source: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return source
        }
        val matrix = createRotationMatrix(orientation)
        return try {
            val transformed = Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true
            )
            if (transformed != source && !source.isRecycled) {
                source.recycle()
            }
            transformed
        } catch (_: Throwable) {
            source
        }
    }

    fun copyExifAttributes(sourceFile: File, destinationFile: File, preserveGps: Boolean = true) {
        try {
            if (!sourceFile.exists() || !destinationFile.exists()) return
            val srcExif = ExifInterface(sourceFile)
            val destExif = ExifInterface(destinationFile)

            val tags = mutableListOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_FLASH,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_WHITE_BALANCE,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
            )

            if (preserveGps) {
                tags.addAll(listOf(
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP
                ))
            }

            for (tag in tags) {
                srcExif.getAttribute(tag)?.let { value ->
                    destExif.setAttribute(tag, value)
                }
            }
            // Reset destination orientation to NORMAL since pixel buffer is already rotated upright
            destExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            destExif.saveAttributes()
        } catch (_: Throwable) {
            // EXIF copy error should not abort the successful image conversion
        }
    }
}
