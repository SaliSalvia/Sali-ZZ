package com.salizzai.app

import com.salizzai.app.data.model.AvailableModels
import com.salizzai.app.data.model.ReasoningEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun `glm 5_3 cannot disable reasoning`() {
        // Z.ai returns an error for thinking.type="disabled" on the 5.3 family,
        // so the UI must never offer an off switch for these models.
        assertTrue(AvailableModels.requiresThinking("glm-5.3"))
        assertTrue(AvailableModels.requiresThinking("glm-5.3-flash"))
        assertFalse(AvailableModels.byId("glm-5.3")!!.canToggleThinking)
    }

    @Test
    fun `glm 5_2 keeps an optional thinking switch`() {
        assertFalse(AvailableModels.requiresThinking("glm-5.2"))
        assertTrue(AvailableModels.byId("glm-5.2")!!.supportsThinking)
        assertTrue(AvailableModels.byId("glm-5.2")!!.canToggleThinking)
    }

    @Test
    fun `glm 5_3 is text only while flash handles images`() {
        assertFalse(AvailableModels.supportsVision("glm-5.3"))
        assertFalse(AvailableModels.supportsVision("glm-5.2"))
        assertTrue(AvailableModels.supportsVision("glm-5.3-flash"))
        assertTrue(AvailableModels.byId("glm-5.3-flash")!!.supportsFile)
    }

    @Test
    fun `pickForVision falls back to flash for text only models`() {
        assertEquals("glm-5.3-flash", AvailableModels.pickForVision("glm-5.3"))
        assertEquals("glm-5.3-flash", AvailableModels.pickForVision("glm-5.2"))
        assertEquals("glm-5.3-flash", AvailableModels.pickForVision("glm-5.3-flash"))
        assertEquals("glm-5v-turbo", AvailableModels.pickForVision("glm-5v-turbo"))
    }

    @Test
    fun `bestAvailable honours the preference order`() {
        assertEquals(
            "glm-5.3",
            AvailableModels.bestAvailable(listOf("glm-4.5-flash", "glm-5.3", "glm-5.2"))
        )
        assertEquals(
            "glm-5.3-flash",
            AvailableModels.bestAvailable(listOf("glm-5.3-flash", "glm-5.3-flashx"))
        )
        // Unknown ids still resolve to something usable.
        assertEquals("some-new-model", AvailableModels.bestAvailable(listOf("some-new-model")))
        assertEquals(null, AvailableModels.bestAvailable(emptyList()))
    }

    @Test
    fun `catalog deduplicates remote ids and keeps priority order first`() {
        val catalog = AvailableModels.catalog(setOf("glm-5.3", "brand-new-model"))
        val ids = catalog.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals("glm-5.3", ids.first())
        assertTrue(ids.contains("brand-new-model"))
        assertNotNull(catalog.first { it.id == "brand-new-model" }.badge)
    }

    @Test
    fun `reasoning effort parses known and unknown values`() {
        assertEquals(ReasoningEffort.LOW, ReasoningEffort.from("low"))
        assertEquals(ReasoningEffort.HIGH, ReasoningEffort.from("HIGH"))
        assertEquals(ReasoningEffort.MAX, ReasoningEffort.from("max"))
        assertEquals(ReasoningEffort.MAX, ReasoningEffort.from("nonsense"))
        assertEquals(ReasoningEffort.MAX, ReasoningEffort.from(null))
    }

    @Test
    fun `normalizeId handles spaced and mixed case ids`() {
        assertEquals("glm-5.3-flash", AvailableModels.normalizeId("  GLM 5.3 Flash "))
        assertEquals("glm-5.3", AvailableModels.normalizeId("GLM-5.3"))
    }
}
