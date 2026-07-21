/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.t8rin.imagetoolbox.lib.portrait_analysis.visual

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import kotlin.math.min

enum class PreviewContentScale {
    FIT
}

data class PixelPoint2D(
    val x: Float,
    val y: Float
) {
    init {
        require(x.isFinite())
        require(y.isFinite())
    }
}

data class PixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left.isFinite())
        require(top.isFinite())
        require(right.isFinite())
        require(bottom.isFinite())
        require(right >= left)
        require(bottom >= top)
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
}

/**
 * Single source of truth for mapping detector/source-image coordinates to a FIT preview.
 *
 * The same values must be used for the bitmap and every overlay primitive. The transform contains
 * no Android UI types, so its calculations are unit-testable and serializable to diagnostics.
 */
data class ImageRenderTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val previewWidth: Int,
    val previewHeight: Int,
    val contentScale: PreviewContentScale,
    val scale: Float,
    val renderedWidth: Float,
    val renderedHeight: Float,
    val offsetX: Float,
    val offsetY: Float
) {
    init {
        require(sourceWidth > 0)
        require(sourceHeight > 0)
        require(previewWidth > 0)
        require(previewHeight > 0)
        require(scale > 0f && scale.isFinite())
        require(renderedWidth > 0f && renderedWidth.isFinite())
        require(renderedHeight > 0f && renderedHeight.isFinite())
        require(offsetX.isFinite())
        require(offsetY.isFinite())
    }

    fun sourceToPreview(sourceX: Float, sourceY: Float): PixelPoint2D = PixelPoint2D(
        x = offsetX + sourceX * scale,
        y = offsetY + sourceY * scale
    )

    fun normalizedToSource(point: NormalizedPoint3D): PixelPoint2D = PixelPoint2D(
        x = point.x * sourceWidth,
        y = point.y * sourceHeight
    )

    fun normalizedToPreview(point: NormalizedPoint3D): PixelPoint2D {
        val source = normalizedToSource(point)
        return sourceToPreview(source.x, source.y)
    }

    fun normalizedRectToPreview(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): PixelRect {
        val first = normalizedToPreview(NormalizedPoint3D(left, top))
        val second = normalizedToPreview(NormalizedPoint3D(right, bottom))
        return PixelRect(first.x, first.y, second.x, second.y)
    }

    companion object {
        fun fit(
            sourceWidth: Int,
            sourceHeight: Int,
            previewWidth: Int,
            previewHeight: Int
        ): ImageRenderTransform {
            require(sourceWidth > 0)
            require(sourceHeight > 0)
            require(previewWidth > 0)
            require(previewHeight > 0)

            val scale = min(
                previewWidth.toFloat() / sourceWidth.toFloat(),
                previewHeight.toFloat() / sourceHeight.toFloat()
            )
            val renderedWidth = sourceWidth * scale
            val renderedHeight = sourceHeight * scale
            return ImageRenderTransform(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                contentScale = PreviewContentScale.FIT,
                scale = scale,
                renderedWidth = renderedWidth,
                renderedHeight = renderedHeight,
                offsetX = (previewWidth - renderedWidth) / 2f,
                offsetY = (previewHeight - renderedHeight) / 2f
            )
        }
    }
}
