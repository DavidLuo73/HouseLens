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
import tw.houselens.viewer.auth.DataStatusViewModel
import tw.houselens.viewer.auth.DataStatusSection
import tw.houselens.viewer.explore.*
import tw.houselens.viewer.districts.*
import tw.houselens.viewer.detail.*
import tw.houselens.viewer.find.*
import tw.houselens.viewer.saved.SavedScreen

@Composable
fun HouseLensNav(graph:AppGraph) {
    val session=graph.session
    val owner=session.owner
    key(session) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides session) {
            val nav=rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route=entry?.destination?.route
            val root=route==null || route in listOf("find","explore","saved","districts")
            Scaffold(topBar={ if(root) Row(Modifier.fillMaxWidth().statusBarsPadding(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick={ nav.navigate("info") { launchSingleTop=true } },modifier=Modifier.heightIn(min=48.dp)) { Text("資訊") }
            } },bottomBar={ if(root) NavigationBar {
                listOf("find" to "找房","explore" to "精選","saved" to "收藏","districts" to "區域").forEach { (destination,label) ->
                    NavigationBarItem(selected=(route ?: "find")==destination,onClick={ nav.navigate(destination) {
                        popUpTo(nav.graph.startDestinationId) { saveState=true };launchSingleTop=true;restoreState=true
                    } },icon={ Text(when(destination) { "find"->"⌂";"explore"->"☆";"saved"->"♡";else->"▥" }) },label={ Text(label) })
                }
            } }) { padding ->
                NavHost(nav,startDestination="find",modifier=Modifier.fillMaxSize().padding(if(root) padding else PaddingValues())) {
                    composable("find") { findEntry ->
                        val model:FindViewModel=viewModel(factory=viewModelFactory { initializer { session.find(createSavedStateHandle()) } })
                        LaunchedEffect(findEntry) {
                            findEntry.savedStateHandle.getStateFlow<String?>("analyticsDistrict",null).collect { district ->
                                if(district!=null) { model.setDistrictFromAnalytics(district);findEntry.savedStateHandle["analyticsDistrict"]=null }
                            }
                        }
                        FindScreen(model) { id,history -> nav.navigate("property/${Uri.encode(id)}?history=$history") }
                    }
                    composable("explore") {
                        val model:ExploreViewModel=viewModel(factory=viewModelFactory { initializer { ExploreViewModel(session.data) } })
                        ExploreScreen(model) { id,history -> nav.navigate("property/${Uri.encode(id)}?history=$history") }
                    }
                    composable("districts") {
                        val model:DistrictsViewModel=viewModel(factory=viewModelFactory { initializer { DistrictsViewModel(session.data) } })
                        DistrictsScreen(model) { district ->
                            nav.navigate("find") { popUpTo(nav.graph.startDestinationId) { saveState=true };launchSingleTop=true;restoreState=true }
                            nav.getBackStackEntry("find").savedStateHandle["analyticsDistrict"]=district
                        }
                    }
                    composable("saved") { SavedScreen(session.saved) { id,history -> nav.navigate("property/${Uri.encode(id)}?history=$history") } }
                    composable("property/{id}?history={history}",arguments=listOf(navArgument("id") { type=NavType.StringType },navArgument("history") { type=NavType.BoolType;defaultValue=false })) { backStack ->
                        val model:DetailViewModel=viewModel(factory=viewModelFactory { initializer { DetailViewModel(backStack.arguments!!.getString("id")!!,session.data,owner,session.favorites) } })
                        DetailScreen(model,backStack.arguments?.getBoolean("history")==true,{ nav.popBackStack() })
                    }
                    composable("info") {
                        val status:DataStatusViewModel=viewModel(factory=viewModelFactory { initializer { DataStatusViewModel(session.data) } })
                        Scaffold(topBar={ TextButton(onClick={ nav.popBackStack() },modifier=Modifier.statusBarsPadding().heightIn(min=48.dp)) { Text("返回") } }) { infoPadding ->
                            Column(Modifier.fillMaxSize().padding(infoPadding).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text("資訊",style=MaterialTheme.typography.headlineSmall)
                                Text("版本 ${BuildConfig.VERSION_NAME}")
                                Text("收藏僅儲存在此裝置，不跨裝置同步")
                                Text(stringResource(R.string.local_data))
                                DataStatusSection(status)
                            }
                        }
                    }
                }
            }
        }
    }
}