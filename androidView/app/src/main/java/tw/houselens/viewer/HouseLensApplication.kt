package tw.houselens.viewer

import android.app.Application

class HouseLensApplication : Application() {
    val graph by lazy { DefaultAppGraph(applicationContext) }
}
