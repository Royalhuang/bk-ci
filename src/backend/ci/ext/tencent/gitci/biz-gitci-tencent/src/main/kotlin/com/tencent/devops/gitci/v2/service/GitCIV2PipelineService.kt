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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.gitci.v2.service

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.redis.RedisLock
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.gitci.constant.GitCIConstant.DEVOPS_PROJECT_PREFIX
import com.tencent.devops.gitci.dao.GitPipelineResourceDao
import com.tencent.devops.gitci.pojo.GitCIBuildHistory
import com.tencent.devops.gitci.pojo.GitProjectPipeline
import com.tencent.devops.process.api.service.ServicePipelineResource
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GitCIV2PipelineService @Autowired constructor(
    private val dslContext: DSLContext,
    private val client: Client,
    private val pipelineResourceDao: GitPipelineResourceDao,
    private val gitCIV2DetailService: GitCIV2DetailService,
    private val scmService: ScmService,
    private val redisOperation: RedisOperation,
    private val websocketService: GitCIV2WebsocketService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(GitCIV2PipelineService::class.java)
        private val channelCode = ChannelCode.GIT
    }

    fun getPipelineList(
        userId: String,
        gitProjectId: Long,
        keyword: String?,
        page: Int?,
        pageSize: Int?
    ): Page<GitProjectPipeline> {
        logger.info("get pipeline list, gitProjectId: $gitProjectId")
        val pageNotNull = page ?: 1
        val pageSizeNotNull = pageSize ?: 10
        val limit = PageUtil.convertPageSizeToSQLLimit(pageNotNull, pageSizeNotNull)
        val pipelines = pipelineResourceDao.getPageByGitProjectId(
            dslContext = dslContext,
            gitProjectId = gitProjectId,
            keyword = keyword,
            offset = limit.offset,
            limit = limit.limit
        )
        if (pipelines.isEmpty()) return Page(
            count = 0L,
            page = pageNotNull,
            pageSize = pageSizeNotNull,
            totalPages = 0,
            records = emptyList()
        )
        val count = pipelineResourceDao.getPipelineCount(dslContext, gitProjectId)
        val latestBuilds =
            try {
                gitCIV2DetailService.batchGetBuildDetail(
                    userId = userId,
                    gitProjectId = gitProjectId,
                    buildIds = pipelines.map { it.latestBuildId }
                )
            } catch (e: Exception) {
                logger.info("getPipelineList batchGetBuildDetail error gitProjectId: $gitProjectId")
                emptyMap<String, GitCIBuildHistory>()
            }
        return Page(
            count = count.toLong(),
            page = pageNotNull,
            pageSize = pageSizeNotNull,
            totalPages = PageUtil.calTotalPage(pageSizeNotNull, count.toLong()),
            records = pipelines.map {
                GitProjectPipeline(
                    gitProjectId = gitProjectId,
                    pipelineId = it.pipelineId,
                    filePath = it.filePath,
                    displayName = it.displayName,
                    enabled = it.enabled,
                    creator = it.creator,
                    latestBuildInfo = latestBuilds[it.latestBuildId]
                )
            }
        )
    }

    fun getPipelineListWithoutHistory(
        userId: String,
        gitProjectId: Long
    ): List<GitProjectPipeline> {
        logger.info("get pipeline info list, gitProjectId: $gitProjectId")
        val pipelines = pipelineResourceDao.getAllByGitProjectId(
            dslContext = dslContext,
            gitProjectId = gitProjectId
        )
        return pipelines.map {
            GitProjectPipeline(
                gitProjectId = gitProjectId,
                pipelineId = it.pipelineId,
                filePath = it.filePath,
                displayName = it.displayName,
                enabled = it.enabled,
                creator = it.creator,
                latestBuildInfo = null
            )
        }
    }

    fun getPipelineListById(
        userId: String,
        gitProjectId: Long,
        pipelineId: String
    ): GitProjectPipeline? {
        logger.info("get pipeline: $pipelineId, gitProjectId: $gitProjectId")
        val pipeline = pipelineResourceDao.getPipelineById(
            dslContext = dslContext,
            gitProjectId = gitProjectId,
            pipelineId = pipelineId
        ) ?: return null
        return GitProjectPipeline(
            gitProjectId = gitProjectId,
            pipelineId = pipeline.pipelineId,
            filePath = pipeline.filePath,
            displayName = pipeline.displayName,
            enabled = pipeline.enabled,
            creator = pipeline.creator,
            latestBuildInfo = null
        )
    }

    fun enablePipeline(
        userId: String,
        gitProjectId: Long,
        pipelineId: String,
        enabled: Boolean
    ): Boolean {
        // 关闭流水线时同时关闭其定时触发任务
        val lock = getLock(gitProjectId = gitProjectId, pipelineId = pipelineId)
        try {
            lock.lock()
            val processClient = client.get(ServicePipelineResource::class)
            val model = getModel(processClient, userId, gitProjectId, pipelineId) ?: return false
            model.stages.first()
                .containers.first()
                .elements.filter { it.getClassType() == "timerTrigger" }
                .forEach { it.additionalOptions?.enable = enabled }
            val edited = saveModel(processClient, userId, gitProjectId, pipelineId, model)
            logger.info("gitProjectId: $gitProjectId enable pipeline[$pipelineId] to $enabled" +
                ", edit timerTrigger with $edited")
            return pipelineResourceDao.enablePipelineById(
                dslContext = dslContext,
                pipelineId = pipelineId,
                enabled = enabled
            ) == 1
            websocketService.pushPipelineWebSocket(gitProjectId.toString(), pipelineId, userId)
        } catch (e: Exception) {
            logger.error("gitProjectId: $gitProjectId enable pipeline[$pipelineId] to $enabled error ${e.message}")
            return false
        } finally {
            lock.unlock()
        }
    }

    fun getYamlByPipeline(
        gitProjectId: Long,
        pipelineId: String,
        ref: String
    ): String? {
        logger.info("get yaml by pipelineId:($pipelineId), ref: $ref")
        val filePath =
            pipelineResourceDao.getPipelineById(dslContext, gitProjectId, pipelineId)?.filePath ?: return null
        val token = try {
            scmService.getToken(gitProjectId.toString())
        } catch (e: Exception) {
            logger.error("getYamlByPipeline $pipelineId $ref can't get token")
            return null
        }
        return scmService.getYamlFromGit(
            token = token.accessToken,
            gitProjectId = gitProjectId.toString(),
            fileName = filePath,
            ref = ref,
            useAccessToken = true
        )
    }

    private fun getLock(gitProjectId: Long, pipelineId: String): RedisLock {
        return RedisLock(
            redisOperation = redisOperation,
            lockKey = "GITCI_PIPELINE_ENABLE_LOCK_${gitProjectId}_$pipelineId",
            expiredTimeInSeconds = 60L
        )
    }

    private fun getModel(
        processClient: ServicePipelineResource,
        userId: String,
        gitProjectId: Long,
        pipelineId: String
    ): Model? {
        try {
            val response =
                processClient.get(userId, "$DEVOPS_PROJECT_PREFIX$gitProjectId", pipelineId, channelCode)
            if (response.isNotOk()) {
                logger.error("get pipeline failed, msg: ${response.message}")
                return null
            }
            return response.data
        } catch (e: Exception) {
            logger.error("get pipeline failed, pipelineId: " +
                "$pipelineId, projectCode: $gitProjectId, error msg: ${e.message}")
            return null
        }
    }

    private fun saveModel(
        processClient: ServicePipelineResource,
        userId: String,
        gitProjectId: Long,
        pipelineId: String,
        model: Model
    ): Boolean? {
        try {
            val response = processClient.edit(
                userId = userId,
                projectId = "$DEVOPS_PROJECT_PREFIX$gitProjectId",
                pipelineId = pipelineId,
                pipeline = model,
                channelCode = channelCode
            )
            if (response.isNotOk()) {
                logger.error("edit pipeline failed, msg: ${response.message}")
                return null
            }
            return response.data
        } catch (e: Exception) {
            logger.error("edit pipeline failed, pipelineId: " +
                "$pipelineId, projectCode: $gitProjectId, error msg: ${e.message}")
            return null
        }
    }
}
