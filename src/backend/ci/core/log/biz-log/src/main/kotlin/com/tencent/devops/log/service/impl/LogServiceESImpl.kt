/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.log.service.impl

import com.google.common.cache.CacheBuilder
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.event.pojo.pipeline.PipelineBuildFinishBroadCastEvent
import com.tencent.devops.common.log.pojo.EndPageQueryLogs
import com.tencent.devops.common.log.pojo.LogBatchEvent
import com.tencent.devops.common.log.pojo.LogEvent
import com.tencent.devops.common.log.pojo.LogLine
import com.tencent.devops.common.log.pojo.LogStatusEvent
import com.tencent.devops.common.log.pojo.PageQueryLogs
import com.tencent.devops.common.log.pojo.QueryLogs
import com.tencent.devops.common.log.pojo.enums.LogStatus
import com.tencent.devops.common.log.pojo.enums.LogType
import com.tencent.devops.common.log.pojo.message.LogMessage
import com.tencent.devops.common.log.pojo.message.LogMessageWithLineNo
import com.tencent.devops.common.log.utils.LogMQEventDispatcher
import com.tencent.devops.common.redis.RedisLock
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.log.client.LogClient
import com.tencent.devops.log.jmx.v2.CreateIndexBeanV2
import com.tencent.devops.log.jmx.v2.LogBeanV2
import com.tencent.devops.log.service.IndexService
import com.tencent.devops.log.service.LogService
import com.tencent.devops.log.service.LogStatusService
import com.tencent.devops.log.service.LogTagService
import com.tencent.devops.log.util.Constants
import com.tencent.devops.log.util.ESIndexUtils
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.admin.indices.open.OpenIndexRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.Operator
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import java.io.IOException
import java.sql.Date
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.StreamingOutput
import kotlin.math.ceil

