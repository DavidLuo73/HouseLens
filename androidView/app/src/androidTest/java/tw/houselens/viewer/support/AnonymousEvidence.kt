package tw.houselens.viewer.support

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry

fun anonymousScreenshot(label: String) {
    if (InstrumentationRegistry.getArguments().getString("skipSyntheticScreenshots") == "true") return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val requested = when (InstrumentationRegistry.getArguments().getString("expectedOrientation")) {
        "portrait" -> "portrait"
        "landscape" -> "landscape"
        else -> "unspecified"
    }
    val actual = if (instrumentation.targetContext.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait"
    // /sdcard survives the connected-test runner uninstalling its synthetic target app.
    instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/anonymous-v1-$requested-$actual-$label.png").use {
        java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
    }
}
