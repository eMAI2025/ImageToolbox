package com.t8rin.imagetoolbox.lib.portrait_analysis.visual

import com.t8rin.imagetoolbox.lib.portrait_analysis.model.NormalizedPoint3D
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageRenderTransformTest {
    @Test
    fun portraitImageIsCenteredInLandscapePreview() {
        val transform = ImageRenderTransform.fit(1000, 2000, 1000, 1000)
        assertEquals(0.5f, transform.scale, 0.0001f)
        assertEquals(250f, transform.offsetX, 0.0001f)
        assertEquals(0f, transform.offsetY, 0.0001f)
        val center = transform.normalizedToPreview(NormalizedPoint3D(0.5f, 0.5f))
        assertEquals(500f, center.x, 0.0001f)
        assertEquals(500f, center.y, 0.0001f)
    }

    @Test
    fun normalizedCornersMapToRenderedImageRect() {
        val transform = ImageRenderTransform.fit(4000, 3000, 1000, 1000)
        val topLeft = transform.normalizedToPreview(NormalizedPoint3D(0f, 0f))
        val bottomRight = transform.normalizedToPreview(NormalizedPoint3D(1f, 1f))
        assertEquals(transform.offsetX, topLeft.x, 0.0001f)
        assertEquals(transform.offsetY, topLeft.y, 0.0001f)
        assertEquals(transform.offsetX + transform.renderedWidth, bottomRight.x, 0.0001f)
        assertEquals(transform.offsetY + transform.renderedHeight, bottomRight.y, 0.0001f)
    }
}
