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

package com.tencent.devops.process.engine.service

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.Container
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.container.TriggerContainer
import com.tencent.devops.common.pipeline.container.VMBuildContainer
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.utils.ModelUtils
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.process.dao.BuildDetailDao
import com.tencent.devops.process.engine.dao.PipelineBuildDao
import com.tencent.devops.process.engine.dao.PipelineBuildSummaryDao
import com.tencent.devops.process.engine.service.detail.BaseBuildDetailService
import com.tencent.devops.process.pojo.BuildStageStatus
import com.tencent.devops.process.pojo.VmInfo
import com.tencent.devops.process.pojo.pipeline.ModelDetail
import com.tencent.devops.process.utils.PipelineVarUtil
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Suppress("LongParameterList", "ComplexMethod", "ReturnCount")
@Service
class PipelineBuildDetailService @Autowired constructor(
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val pipelineBuildSummaryDao: PipelineBuildSummaryDao,
    dslContext: DSLContext,
    pipelineBuildDao: PipelineBuildDao,
    buildDetailDao: BuildDetailDao,
    redisOperation: RedisOperation,
    pipelineEventDispatcher: PipelineEventDispatcher
) : BaseBuildDetailService(
    dslContext,
    pipelineBuildDao,
    buildDetailDao,
    pipelineEventDispatcher,
    redisOperation
) {

    companion object {
        val logger = LoggerFactory.getLogger(PipelineBuildDetailService::class.java)!!
    }

    /**
     * 查询ModelDetail
     * @param buildId: 构建Id
     * @param refreshStatus: 是否刷新状态
     */
    fun get(buildId: String, refreshStatus: Boolean = true): ModelDetail? {

        val record = buildDetailDao.get(dslContext, buildId) ?: return null

        val buildInfo = pipelineBuildDao.convert(pipelineBuildDao.getBuildInfo(dslContext, buildId)) ?: return null

        val latestVersion = pipelineRepositoryService.getPipelineInfo(buildInfo.pipelineId)?.version ?: -1

        val buildSummaryRecord = pipelineBuildSummaryDao.get(dslContext, buildInfo.pipelineId)

        val model = JsonUtil.to(record.model, Model::class.java)

        // 判断需要刷新状态，目前只会改变canRetry & canSkip 状态
        if (refreshStatus) {
            if (buildInfo.status.isFailure() || buildInfo.status.isCancel()) { // 已经失败或者取消(终态)
                ModelUtils.refreshCanRetry(model)
            }
        }

        val triggerContainer = model.stages[0].containers[0] as TriggerContainer
        val buildNo = triggerContainer.buildNo
        if (buildNo != null) {
            buildNo.buildNo = buildSummaryRecord?.buildNo ?: buildNo.buildNo
        }
        val params = triggerContainer.params
        val newParams = ArrayList<BuildFormProperty>(params.size)
        params.forEach {
            // 变量名从旧转新: 兼容从旧入口写入的数据转到新的流水线运行
            val newVarName = PipelineVarUtil.oldVarToNewVar(it.id)
            if (!newVarName.isNullOrBlank()) newParams.add(it.copy(id = newVarName)) else newParams.add(it)
        }
        triggerContainer.params = newParams

        return ModelDetail(
            id = record.buildId,
            pipelineId = buildInfo.pipelineId,
            pipelineName = model.name,
            userId = record.startUser ?: "",
            trigger = StartType.toReadableString(buildInfo.trigger, buildInfo.channelCode),
            startTime = record.startTime?.timestampmilli() ?: LocalDateTime.now().timestampmilli(),
            endTime = record.endTime?.timestampmilli(),
            status = record.status ?: "",
            model = model,
            currentTimestamp = System.currentTimeMillis(),
            buildNum = buildInfo.buildNum,
            cancelUserId = record.cancelUser ?: "",
            curVersion = buildInfo.version,
            latestVersion = latestVersion,
            latestBuildNum = buildSummaryRecord?.buildNum ?: -1,
            executeTime = buildInfo.executeTime
        )
    }

    fun updateModel(buildId: String, model: Model) {
        buildDetailDao.update(
            dslContext = dslContext,
            buildId = buildId,
            model = JsonUtil.getObjectMapper().writeValueAsString(model),
            buildStatus = BuildStatus.RUNNING
        )
        pipelineDetailChangeEvent(buildId)
    }

    fun buildCancel(buildId: String, buildStatus: BuildStatus) {
        logger.info("Cancel the build $buildId")
        update(buildId = buildId, modelInterface = object : ModelInterface {

            var update = false

            override fun onFindStage(stage: Stage, model: Model): Traverse {
                if (stage.status == BuildStatus.RUNNING.name) {
                    stage.status = buildStatus.name
                    if (stage.startEpoch == null) {
                        stage.elapsed = 0
                    } else {
                        stage.elapsed = System.currentTimeMillis() - stage.startEpoch!!
                    }
                    update = true
                }
                return Traverse.CONTINUE
            }

            override fun onFindContainer(id: Int, container: Container, stage: Stage): Traverse {
                val status = BuildStatus.parse(container.status)
                if (status == BuildStatus.PREPARE_ENV) {
                    if (container.startEpoch == null) {
                        container.systemElapsed = 0
                    } else {
                        container.systemElapsed = System.currentTimeMillis() - container.startEpoch!!
                    }

                    var containerElapsed = 0L
                    run lit@{
                        stage.containers.forEach {
                            containerElapsed += it.elementElapsed ?: 0
                            if (it == container) {
                                return@lit
                            }
                        }
                    }

                    stage.elapsed = containerElapsed

                    update = true
                }
                // #3138 状态实时刷新
                if (status.isRunning()) {
                    container.status = buildStatus.name
                }
                return Traverse.CONTINUE
            }

            override fun onFindElement(e: Element, c: Container): Traverse {
                if (e.status == BuildStatus.RUNNING.name || e.status == BuildStatus.REVIEWING.name) {
                    val status = if (e.status == BuildStatus.RUNNING.name) {
                        BuildStatus.TERMINATE.name
                    } else buildStatus.name
                    e.status = status
                    c.status = status

                    if (e.startEpoch != null) {
                        e.elapsed = System.currentTimeMillis() - e.startEpoch!!
                    }

                    var elementElapsed = 0L
                    run lit@{
                        c.elements.forEach {
                            elementElapsed += it.elapsed ?: 0
                            if (it == e) {
                                return@lit
                            }
                        }
                    }

                    c.elementElapsed = elementElapsed

                    update = true
                }
                return Traverse.CONTINUE
            }

            override fun needUpdate(): Boolean {
                return update
            }
        }, buildStatus = BuildStatus.RUNNING, operation = "buildCancel")
    }

    fun buildEnd(buildId: String, buildStatus: BuildStatus, cancelUser: String? = null): List<BuildStageStatus> {
        logger.info("[$buildId]|BUILD_END|buildStatus=$buildStatus|cancelUser=$cancelUser")
        var allStageStatus: List<BuildStageStatus> = emptyList()
        update(buildId = buildId, modelInterface = object : ModelInterface {
            var update = false

            override fun onFindContainer(id: Int, container: Container, stage: Stage): Traverse {
                if (!container.status.isNullOrBlank() && BuildStatus.valueOf(container.status!!).isRunning()) {
                    container.status = buildStatus.name
                    update = true
                    if (container.startEpoch == null) {
                        container.elementElapsed = 0
                    } else {
                        container.elementElapsed = System.currentTimeMillis() - container.startEpoch!!
                    }
                }
                return Traverse.CONTINUE
            }

            override fun onFindStage(stage: Stage, model: Model): Traverse {
                if (allStageStatus.isEmpty()) {
                    allStageStatus = fetchHistoryStageStatus(model)
                }
                if (stage.id.isNullOrBlank()) {
                    return Traverse.BREAK
                }
                if (!stage.status.isNullOrBlank() && BuildStatus.valueOf(stage.status!!).isRunning()) {
                    stage.status = buildStatus.name
                    update = true
                    if (stage.startEpoch == null) {
                        stage.elapsed = 0
                    } else {
                        stage.elapsed = System.currentTimeMillis() - stage.startEpoch!!
                    }
                }
                return Traverse.CONTINUE
            }

            override fun onFindElement(e: Element, c: Container): Traverse {
                if (!e.status.isNullOrBlank() && BuildStatus.valueOf(e.status!!).isRunning()) {
                    e.status = buildStatus.name
                    update = true
                    if (e.startEpoch != null) {
                        e.elapsed = System.currentTimeMillis() - e.startEpoch!!
                    }

                    var elementElapsed = 0L
                    run lit@{
                        c.elements.forEach {
                            elementElapsed += it.elapsed ?: 0
                            if (it == e) {
                                return@lit
                            }
                        }
                    }
                    c.elementElapsed = elementElapsed
                }

                return Traverse.CONTINUE
            }

            override fun needUpdate(): Boolean {
                return update
            }
        }, buildStatus = buildStatus, operation = "buildEnd")
        return allStageStatus
    }

    fun updateBuildCancelUser(buildId: String, cancelUserId: String) {
        buildDetailDao.updateBuildCancelUser(dslContext, buildId, cancelUserId)
    }

    private fun fetchHistoryStageStatus(model: Model): List<BuildStageStatus> {
        // 更新Stage状态至BuildHistory
        return model.stages.map {
            BuildStageStatus(
                stageId = it.id!!,
                name = it.name ?: it.id!!,
                status = it.status,
                startEpoch = it.startEpoch,
                elapsed = it.elapsed
            )
        }
    }

    fun saveBuildVmInfo(projectId: String, pipelineId: String, buildId: String, containerId: Int, vmInfo: VmInfo) {
        update(
            buildId = buildId,
            modelInterface = object : ModelInterface {
                var update = false

                override fun onFindContainer(id: Int, container: Container, stage: Stage): Traverse {
                    if (id == containerId) {
                        if (container is VMBuildContainer && container.showBuildResource == true) {
                            container.name = vmInfo.name
                        }
                        update = true
                        return Traverse.BREAK
                    }
                    return Traverse.CONTINUE
                }

                override fun needUpdate(): Boolean {
                    return update
                }
            },
            buildStatus = BuildStatus.RUNNING,
            operation = "saveBuildVmInfo($projectId,$pipelineId)"
        )
    }
}
