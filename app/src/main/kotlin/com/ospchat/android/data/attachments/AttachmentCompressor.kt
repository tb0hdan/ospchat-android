package com.ospchat.android.data.attachments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Down-scales an arbitrary user-picked image to at most [MAX_EDGE] pixels on
 * its longest edge, applies the EXIF orientation as a real pixel rotation,
 * and re-encodes the result as JPEG at [JPEG_QUALITY].
 *
 * Baking the rotation into the pixels means the produced JPEG is visually
 * upright and contains no EXIF (Bitmap.compress doesn't preserve EXIF
 * metadata), so the receiver renders it as-is without needing its own
 * rotation pipeline. The reported [Result.width] / [Result.height] are the
 * dimensions *after* rotation, so the receiver can pre-size its placeholder
 * with the correct aspect ratio.
 */
@Singleton
class AttachmentCompressor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        data class Result(
            val bytes: ByteArray,
            val width: Int,
            val height: Int,
            val mimeType: String,
        )

        fun compress(uri: Uri): Result {
            val resolver = context.contentResolver

            // Read EXIF orientation up front. The source URI might be a
            // photo-picker content URI; ExifInterface(InputStream) handles
            // all schemes the resolver supports.
            val orientation =
                resolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream)
                        .getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                } ?: ExifInterface.ORIENTATION_NORMAL

            // First pass: dimensions only.
            // decodeStream(..., inJustDecodeBounds=true) always returns null;
            // the dimensions are written to outWidth/outHeight on the options.
            val dimensionOptions =
                BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val dimensionStream =
                resolver.openInputStream(uri)
                    ?: error("Could not open attachment URI")
            dimensionStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, dimensionOptions)
            }

            val sourceWidth = dimensionOptions.outWidth
            val sourceHeight = dimensionOptions.outHeight
            if (sourceWidth <= 0 || sourceHeight <= 0) error("Picked URI is not a decodable image")

            val sampleSize = computeSampleSize(sourceWidth, sourceHeight)

            // Second pass: decode at the sampled resolution.
            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val sampledBitmap =
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: error("Could not decode attachment URI")

            // Exact scale to fit MAX_EDGE on the longest edge.
            val scaled = scaleToFit(sampledBitmap)
            if (scaled !== sampledBitmap) sampledBitmap.recycle()

            // Apply the EXIF rotation as a pixel transform. After this, the
            // bitmap is visually upright and the JPEG we produce will need no
            // EXIF tag.
            val rotated = applyExifRotation(scaled, orientation)
            if (rotated !== scaled) scaled.recycle()

            val baos = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            val width = rotated.width
            val height = rotated.height
            rotated.recycle()

            return Result(
                bytes = baos.toByteArray(),
                width = width,
                height = height,
                mimeType = "image/jpeg",
            )
        }

        private fun computeSampleSize(
            srcW: Int,
            srcH: Int,
        ): Int {
            var sample = 1
            var w = srcW
            var h = srcH
            while (w / 2 >= MAX_EDGE || h / 2 >= MAX_EDGE) {
                w /= 2
                h /= 2
                sample *= 2
            }
            return sample
        }

        private fun scaleToFit(bitmap: Bitmap): Bitmap {
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest <= MAX_EDGE) return bitmap
            val ratio = MAX_EDGE.toFloat() / longest
            val newW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        }

        private fun applyExifRotation(
            bitmap: Bitmap,
            orientation: Int,
        ): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> {
                    return bitmap
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    matrix.postRotate(90f)
                }

                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    matrix.postRotate(180f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    matrix.postRotate(270f)
                }

                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                    matrix.preScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.preScale(1f, -1f)
                }

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.preScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.preScale(-1f, 1f)
                }

                else -> {
                    return bitmap
                }
            }
            return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                // filter =
                true,
            )
        }

        private companion object {
            const val MAX_EDGE = 1920
            const val JPEG_QUALITY = 85
        }
    }
