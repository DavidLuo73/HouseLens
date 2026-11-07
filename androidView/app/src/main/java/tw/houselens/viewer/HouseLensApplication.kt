package tw.houselens.viewer

import android.app.Application

class HouseLensApplication : Application() {
    val graph by lazy { AppGraph(applicationContext) }
}
