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

package com.tencent.devops.process.engine.service.record

import com.tencent.devops.common.api.constant.INIT_VERSION
import com.tencent.devops.common.api.util.Watcher
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.agent.ManualReviewUserTaskElement
import com.tencent.devops.common.pipeline.pojo.element.market.MarketBuildAtomElement
import com.tencent.devops.common.pipeline.pojo.element.market.MarketBuildLessAtomElement
import com.tencent.devops.common.pipeline.pojo.element.matrix.MatrixStatusElement
import com.tencent.devops.common.pipeline.pojo.element.quality.QualityGateInElement
import com.tencent.devops.common.pipeline.pojo.element.quality.QualityGateOutElement
import com.tencent.devops.process.dao.record.BuildRecordTaskDao
import com.tencent.devops.process.engine.dao.PipelineBuildTaskDao
import com.tencent.devops.process.engine.pojo.PipelineTaskStatusInfo
import com.tencent.devops.process.pojo.pipeline.record.BuildRecordTask
import com.tencent.devops.process.pojo.pipeline.record.time.BuildRecordTimeCost
import com.tencent.devops.process.pojo.pipeline.record.time.BuildRecordTimeStamp
import com.tencent.devops.process.pojo.task.TaskBuildEndParam
import com.tencent.devops.process.service.BuildVariableService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Suppress("LongParameterList", "MagicNumber", "ReturnCount", "TooManyFunctions", "ComplexCondition")
@Service
class TaskBuildRecordService(
    private val buildVariableService: BuildVariableService,
    private val dslContext: DSLContext,
    private val buildRecordTaskDao: BuildRecordTaskDao,
    private val buildTaskDao: PipelineBuildTaskDao,
    private val containerBuildRecordService: ContainerBuildRecordService
) {

//    fun batchUpdate(transactionContext: DSLContext?, taskList: List<BuildRecordTask>) {
//        return buildRecordTaskDao.batchUpdate(transactionContext ?: dslContext, taskList)
//    }

    fun updateTaskStatus(
        projectId: String,
        pipelineId: String,
        buildId: String,
        stageId: String,
        containerId: String,
        taskId: String,
        executeCount: Int,
        buildStatus: BuildStatus,
        operation: String
    ) {
        updateTaskByMap(
            projectId = projectId,
            pipelineId = pipelineId,
            buildId = buildId,
            taskId = taskId,
            executeCount = executeCount,
            taskVar = mapOf(
                Element::status.name to buildStatus.name
            ),
            operation = operation
        )
    }

    // TODO 暂时保留和detail一致的方法，后续简化为updateTaskStatus
    fun taskPause(
        projectId: String,
        pipelineId: String,
        buildId: String,
        stageId: String,
        containerId: String,
        taskId: String,
        executeCount: Int
    ) {
        updateTaskStatus(
            projectId = projectId,
            pipelineId = pipelineId,
            buildId = buildId,
            stageId = stageId,
            containerId = containerId,
            taskId = taskId,
            executeCount = executeCount,
            buildStatus = BuildStatus.PAUSE,
            operation = "taskPause#$taskId"
        )
    }

    fun taskStart(
        projectId: String,
        pipelineId: String,
        buildId: String,
        containerId: String,
        taskId: String,
        executeCount: Int
    ) {
        val delimiters = ","
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            val recordTask = buildRecordTaskDao.getRecord(
                dslContext = context,
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId,
                taskId = taskId,
                executeCount = executeCount
            ) ?: run {
                logger.warn(
                    "ENGINE|$buildId|updateTaskByMap| get task($taskId) record failed."
                )
                return@transaction
            }
            val taskVar = mutableMapOf<String, Any>()
            taskVar.putAll(recordTask.taskVar)
            if (
                recordTask.classType == ManualReviewUserTaskElement.classType ||
                (recordTask.classType == MatrixStatusElement.classType &&
                    recordTask.originClassType == ManualReviewUserTaskElement.classType)
            ) {
                taskVar[Element::status.name] = BuildStatus.REVIEWING.name
                val list = mutableListOf<String>()
                taskVar[ManualReviewUserTaskElement::reviewUsers.name]?.let {
                    try {
                        (it as List<*>).forEach { reviewUser ->
                            list.addAll(
                                buildVariableService.replaceTemplate(projectId, buildId, reviewUser.toString())
                                    .split(delimiters)
                            )
                        }
                    } catch (ignore: Throwable) {
                        return@let
                    }
                }
                taskVar[ManualReviewUserTaskElement::reviewUsers.name] = list
            } else if (
                recordTask.classType == QualityGateInElement.classType ||
                recordTask.classType == QualityGateOutElement.classType ||
                recordTask.originClassType == QualityGateInElement.classType ||
                recordTask.originClassType == QualityGateOutElement.classType
            ) {
                taskVar[Element::status.name] = BuildStatus.REVIEWING.name
                containerBuildRecordService.updateContainerStatus(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    buildId = buildId,
                    containerId = containerId,
                    executeCount = executeCount,
                    buildStatus = BuildStatus.REVIEWING,
                    operation = "taskStart#$taskId"
                )
            } else {
                taskVar[Element::status.name] = BuildStatus.RUNNING.name
                containerBuildRecordService.updateContainerStatus(
                    projectId = projectId,
                    pipelineId = pipelineId,
                    buildId = buildId,
                    containerId = containerId,
                    executeCount = executeCount,
                    buildStatus = BuildStatus.RUNNING,
                    operation = "taskStart#$taskId"
                )
            }

            buildRecordTaskDao.updateRecord(
                dslContext = context,
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId,
                taskId = taskId,
                executeCount = executeCount,
                taskVar = taskVar,
                startTime = LocalDateTime.now(),
                endTime = null,
                timestamps = null,
                timeCost = null
            )
        }
    }

    // TODO 暂时保留和detail一致的方法，后续简化为updateTaskStatus
    fun taskCancel(
        projectId: String,
        pipelineId: String,
        buildId: String,
        stageId: String,
        containerId: String,
        taskId: String,
        executeCount: Int
    ) {
        updateTaskStatus(
            projectId = projectId,
            pipelineId = pipelineId,
            buildId = buildId,
            stageId = stageId,
            containerId = containerId,
            taskId = taskId,
            executeCount = executeCount,
            buildStatus = BuildStatus.CANCELED,
            operation = "taskCancel#$taskId"
        )
    }

    fun taskEnd(taskBuildEndParam: TaskBuildEndParam): List<PipelineTaskStatusInfo> {
        val projectId = taskBuildEndParam.projectId
        val pipelineId = taskBuildEndParam.pipelineId
        val buildId = taskBuildEndParam.buildId
        val taskId = taskBuildEndParam.taskId
        val buildStatus = taskBuildEndParam.buildStatus
        val atomVersion = taskBuildEndParam.atomVersion
        val errorType = taskBuildEndParam.errorType
        val updateTaskStatusInfos = mutableListOf<PipelineTaskStatusInfo>()

        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            val buildTask = buildTaskDao.get(
                dslContext = context,
                projectId = projectId,
                buildId = buildId,
                taskId = taskId
            )
            val executeCount = buildTask?.executeCount ?: 1
            val recordTask = buildRecordTaskDao.getRecord(
                dslContext = context,
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId,
                taskId = taskId,
                executeCount = executeCount
            ) ?: run {
                logger.warn(
                    "ENGINE|$buildId|updateTaskByMap| get task($taskId) record failed."
                )
                return@transaction
            }
            val taskVar = mutableMapOf<String, Any>()
            taskVar.putAll(recordTask.taskVar)
            if (atomVersion != null) {
                when (recordTask.classType) {
                    MarketBuildAtomElement.classType -> {
                        taskVar[MarketBuildAtomElement::version.name] = atomVersion
                    }
                    MarketBuildLessAtomElement.classType -> {
                        taskVar[MarketBuildLessAtomElement::version.name] = atomVersion
                    }
                    else -> {
                        taskVar[MarketBuildAtomElement::version.name] = INIT_VERSION
                    }
                }
            }
            taskVar[Element::status.name] = buildStatus.name
            // TODO 计算总耗时
            buildRecordTaskDao.updateRecord(
                dslContext = context,
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId,
                taskId = taskId,
                executeCount = executeCount,
                taskVar = taskVar,
                startTime = null,
                endTime = LocalDateTime.now(),
                timestamps = null,
                timeCost = null
            )
        }

        return updateTaskStatusInfos
    }

    @Suppress("NestedBlockDepth")
    fun taskContinue(
        projectId: String,
        pipelineId: String,
        buildId: String,
        stageId: String,
        containerId: String,
        taskId: String,
        executeCount: Int,
        element: Element?
    ) {
        containerBuildRecordService.updateContainerStatus(
            projectId = projectId,
            pipelineId = pipelineId,
            buildId = buildId,
            containerId = containerId,
            executeCount = executeCount,
            buildStatus = BuildStatus.QUEUE,
            operation = "updateElementWhenPauseContinue#$taskId"
        )
        updateTaskStatus(
            projectId = projectId,
            pipelineId = pipelineId,
            buildId = buildId,
            stageId = stageId,
            containerId = containerId,
            taskId = taskId,
            executeCount = executeCount,
            buildStatus = BuildStatus.QUEUE,
            operation = "updateElementWhenPauseContinue#$taskId"
        )
    }

    fun updateTaskByMap(
        projectId: String,
        pipelineId: String,
        buildId: String,
        taskId: String,
        executeCount: Int,
        taskVar: Map<String, Any>,
        operation: String,
        startTime: LocalDateTime? = null,
        endTime: LocalDateTime? = null,
        timestamps: List<BuildRecordTimeStamp>? = null,
        timeCost: BuildRecordTimeCost? = null
    ) {
        val watcher = Watcher(id = "updateDetail#$buildId#$operation")
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            watcher.start("getRecord")
            val recordVar = buildRecordTaskDao.getRecordTaskVar(
                dslContext = transactionContext,
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId,
                taskId = taskId,
                executeCount = executeCount
            )?.toMutableMap() ?: run {
                logger.warn(
                    "ENGINE|$buildId|updateTaskByMap| get task($taskId) record failed."
                )
                return@transaction
            }
            watcher.start("updateRecord")
            buildRecordTaskDao.updateRecord(
                dslContext = transactionContext,
                projectId = projectId,
                pipelineId = pipelineId,
                buildId = buildId,
                taskId = taskId,
                executeCount = executeCount,
                taskVar = recordVar.plus(taskVar),
                startTime = startTime,
                endTime = endTime,
                timestamps = timestamps,
                timeCost = timeCost
            )
            watcher.start("updated")
        }
        watcher.stop()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TaskBuildRecordService::class.java)
    }
}
