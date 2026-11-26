package com.petal.browser.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PetalMaterialShapesCatalogTest {

    @Test
    fun `catalog contains exactly 35 unique official shapes without Sun or Cookie10Sided`() {
        val all = PetalMaterialShapes.allShapes
        assertEquals(35, all.size)
        assertEquals(35, all.map { it.name }.toSet().size)
        assertEquals(35, all.map { it.polygon }.distinct().size)

        val names = all.map { it.name }.toSet()
        assertFalse(names.contains("Sun"))
        assertFalse(names.contains("Cookie 10-Sided"))
        assertFalse(names.contains("Cookie10Sided"))
    }

    @Test
    fun `every official polygon has non-empty cubics, valid normalized bounds, and safe shadow outline`() {
        val density = Density(1f, 1f)
        val sizes = listOf(Size(48f, 48f), Size(120f, 60f))
        for (holder in PetalMaterialShapes.allShapes) {
            val normalized = holder.polygon.normalized()
            assertTrue("Missing cubics for ${holder.name}", normalized.cubics.isNotEmpty())
            val bounds = normalized.calculateBounds()
            assertEquals("Bounds array length must be 4 for ${holder.name}", 4, bounds.size)
            val width = bounds[2] - bounds[0]
            val height = bounds[3] - bounds[1]
            assertTrue("Zero width for ${holder.name}", width > 0f)
            assertTrue("Zero height for ${holder.name}", height > 0f)

            assertNotNull(holder.toShape())
            val shadowShape = holder.toSafeShadowShape()
            for (size in sizes) {
                val outline = shadowShape.createOutline(size, LayoutDirection.Ltr, density)
                assertTrue("Expected convex rounded shadow outline for ${holder.name}", outline is Outline.Rounded)
            }
        }
    }

    @Test
    fun `resolveByName matches case-insensitively and maps legacy names without throwing`() {
        assertEquals(PetalMaterialShapes.Flower, PetalMaterialShapes.resolveByName("Flower"))
        assertEquals(PetalMaterialShapes.Flower, PetalMaterialShapes.resolveByName("flower"))
        assertEquals(PetalMaterialShapes.Sunny, PetalMaterialShapes.resolveByName("Sun"))
        assertEquals(PetalMaterialShapes.Sunny, PetalMaterialShapes.resolveByName("sun"))
        assertEquals(PetalMaterialShapes.Cookie9Sided, PetalMaterialShapes.resolveByName("Cookie10Sided"))
        assertEquals(PetalMaterialShapes.Cookie9Sided, PetalMaterialShapes.resolveByName("Cookie 10-Sided"))
        assertEquals(PetalMaterialShapes.Square, PetalMaterialShapes.resolveByName("RoundedSquare"))
        assertEquals(PetalMaterialShapes.ClamShell, PetalMaterialShapes.resolveByName("ClamShell"))
        assertNull(PetalMaterialShapes.resolveByName(null))
        assertNull(PetalMaterialShapes.resolveByName("non_existent_shape"))
    }

    @Test
    fun `five shape picker categories partition all 35 shapes without overlap`() {
        val partitioned = PetalShapeGroup.entries.flatMap { PetalMaterialShapes.shapesInGroup(it) }
        assertEquals(35, partitioned.size)
        assertEquals(PetalMaterialShapes.allShapes.toSet(), partitioned.toSet())
    }

    @Test
    fun `homescreenShapes and MaterialShapes alias contain only official shapes`() {
        assertEquals(PetalMaterialShapes.allShapes, MaterialShapes.allShapes)
        assertEquals(17, PetalMaterialShapes.homescreenShapes.size)
        assertEquals(17, PetalMaterialShapes.homescreenShapes.map { it.name }.toSet().size)
        assertTrue(PetalMaterialShapes.allShapes.containsAll(PetalMaterialShapes.homescreenShapes))
    }
}
