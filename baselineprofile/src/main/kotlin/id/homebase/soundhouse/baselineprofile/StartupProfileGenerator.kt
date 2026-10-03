package id.homebase.soundhouse.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Needs a signed-in id.homebase.soundhouse on the device to cover Home and Library; signed out it profiles login only.
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndTabs() = rule.collect(packageName = "id.homebase.soundhouse", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        val library = device.wait(Until.findObject(By.text("Library")), 15_000) ?: return@collect
        library.click()
        device.waitForIdle()
        device.findObject(By.text("Home"))?.click()
        device.waitForIdle()
    }
}
