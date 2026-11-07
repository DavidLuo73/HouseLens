package tw.houselens.viewer.data

interface HouseLensData {
    suspend fun properties(query: PropertyQuery, page: Int): PropertyPage
    suspend fun property(id: String): PropertyDto?
    suspend fun detail(id: String): PropertyDetails?
    suspend fun topRated(): List<TopGroupDto>
    suspend fun bigDrops(page: Int, district: String? = null): DropPage
    suspend fun districtStats(): List<DistrictStatsDto>
    suspend fun districtOptions(): List<DistrictOptionDto>
    suspend fun latestRun(): CrawlRunDto?
}
class ApiException(val status: Int?, val retryAfterSeconds: Long?, message: String) : Exception(message)
