package tw.houselens.probe

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.webkit.WebView

fun readDeviceSnapshot(context: Context): DeviceSnapshot {
    val power = context.getSystemService(PowerManager::class.java)
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    val webView = WebView.getCurrentWebViewPackage()
    return DeviceSnapshot(Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.ID,
        Build.VERSION.SECURITY_PATCH, webView?.packageName, webView?.versionName,
        when {
            network == null -> "OFFLINE"
            network.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            network.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            else -> "OTHER"
        }, context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }, power.isPowerSaveMode, power.isIgnoringBatteryOptimizations(context.packageName),
        if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else null)
}