class LogServiceESImpl constructor(
    private val client: LogClient,
    private val indexService: IndexService,
    private val logStatusService: LogStatusService,
    private val logTagService: LogTagService,
    private val createIndexBeanV2: CreateIndexBeanV2,
    private val logBeanV2: LogBeanV2,
    private val redisOperation: RedisOperation,
    private val logMQEventDispatcher: LogMQEventDispatcher
) : LogService {

    companion object {
        private val logger = LoggerFactory.getLogger(LogServiceESImpl::class.java)
    }

    private val indexCache = CacheBuilder.newBuilder()
        .maximumSize(100000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<String/*BuildId*/, Boolean/*Has create the index*/>()

    override fun pipelineFinish(event: PipelineBuildFinishBroadCastEvent) {
        with(event) {
            logger.info("[$projectId|$pipelineId|$buildId] build finish")
            indexService.flushLineNum2DB(buildId)
        }
    }

    override fun addLogEvent(event: LogEvent) {
        val logMessage = addLineNo(event.buildId, event.logs)
        if (logMessage.isNotEmpty()) {
            logMQEventDispatcher.dispatch(LogBatchEvent(event.buildId, logMessage))
        }
    }

    override fun addBatchLogEvent(event: LogBatchEvent) {
        val currentEpoch = System.currentTimeMillis()
        var success = false
        try {
            prepareIndex(event.buildId)
            val logMessages = event.logs
            val buf = mutableListOf<LogMessageWithLineNo>()
            logMessages.forEach {
                buf.add(it)
                if (buf.size == Constants.BULK_BUFFER_SIZE) {
                    if (doAddMultiLines(buf, event.buildId) == 0) {
                        throw Exception("None of lines is inserted successfully to ES [${event.buildId}|${event.retryTime}]")
                    } else {
                        buf.clear()
                    }
                }
            }
            if (buf.isNotEmpty()) {
                if (doAddMultiLines(buf, event.buildId) == 0) {
                    throw Exception("None of lines is inserted successfully to ES [${event.buildId}|${event.retryTime}]")
                }
            }
            success = true
        } finally {
            val elapse = System.currentTimeMillis() - currentEpoch
            logBeanV2.execute(elapse, success)
        }
    }

    override fun updateLogStatus(event: LogStatusEvent) {
        with(event) {
            logger.info("[$buildId|$tag|$subTag|$jobId|$executeCount|$finished] Start to update log status")
            logStatusService.finish(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount,
                finish = finished
            )
        }
    }

    override fun queryInitLogs(
        buildId: String,
        isAnalysis: Boolean,
        keywordsStr: String?,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?
    ): QueryLogs {
        val currentEpoch = System.currentTimeMillis()
        var success = false
        try {
            val index = indexService.getIndexName(buildId)
            val result = doQueryInitLogs(
                buildId = buildId,
                index = index,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
            success = logStatusSuccess(result.status)
            return result
        } finally {
            logBeanV2.query(System.currentTimeMillis() - currentEpoch, success)
        }
    }

    override fun queryMoreLogsBetweenLines(
        buildId: String,
        num: Int,
        fromStart: Boolean,
        start: Long,
        end: Long,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?
    ): QueryLogs {
        val startEpoch = System.currentTimeMillis()
        var success = false
        try {
            val index = indexService.getIndexName(buildId)

            val queryLogs = QueryLogs(
                buildId, getLogStatus(
                    buildId = buildId,
                    tag = tag,
                    subTag = subTag,
                    jobId = jobId,
                    executeCount = executeCount
                )
            )

            try {
                val query = getQuery(buildId, tag, subTag, jobId, executeCount)
                    .must(QueryBuilders.rangeQuery("lineNo").gte(start).lte(end))

                val searchRequest = SearchRequest(index)
                    .source(
                        SearchSourceBuilder()
                            .query(query)
                            .highlighter(
                                HighlightBuilder().preTags("\u001b[31m").postTags("\u001b[0m")
                                    .field("message").fragmentSize(100000)
                            )
                            .docValueField("lineNo")
                            .docValueField("timestamp")
                            .size(num)
                            .sort("lineNo", if (fromStart) SortOrder.ASC else SortOrder.DESC)
                            .timeout(TimeValue.timeValueSeconds(60))
                    )

                val searchResponse = try {
                    client.restClient(buildId).search(searchRequest, RequestOptions.DEFAULT)
                } catch (e: IOException) {
                    client.restClient(buildId).search(searchRequest, genLargeSearchOptions())
                }

                searchResponse.hits.forEach { searchHitFields ->
                    val sourceMap = searchHitFields.sourceAsMap
                    val logLine = LogLine(
                        lineNo = sourceMap["lineNo"].toString().toLong(),
                        timestamp = sourceMap["timestamp"].toString().toLong(),
                        message = sourceMap["message"].toString(),
                        priority = Constants.DEFAULT_PRIORITY_NOT_DELETED,
                        tag = sourceMap["tag"].toString(),
                        subTag = sourceMap["subTag"].toString(),
                        jobId = sourceMap["jobId"].toString()
                    )
                    queryLogs.logs.add(logLine)
                }
                if (!fromStart) {
                    queryLogs.logs.reverse()
                }
                if (queryLogs.logs.isEmpty()) queryLogs.status = LogStatus.EMPTY
                success = true
            } catch (e: ElasticsearchStatusException) {
                e.status()
                val exString = e.toString()
                if (exString.contains("index_closed_exception")) {
                    logger.error("[$buildId] Can't search because of index_closed_exception", e)
                    queryLogs.status = LogStatus.CLOSED
                }
            } catch (e: Exception) {
                logger.error("Query more logs between lines failed because of ${e.javaClass}. buildId: $buildId", e)
            }
            return queryLogs
        } finally {
            logBeanV2.query(System.currentTimeMillis() - startEpoch, success)
        }
    }

    override fun queryLogsAfterLine(
        buildId: String,
        start: Long,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?
    ): QueryLogs {
        val startEpoch = System.currentTimeMillis()
        var success = false
        try {
            val index = indexService.getIndexName(buildId)
            val result = doQueryLogsAfterLine(
                buildId = buildId,
                index = index,
                start = start,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
            success = logStatusSuccess(result.status)
            return result
        } finally {
            logBeanV2.query(System.currentTimeMillis() - startEpoch, success)
        }
    }

    override fun downloadLogs(
        pipelineId: String,
        buildId: String,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?,
        fileName: String?
    ): Response {

        val index = indexService.getIndexName(buildId)
        val query = getQuery(
            buildId = buildId,
            tag = tag,
            subTag = subTag,
            jobId = jobId,
            executeCount = executeCount
        )

        val scrollClient = client.restClient(buildId)
        val searchRequest = SearchRequest(index)
            .source(
                SearchSourceBuilder()
                    .query(query)
                    .docValueField("lineNo")
                    .docValueField("timestamp")
                    .size(Constants.MAX_LINES)
                    .sort("lineNo", SortOrder.ASC)
            )
            .scroll(TimeValue(1000 * 64))

        var scrollResp = try {
            scrollClient.search(searchRequest, RequestOptions.DEFAULT)
        } catch (e: IOException) {
            scrollClient.search(searchRequest, genLargeSearchOptions())
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS")
        // 一边读一边流式下载
        val fileStream = StreamingOutput { output ->
            do {
                val sb = StringBuilder()
                scrollResp.hits.hits.forEach { searchHit ->
                    val sourceMap = searchHit.sourceAsMap

                    val logLine = LogLine(
                        sourceMap["lineNo"].toString().toLong(),
                        sourceMap["timestamp"].toString().toLong(),
                        sourceMap["message"].toString().removePrefix("\u001b[31m").removePrefix("\u001b[1m").replace(
                            "\u001B[m",
                            ""
                        ).removeSuffix("\u001b[m"),
                        Constants.DEFAULT_PRIORITY_NOT_DELETED
                    )
                    val dateTime = sdf.format(Date(logLine.timestamp))
                    val str = "$dateTime : ${logLine.message}" + System.lineSeparator()
                    sb.append(str)
                }
                output.write(sb.toString().toByteArray())
                output.flush()
                scrollResp = scrollClient.scroll(
                    SearchScrollRequest(scrollResp.scrollId).scroll(TimeValue(1000 * 64)),
                    RequestOptions.DEFAULT
                )
            } while (scrollResp.hits.hits.isNotEmpty())
        }

        val resultName = fileName ?: "$pipelineId-$buildId-log"
        return Response
            .ok(fileStream, MediaType.APPLICATION_OCTET_STREAM_TYPE)
            .header("content-disposition", "attachment; filename = $resultName.log")
            .header("Cache-Control", "no-cache")
            .build()
    }

    override fun getEndLogs(
        pipelineId: String,
        buildId: String,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?,
        size: Int
    ): EndPageQueryLogs {
        val startEpoch = System.currentTimeMillis()
        var success = false
        try {
            val result = doGetEndLogs(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount,
                size = size
            )
            success = logStatusSuccess(result.status)
            return result
        } finally {
            logBeanV2.query(System.currentTimeMillis() - startEpoch, success)
        }
    }

    override fun queryInitLogsPage(
        buildId: String,
        isAnalysis: Boolean,
        keywordsStr: String?,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?,
        page: Int,
        pageSize: Int
    ): PageQueryLogs {
        val startEpoch = System.currentTimeMillis()
        var success = false
        var queryLogs = QueryLogs(
            buildId, getLogStatus(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
        )
        var logSize = 0L
        try {
            val index = indexService.getIndexName(buildId)
            queryLogs = queryInitLogsPage(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount,
                page = page,
                pageSize = pageSize
            )
            logSize = getLogSize(
                index = index,
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
            success = logStatusSuccess(queryLogs.status)
            if (queryLogs.logs.isEmpty()) queryLogs.status = LogStatus.EMPTY
        } catch (e: ElasticsearchStatusException) {
            val exString = e.toString()
            if (exString.contains("index_closed_exception")) {
                logger.error("[$buildId] Can't search because of index_closed_exception", e)
                queryLogs.status = LogStatus.CLOSED
            }
        } catch (e: Exception) {
            logger.error("Query init logs failed because of ${e.javaClass}. buildId: $buildId", e)
            queryLogs.status = LogStatus.FAIL
        } finally {
            logBeanV2.query(System.currentTimeMillis() - startEpoch, success)
        }

        return PageQueryLogs(
            buildId = queryLogs.buildId,
            finished = queryLogs.finished,
            logs = Page(
                count = logSize,
                page = page,
                pageSize = pageSize,
                totalPages = ceil((logSize + 0.0) / pageSize).toInt(),
                records = queryLogs.logs
            ),
            timeUsed = queryLogs.timeUsed,
            status = queryLogs.status
        )
    }

    override fun reopenIndex(buildId: String): Boolean {
        logger.info("Reopen Index - $buildId")
        val index = indexService.getIndexName(buildId)
        return openIndex(buildId, index)
    }

    private fun logStatusSuccess(logStatus: LogStatus) =
        (logStatus == LogStatus.EMPTY || logStatus == LogStatus.SUCCEED)

    private fun openIndex(buildId: String, index: String): Boolean {
        logger.info("[$buildId|$index] Start to open the index")
        return client.restClient(buildId).indices()
            .open(OpenIndexRequest(index), RequestOptions.DEFAULT)
            .isAcknowledged
    }

    private fun queryInitLogsPage(
        buildId: String,
        tag: String? = null,
        subTag: String? = null,
        jobId: String? = null,
        executeCount: Int?,
        page: Int,
        pageSize: Int
    ): QueryLogs {
        val queryLogs = QueryLogs(
            buildId, getLogStatus(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
        )
        val index = indexService.getIndexName(buildId)

        val boolQuery = QueryBuilders.boolQuery()
        if (page != -1 && pageSize != -1) {
            val endLineNo = pageSize * page
            val beginLineNo = endLineNo - pageSize + 1
            boolQuery.must(QueryBuilders.rangeQuery("lineNo").gte(beginLineNo).lte(endLineNo))
        }

        val query = getQuery(buildId, tag, subTag, jobId, executeCount)
            .must(boolQuery)

        val scrollClient = client.restClient(buildId)
        val searchRequest = SearchRequest(index)
            .source(
                SearchSourceBuilder()
                    .query(query)
                    .docValueField("lineNo")
                    .docValueField("timestamp")
                    .size(pageSize)
                    .sort("lineNo", SortOrder.ASC)
                    .timeout(TimeValue.timeValueSeconds(60))
            )
            .scroll(TimeValue(1000 * 64))

        var searchResponse = try {
            client.restClient(buildId).search(searchRequest, RequestOptions.DEFAULT)
        } catch (e: IOException) {
            client.restClient(buildId).search(searchRequest, genLargeSearchOptions())
        }
        do {
            searchResponse.hits.hits.forEach { searchHit ->
                val sourceMap = searchHit.sourceAsMap
                val logType = sourceMap["logType"].toString()
                val logLine = LogLine(
                    sourceMap["lineNo"].toString().toLong(),
                    sourceMap["timestamp"].toString().toLong(),
                    if (logType == LogType.LOG.name) sourceMap["message"].toString() else "",
                    Constants.DEFAULT_PRIORITY_NOT_DELETED
                )
                queryLogs.logs.add(logLine)
            }
            searchResponse = scrollClient.scroll(
                SearchScrollRequest(searchResponse.scrollId).scroll(TimeValue(1000 * 64)),
                RequestOptions.DEFAULT
            )
        } while (searchResponse.hits.hits.isNotEmpty())

        if (queryLogs.logs.isEmpty()) queryLogs.status = LogStatus.EMPTY
        return queryLogs
    }

    private fun doGetEndLogs(
        buildId: String,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?,
        size: Int
    ): EndPageQueryLogs {
        val queryLogs = EndPageQueryLogs(buildId)
        try {
            val beginTime = System.currentTimeMillis()
            val index = indexService.getIndexName(buildId)
            val logSize = getLogSize(
                index = index,
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
            if (logSize == 0L) return queryLogs
            val start = if (logSize > size.toLong()) logSize - size.toLong() else 0L
            val query = getQuery(buildId, tag, subTag, jobId, executeCount)
                .must(QueryBuilders.rangeQuery("lineNo").gte(start))
            val searchRequest = SearchRequest(index)
                .source(
                    SearchSourceBuilder()
                        .query(query)
                        .docValueField("lineNo")
                        .docValueField("timestamp")
                        .size(size)
                        .sort("lineNo", SortOrder.ASC)
                        .timeout(TimeValue.timeValueSeconds(60))
                )
                .scroll(TimeValue(1000 * 32))

            val searchResponse = try {
                client.restClient(buildId).search(searchRequest, RequestOptions.DEFAULT)
            } catch (ex: IOException) {
                client.restClient(buildId).search(searchRequest, genLargeSearchOptions())
            }

            val logs = mutableListOf<LogLine>()
            searchResponse.hits.hits.forEach { searchHit ->
                val sourceMap = searchHit.sourceAsMap
                val logLine = LogLine(
                    lineNo = sourceMap["lineNo"].toString().toLong(),
                    timestamp = sourceMap["timestamp"].toString().toLong(),
                    message = sourceMap["message"].toString(),
                    priority = Constants.DEFAULT_PRIORITY_NOT_DELETED,
                    tag = sourceMap["tag"].toString(),
                    subTag = sourceMap["subTag"].toString(),
                    jobId = sourceMap["jobId"].toString(),
                    executeCount = sourceMap["executeCount"]?.toString()?.toInt() ?: 1
                )
                logs.add(logLine)
            }
            return EndPageQueryLogs(
                buildId = buildId,
                startLineNo = logs.lastOrNull()?.lineNo ?: 0,
                endLineNo = logs.firstOrNull()?.lineNo ?: 0,
                logs = logs,
                timeUsed = System.currentTimeMillis() - beginTime
            )
        } catch (e: ElasticsearchStatusException) {
            val exString = e.toString()
            if (exString.contains("index_closed_exception")) {
                logger.error("[$buildId] Can't search because of index_closed_exception", e)
                queryLogs.status = LogStatus.CLOSED
            }
        } catch (e: Exception) {
            logger.error("Query end logs failed because of ${e.javaClass}. buildId: $buildId", e)
            queryLogs.status = LogStatus.FAIL
        }
        return queryLogs
    }

    private fun doQueryInitLogs(
        buildId: String,
        index: String,
        tag: String? = null,
        subTag: String? = null,
        jobId: String? = null,
        executeCount: Int?
    ): QueryLogs {
        logger.info("[$index|$buildId|$tag|$subTag|$jobId|$executeCount] doQueryInitLogs")
        val logStatus = if (tag == null && jobId != null) getLogStatus(
            buildId = buildId,
            tag = jobId,
            subTag = null,
            jobId = null,
            executeCount = executeCount
        ) else getLogStatus(
            buildId = buildId,
            tag = tag,
            subTag = subTag,
            jobId = jobId,
            executeCount = executeCount
        )

        val subTags = if (tag.isNullOrBlank()) null else logTagService.getSubTags(buildId, tag!!)
        val queryLogs = QueryLogs(buildId = buildId, finished = logStatus, subTags = subTags)

        try {
            val logSize = getLogSize(
                index = index,
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
            if (logSize == 0L) return queryLogs

            val startTime = System.currentTimeMillis()
            val boolQueryBuilder = getQuery(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
            logger.info("Get the query builder: $boolQueryBuilder")

            val searchRequest = SearchRequest(index)
                .source(
                    SearchSourceBuilder()
                        .query(boolQueryBuilder)
                        .docValueField("lineNo")
                        .docValueField("timestamp")
                        .size(10000)
                        .sort("lineNo", SortOrder.ASC)
                        .timeout(TimeValue.timeValueSeconds(60))
                )

            val searchResponse = try {
                client.restClient(buildId).search(searchRequest, RequestOptions.DEFAULT)
            } catch (e: IOException) {
                client.restClient(buildId).search(searchRequest, genLargeSearchOptions())
            }

            searchResponse.hits.forEach { searchHitFields ->
                val sourceMap = searchHitFields.sourceAsMap
                val ln = sourceMap["lineNo"].toString().toLong()
                val t = sourceMap["tag"]?.toString() ?: ""
                val logLine = LogLine(
                    lineNo = ln,
                    timestamp = sourceMap["timestamp"].toString().toLong(),
                    message = sourceMap["message"].toString(),
                    priority = Constants.DEFAULT_PRIORITY_NOT_DELETED,
                    tag = t,
                    subTag = sourceMap["subTag"]?.toString() ?: "",
                    jobId = sourceMap["jobId"]?.toString() ?: "",
                    executeCount = sourceMap["executeCount"]?.toString()?.toInt() ?: 1
                )
                queryLogs.logs.add(logLine)
            }
            logger.info("logs query time cost: ${System.currentTimeMillis() - startTime}")
            if (queryLogs.logs.isEmpty()) queryLogs.status = LogStatus.EMPTY
            queryLogs.hasMore = logSize > queryLogs.logs.size
        } catch (e: ElasticsearchStatusException) {
            val exString = e.toString()
            if (exString.contains("index_closed_exception")) {
                logger.error("[$buildId] Can't search because of index_closed_exception", e)
                queryLogs.status = LogStatus.CLOSED
            }
        } catch (e: Exception) {
            logger.error("Query init logs failed because of ${e.javaClass}. buildId: $buildId", e)
            queryLogs.status = LogStatus.FAIL
        }
        return queryLogs
    }

    private fun doQueryLogsAfterLine(
        buildId: String,
        index: String,
        start: Long,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?
    ): QueryLogs {
        logger.info("[$index|$buildId|$tag|$subTag|$jobId|$executeCount] doQueryLargeLogsAfterLine")
        val logStatus = if (tag == null && jobId != null) {
            getLogStatus(
                buildId = buildId,
                tag = jobId,
                subTag = null,
                jobId = null,
                executeCount = executeCount
            )
        } else {
            getLogStatus(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            )
        }

        val subTags = if (tag.isNullOrBlank()) null else logTagService.getSubTags(buildId, tag!!)
        val queryLogs = QueryLogs(buildId = buildId, finished = logStatus, subTags = subTags)

        try {
            val startTime = System.currentTimeMillis()
            val logSize = getLogSize(
                index = index,
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount,
                start = start
            )
            if (logSize == 0L) return queryLogs
            val boolQueryBuilder = getQuery(
                buildId = buildId,
                tag = tag,
                subTag = subTag,
                jobId = jobId,
                executeCount = executeCount
            ).must(QueryBuilders.rangeQuery("lineNo").gte(start))

            val searchRequest = SearchRequest(index)
                .source(
                    SearchSourceBuilder()
                        .query(boolQueryBuilder)
                        .docValueField("lineNo")
                        .docValueField("timestamp")
                        .size(Constants.MAX_LINES)
                        .sort("lineNo", SortOrder.ASC)
                )
                .scroll(TimeValue(1000 * 64))
            val scrollClient = client.restClient(buildId)

            // 初始化请求
            val searchResponse = try {
                scrollClient.search(searchRequest, RequestOptions.DEFAULT)
            } catch (e: IOException) {
                scrollClient.search(searchRequest, genLargeSearchOptions())
            }

            var scrollId = searchResponse.scrollId
            var hits = searchResponse.hits

            // 开始滚动
            var times = 1
            do {
                hits.forEach { searchHitFields ->
                    val sourceMap = searchHitFields.sourceAsMap
                    val ln = sourceMap["lineNo"].toString().toLong()
                    val t = sourceMap["tag"]?.toString() ?: ""
                    val logLine = LogLine(
                        lineNo = ln,
                        timestamp = sourceMap["timestamp"].toString().toLong(),
                        message = sourceMap["message"].toString(),
                        priority = Constants.DEFAULT_PRIORITY_NOT_DELETED,
                        tag = t,
                        subTag = sourceMap["subTag"]?.toString() ?: "",
                        jobId = sourceMap["jobId"]?.toString() ?: "",
                        executeCount = sourceMap["executeCount"]?.toString()?.toInt() ?: 1
                    )
                    queryLogs.logs.add(logLine)
                }
                times++
                val scrollRequest = SearchScrollRequest(scrollId).scroll(TimeValue(1000 * 64))
                val searchScrollResponse = try {
                    scrollClient.scroll(scrollRequest, RequestOptions.DEFAULT)
                } catch (e: IOException) {
                    scrollClient.scroll(scrollRequest, genLargeSearchOptions())
                }
                scrollId = searchScrollResponse.scrollId
                hits = searchScrollResponse.hits
            } while (hits.hits.isNotEmpty() && times <= Constants.SCROLL_MAX_TIMES)
            queryLogs.hasMore = logSize > queryLogs.logs.size
            logger.info("logs query time cost: ${System.currentTimeMillis() - startTime}")
        } catch (e: ElasticsearchStatusException) {
            val exString = e.toString()
            if (exString.contains("index_closed_exception")) {
                logger.error("[$buildId] Can't search because of index_closed_exception", e)
                queryLogs.status = LogStatus.CLOSED
            }
        } catch (e: Exception) {
            logger.error("Query after logs failed because of ${e.javaClass}. buildId: $buildId", e)
            queryLogs.status = LogStatus.FAIL
            queryLogs.finished = true
            queryLogs.hasMore = false
        }
        return queryLogs
    }

    private fun getLogStatus(
        buildId: String,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?
    ): Boolean {
        return logStatusService.isFinish(
            buildId = buildId,
            tag = tag,
            subTag = subTag,
            jobId = jobId,
            executeCount = executeCount
        )
    }

    private fun getLogSize(
        index: String,
        buildId: String,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?,
        start: Long? = null
    ): Long {
        val query = getQuery(buildId, tag, subTag, jobId, executeCount)
        if (start != null) query.must(QueryBuilders.rangeQuery("lineNo").gte(start))
        val countRequest = CountRequest(index).source(SearchSourceBuilder().query(query))
        val countResponse = client.restClient(buildId).count(countRequest, RequestOptions.DEFAULT)
        return countResponse.count
    }

    private fun doAddMultiLines(logMessages: List<LogMessageWithLineNo>, buildId: String): Int {

        val index = indexService.getIndexName(buildId)

        var lines = 0
        val bulkRequest = BulkRequest()
        for (i in logMessages.indices) {
            val logMessage = logMessages[i]

            val indexRequest = genIndexRequest(
                buildId = buildId,
                logMessage = logMessage,
                index = index
            )
            if (indexRequest != null) {
                bulkRequest.add(indexRequest)
                lines++
            }
        }
        try {
            // 注意，在 bulk 下，TypeMissingException 不会抛出，需要判断 bulkResponse.hasFailures() 抛出
            val bulkResponse = client.restClient(buildId).bulk(bulkRequest, RequestOptions.DEFAULT)
            return if (bulkResponse.hasFailures()) {
                throw Exception(bulkResponse.buildFailureMessage())
            } else {
                lines
            }
        } catch (ex: Exception) {
            val exString = ex.toString()
            if (exString.contains("circuit_breaking_exception")) {
                logger.error(
                    "[$buildId] Add bulk lines failed because of circuit_breaking_exception, attempting to add index. [$logMessages]",
                    ex
                )
                val bulkResponse = client.restClient(buildId)
                    .bulk(bulkRequest.timeout(TimeValue.timeValueSeconds(60)), genLargeSearchOptions())
                return if (bulkResponse.hasFailures()) {
                    logger.error(bulkResponse.buildFailureMessage())
                    0
                } else {
                    lines
                }
            } else {
                logger.error("[$buildId] Add bulk lines failed because of unknown Exception. [$logMessages]", ex)
                throw ex
            }
        }
    }

    private fun genIndexRequest(
        buildId: String,
        logMessage: LogMessageWithLineNo,
        index: String
    ): IndexRequest? {
        val builder = ESIndexUtils.getDocumentObject(buildId, logMessage)
        return try {
            IndexRequest(index).source(builder)
        } catch (e: IOException) {
            logger.error("[$buildId] Convert logMessage to es document failure", e)
            null
        } finally {
            builder.close()
        }
    }

    private fun addLineNo(buildId: String, logMessages: List<LogMessage>): List<LogMessageWithLineNo> {
        val lineNum = indexService.getAndAddLineNum(buildId, logMessages.size)
        if (lineNum == null) {
            logger.error("Got null logIndex from indexService, buildId: $buildId")
            return emptyList()
        }

        var startLineNum: Long = lineNum
        return logMessages.map {
            val timestamp = if (it.timestamp == 0L) {
                System.currentTimeMillis()
            } else {
                it.timestamp
            }
            if (!it.subTag.isNullOrBlank()) {
                logTagService.saveSubTag(buildId, it.tag, it.subTag!!)
            }
            LogMessageWithLineNo(
                tag = it.tag,
                subTag = it.subTag,
                jobId = it.jobId,
                message = it.message,
                timestamp = timestamp,
                logType = it.logType,
                lineNo = startLineNum++,
                executeCount = it.executeCount
            )
        }
    }

    private fun prepareIndex(buildId: String): Boolean {
        val index = indexService.getIndexName(buildId)
        return if (!checkIndexCreate(buildId, index)) {
            createIndex(buildId, index)
            indexCache.put(index, true)
            true
        } else {
            true
        }
    }

    private fun checkIndexCreate(buildId: String, index: String): Boolean {
        if (indexCache.getIfPresent(index) == true) {
            return true
        }
        val redisLock = RedisLock(redisOperation, "LOG:index:create:lock:key:$index", 10)
        try {
            redisLock.lock()
            if (indexCache.getIfPresent(index) == true) {
                return true
            }

            // Check from ES
            if (isExistIndex(buildId, index)) {
                logger.info("[$buildId|$index] the index is already created")
                indexCache.put(index, true)
                return true
            }
            return false
        } finally {
            redisLock.unlock()
        }
    }

    private fun createIndex(buildId: String, index: String): Boolean {
        logger.info("[$index] Create index")
        var success = false
        val startEpoch = System.currentTimeMillis()
        return try {
            logger.info("[$index] Start to create the index")
            val request = CreateIndexRequest(index)
                .settings(ESIndexUtils.getIndexSettings())
                .mapping(ESIndexUtils.getTypeMappings())
            request.setTimeout(TimeValue.timeValueSeconds(30))
            val response = client.restClient(buildId).indices()
                .create(request, RequestOptions.DEFAULT)
            success = true
            response.isShardsAcknowledged
        } catch (e: IOException) {
            logger.error("Create index $index failure", e)
            return false
        } finally {
            createIndexBeanV2.execute(System.currentTimeMillis() - startEpoch, success)
        }
    }

    private fun isExistIndex(buildId: String, index: String): Boolean {
        val request = GetIndexRequest(index)
        request.setTimeout(TimeValue.timeValueSeconds(30))
        return client.restClient(buildId).indices()
            .exists(request, RequestOptions.DEFAULT)
    }

    private fun getQuery(
        buildId: String,
        tag: String?,
        subTag: String?,
        jobId: String?,
        executeCount: Int?
    ): BoolQueryBuilder {
        val query = QueryBuilders.boolQuery()
        if (!tag.isNullOrBlank()) {
            query.must(QueryBuilders.matchQuery("tag", tag).operator(Operator.AND))
        }
        if (!subTag.isNullOrBlank()) {
            query.must(QueryBuilders.matchQuery("subTag", subTag).operator(Operator.AND))
        }
        if (!jobId.isNullOrBlank()) {
            query.must(QueryBuilders.matchQuery("jobId", jobId).operator(Operator.AND))
        }
        query.must(QueryBuilders.matchQuery("executeCount", executeCount ?: 1).operator(Operator.AND))
            .must(QueryBuilders.matchQuery("buildId", buildId).operator(Operator.AND))
        return query
    }

    private fun genLargeSearchOptions(): RequestOptions {
        val builder = RequestOptions.DEFAULT.toBuilder()
        builder.setHttpAsyncResponseConsumerFactory(HeapBufferedResponseConsumerFactory(Constants.RESPONSE_ENTITY_MAX_SIZE))
        return builder.build()
    }
}
