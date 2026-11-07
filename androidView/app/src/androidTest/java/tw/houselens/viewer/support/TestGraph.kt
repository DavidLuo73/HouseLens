package tw.houselens.viewer.support

import androidx.compose.runtime.*
import tw.houselens.viewer.AppGraph
import tw.houselens.viewer.SessionResources
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import java.io.File
import java.math.BigDecimal
import tw.houselens.viewer.auth.OwnerKey
import tw.houselens.viewer.data.*
import tw.houselens.viewer.find.FilterPreferences
import tw.houselens.viewer.saved.*

class TestGraph:AppGraph {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val directory=File(context.cacheDir,"nav-${System.nanoTime()}")
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val owner=OwnerKey("https://test.supabase.co","member")
    val favorites=FavoriteStore(directory.resolve("saved"),scope)
    val requests=java.util.Collections.synchronizedList(mutableListOf<PropertyQuery>())
    var browseFail=false
    var latestRequests=0
    var detailRequests=0
    var fail=false
    var opened:String?=null
    val properties=(1..30).map { PropertyDto("$it",BigDecimal("1200.25"),title="房源 $it",district="中正區",address="臺北市中正區很長很長的完整地址一二三四五六七八九十號",areaPing=BigDecimal("28.5"),floor="3／5",ageYears=20,score=BigDecimal("0.9"),firstSeenAt="2026-01-01T00:00:00Z",lastSeenAt="2026-11-01T00:00:00Z") }
    val api=object:HouseLensData {
        override suspend fun properties(query:PropertyQuery,page:Int):PropertyPage {
            requests+=query;return PropertyPage(30,page,20,properties.drop((page-1)*20).take(20))
        }
        override suspend fun property(id:String)=properties.find { it.id==id }
        override suspend fun detail(id:String):PropertyDetails? {
            detailRequests++
            if(fail) throw java.io.IOException()
            return property(id)?.let { PropertyDetails(it,listOf(SourceDto(0,"javascript:alert(1)"),SourceDto(2,"https://example.com/listing/$id"),SourceDto(3,"http://example.org/listing/$id")),(1..45).map { n ->
                PriceEntryDto("e$n",java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(n*86400L).toString(),BigDecimal(1300-n),sourceSite=0,changePercent=BigDecimal("-0.01"))
            }) }
        }
        override suspend fun districtOptions()=listOf(DistrictOptionDto("臺北市","中正區"))
        override suspend fun districtStats():List<DistrictStatsDto> {
            if(browseFail) throw java.io.IOException("RAW SECRET")
            return listOf(DistrictStatsDto("中正區",1,BigDecimal("42"),BigDecimal("1200"),BigDecimal("1200"),listOf(PriceBucketDto("1000–1500萬",1)),listOf(TrendPointDto("2026-11-07",BigDecimal("42"))),false),DistrictStatsDto("空區"))
        }
        override suspend fun topRated():List<TopGroupDto> {
            if(browseFail) throw java.io.IOException("RAW SECRET")
            return listOf(TopGroupDto("中正區",(1..5).map { TopItemDto("中正區",it,"$it",BigDecimal("1200"),BigDecimal("0.9"),"精選房源 $it") }),TopGroupDto("空區",emptyList()))
        }
        override suspend fun bigDrops(page:Int,district:String?):DropPage {
            if(browseFail) throw java.io.IOException("RAW SECRET")
            return DropPage(21,(if(page==1) 1..20 else 21..21).map { DropDto("$it",BigDecimal("800"),if(it==1) null else BigDecimal("0.2"),"2026-01-01T00:00:00Z",1,"降價房源 $it",district ?: "中正區",originalPrice=BigDecimal("1000"),dropAmount=BigDecimal("200")) })
        }
        override suspend fun latestRun():CrawlRunDto? {
            latestRequests++
            return CrawlRunDto("r","2026-11-07T00:00:00Z",status=1,newCount=1,delistedCount=0,bigDropCount=0,sources=listOf(SourceResultDto(2,false,0)))
        }
    }
    override val session=SessionResources(context,owner,api,favorites,FilterPreferences(directory.resolve("filters"),scope))
    fun dispose() { session.close();scope.cancel();directory.deleteRecursively() }
}

fun captureScreenshot(name:String) {
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/$name").use {
        java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
    }
}