package com.apptracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apptracker.data.db.entity.DnsQueryEntity
import kotlinx.coroutines.flow.Flow

data class DomainCount(val domain: String, val queryCount: Int)
data class ResolverCount(val resolverIp: String, val queryCount: Int)

@Dao
interface DnsQueryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(query: DnsQueryEntity)

    @Query("SELECT * FROM dns_queries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentQueries(limit: Int = 300): Flow<List<DnsQueryEntity>>

    @Query("SELECT * FROM dns_queries WHERE isTracker = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getTrackerQueries(limit: Int = 200): Flow<List<DnsQueryEntity>>

    @Query("SELECT * FROM dns_queries WHERE appPackageName = '' ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentUnattributedQueries(limit: Int = 100): Flow<List<DnsQueryEntity>>

    @Query("SELECT COUNT(*) FROM dns_queries WHERE isTracker = 1 AND timestamp > :since")
    suspend fun countTrackerHitsSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM dns_queries WHERE timestamp > :since")
    suspend fun countTotalQueriesSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM dns_queries WHERE appPackageName = '' AND timestamp > :since")
    suspend fun countUnattributedSince(since: Long): Int

    @Query("SELECT COUNT(DISTINCT resolverIp) FROM dns_queries WHERE timestamp > :since AND resolverIp != ''")
    suspend fun countDistinctResolversSince(since: Long): Int

    @Query(
        "SELECT resolverIp, COUNT(*) as queryCount FROM dns_queries " +
            "WHERE timestamp > :since AND resolverIp != '' " +
            "GROUP BY resolverIp ORDER BY queryCount DESC LIMIT :limit"
    )
    suspend fun topResolversSince(since: Long, limit: Int = 5): List<ResolverCount>

    @Query(
        "SELECT COUNT(*) FROM dns_queries " +
            "WHERE timestamp > :since AND resolverIp != '' AND resolverIp NOT IN (:monitoredResolvers)"
    )
    suspend fun countNonMonitoredResolverQueriesSince(since: Long, monitoredResolvers: List<String>): Int

    @Query(
        "SELECT domain, COUNT(*) as queryCount FROM dns_queries " +
            "WHERE isTracker = 1 GROUP BY domain ORDER BY queryCount DESC LIMIT :limit"
    )
    suspend fun topTrackerDomains(limit: Int = 10): List<DomainCount>

    @Query(
        "SELECT domain, COUNT(*) as queryCount FROM dns_queries " +
            "WHERE appPackageName = :packageName GROUP BY domain ORDER BY queryCount DESC LIMIT :limit"
    )
    suspend fun topDomainsForPackage(packageName: String, limit: Int = 5): List<DomainCount>

    @Query(
        "SELECT COUNT(*) FROM dns_queries WHERE appPackageName = :packageName AND isTracker = 1"
    )
    suspend fun trackerQueryCountForPackage(packageName: String): Int

    @Query("DELETE FROM dns_queries WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
