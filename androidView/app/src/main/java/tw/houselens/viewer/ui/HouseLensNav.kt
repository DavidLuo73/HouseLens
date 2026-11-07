package tw.houselens.viewer.ui

import android.net.Uri
import tw.houselens.viewer.AppGraph
import tw.houselens.viewer.BuildConfig
import tw.houselens.viewer.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.detail.*
import tw.houselens.viewer.find.*
import tw.houselens.viewer.saved.SavedScreen

@Composable
fun HouseLensNav(graph:AppGraph,owner:OwnerKey) {
    val session=graph.authorizedSession(owner) ?: return
    key(owner,session.version) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
            val nav=rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route=entry?.destination?.route
            val root=route==null || route=="find" || route=="saved"
            val scope=rememberCoroutineScope()
            Scaffold(topBar={ if(root) Row(Modifier.fillMaxWidth().statusBarsPadding(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick={ nav.navigate("account") { launchSingleTop=true } },modifier=Modifier.heightIn(min=48.dp)) { Text("帳號") }
            } },bottomBar={ if(root) NavigationBar {
                listOf("find" to "找房","saved" to "收藏").forEach { (destination,label) ->
                    NavigationBarItem(selected=(route ?: "find")==destination,onClick={ nav.navigate(destination) {
                        popUpTo(nav.graph.startDestinationId) { saveState=true };launchSingleTop=true;restoreState=true
                    } },icon={ Text(if(destination=="find") "⌂" else "♡") },label={ Text(label) })
                }
            } }) { padding ->
                NavHost(nav,startDestination="find",modifier=Modifier.fillMaxSize().padding(if(root) padding else PaddingValues())) {
                    composable("find") {
                        val model:FindViewModel=viewModel(factory=viewModelFactory { initializer { session.find(createSavedStateHandle()) } })
                        FindScreen(model) { id,history -> nav.navigate("property/${Uri.encode(id)}?history=$history") }
                    }
                    composable("saved") { SavedScreen(session.saved) { id,history -> nav.navigate("property/${Uri.encode(id)}?history=$history") } }
                    composable("property/{id}?history={history}",arguments=listOf(navArgument("id") { type=NavType.StringType },navArgument("history") { type=NavType.BoolType;defaultValue=false })) { backStack ->
                        val model:DetailViewModel=viewModel(factory=viewModelFactory { initializer { DetailViewModel(backStack.arguments!!.getString("id")!!,session.data,owner,session.favorites) } })
                        DetailScreen(model,backStack.arguments?.getBoolean("history")==true,{ nav.popBackStack() })
                    }
                    composable("account") {
                        Scaffold(topBar={ TextButton(onClick={ nav.popBackStack() },modifier=Modifier.statusBarsPadding().heightIn(min=48.dp)) { Text("返回") } }) { accountPadding ->
                            Column(Modifier.fillMaxSize().padding(accountPadding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text("帳號",style=MaterialTheme.typography.headlineSmall)
                                Text(graph.accountEmail(owner).orEmpty())
                                Text("版本 ${BuildConfig.VERSION_NAME}")
                                Text("收藏僅儲存在此裝置、此帳號，不跨裝置同步")
                                Text(stringResource(R.string.auth_local_data))
                                Button(onClick={ scope.launch { graph.signOut() } },modifier=Modifier.heightIn(min=48.dp)) { Text("登出") }
                            }
                        }
                    }
                }
            }
        }
    }
}