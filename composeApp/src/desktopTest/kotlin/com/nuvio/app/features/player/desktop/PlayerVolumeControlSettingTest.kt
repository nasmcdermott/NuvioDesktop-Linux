package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.DesktopHudVolumeCommand
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.testing.runHeadlessProbe
import com.nuvio.app.testing.withHeadlessFixture
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The volume entry in Hidden Player Buttons, through a real store in an isolated fixture home. */
class PlayerVolumeControlSettingTest {
    private fun probe(case: String) = withHeadlessFixture {
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, case)
    }

    @Test fun defaultsToHiddenInFreshProfile() = probe("default")

    @Test fun offSurvivesFreshProcessesAndOnRestores() = withHeadlessFixture {
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "writeOff")
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "readOff")
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "writeOn")
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "readOn")
    }

    @Test fun hiddenButtonsEntryOwnsTheVolumeControl() = withHeadlessFixture {
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "hideThroughHiddenButtons")
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "readHidden")
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "showThroughHiddenButtons")
        runHeadlessProbe(it, PlayerVolumeControlSettingProbe::class.java, "readShown")
    }
}

internal object PlayerVolumeControlSettingProbe {
    @JvmStatic fun main(args: Array<String>) {
        PlayerSettingsRepository.ensureLoaded()
        val state = PlayerSettingsRepository.uiState
        when (args[1]) {
            "default" -> assertFalse(state.value.desktopVolumeControlEnabled)
            "writeOff" -> {
                PlayerSettingsRepository.setDesktopVolumeControlEnabled(false)
                assertFalse(state.value.desktopVolumeControlEnabled)
            }
            "readOff" -> assertFalse(state.value.desktopVolumeControlEnabled)
            "writeOn" -> {
                PlayerSettingsRepository.setDesktopVolumeControlEnabled(true)
                assertTrue(state.value.desktopVolumeControlEnabled)
            }
            "readOn" -> assertTrue(state.value.desktopVolumeControlEnabled)
            "hideThroughHiddenButtons" -> {
                PlayerSettingsRepository.setDesktopHudButtonHidden(DesktopHudVolumeCommand, true)
                assertFalse(state.value.desktopVolumeControlEnabled)
            }
            "readHidden" -> assertFalse(state.value.desktopVolumeControlEnabled)
            "showThroughHiddenButtons" -> {
                PlayerSettingsRepository.setDesktopHudButtonHidden(DesktopHudVolumeCommand, false)
                assertTrue(state.value.desktopVolumeControlEnabled)
            }
            "readShown" -> assertTrue(state.value.desktopVolumeControlEnabled)
            else -> error("unknown scenario ${args[1]}")
        }
    }
}
