package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.boxplay.ui.theme.BoxPlayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MultitrackTransportBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enabledButtonsKeepTheirCallbacks() {
        var previewClicks = 0
        var exportClicks = 0
        compose.setContent {
            BoxPlayTheme {
                MultitrackTransportBar(true, true, false,
                    { previewClicks++ }, { exportClicks++ })
            }
        }
        compose.onNodeWithText("Tocar").performClick()
        compose.onNodeWithText("Exportar para Box").performClick()
        compose.runOnIdle {
            assertEquals(1, previewClicks)
            assertEquals(1, exportClicks)
        }
    }

    @Test fun disabledButtonsRemainDisabledWithLargeText() {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                BoxPlayTheme {
                    Box(Modifier.width(288.dp)) {
                        MultitrackTransportBar(false, false, false, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Tocar").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Exportar para Box").assertIsDisplayed().assertIsNotEnabled()
    }

    @Test fun activePreviewKeepsPauseAction() {
        var clicked = false
        compose.setContent {
            BoxPlayTheme {
                MultitrackTransportBar(true, true, true, { clicked = true }, {})
            }
        }
        compose.onNodeWithText("Pausar").performClick()
        compose.runOnIdle { assertEquals(true, clicked) }
    }
}

