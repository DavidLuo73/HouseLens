package tw.houselens.probe

import java.math.BigDecimal

fun testQuery(site: Site, district: String = "中和區") =
    ProbeQuery(site, "新北市", district, BigDecimal("100000"))

fun fixtureText(path: String): String = checkNotNull(object {}.javaClass.classLoader?.getResource(path))
    .readText(Charsets.UTF_8)

fun captureFixture(source: SourceAdapter, query: ProbeQuery, path: String, jsonUrl: String? = null) =
    RawCapture(source.request(query, 0).url, 200,
        if (jsonUrl == null) fixtureText(path) else null,
        if (jsonUrl == null) emptyList() else listOf(CapturedJson(jsonUrl, fixtureText(path))),
        1, false)
