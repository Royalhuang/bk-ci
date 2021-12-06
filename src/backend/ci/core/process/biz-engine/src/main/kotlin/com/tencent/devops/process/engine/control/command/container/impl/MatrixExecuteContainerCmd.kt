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

package com.tencent.devops.process.engine.control.command.container.impl

import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.event.enums.ActionType
import com.tencent.devops.common.log.utils.BuildLogPrinter
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.engine.control.command.CmdFlowState
import com.tencent.devops.process.engine.control.command.container.ContainerCmd
import com.tencent.devops.process.engine.control.command.container.ContainerContext
import com.tencent.devops.process.engine.pojo.PipelineBuildContainer
import com.tencent.devops.process.engine.pojo.event.PipelineBuildContainerEvent
import com.tencent.devops.process.engine.service.PipelineContainerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Suppress("TooManyFunctions", "LongParameterList")
@Service
class MatrixExecuteContainerCmd(
    private val pipelineContainerService: PipelineContainerService,
    private val pipelineEventDispatcher: PipelineEventDispatcher,
    private val buildLogPrinter: BuildLogPrinter
) : ContainerCmd {

    companion object {
        private val LOG = LoggerFactory.getLogger(MatrixExecuteContainerCmd::class.java)
    }

    override fun canExecute(commandContext: ContainerContext): Boolean {
        return commandContext.cmdFlowState == CmdFlowState.CONTINUE &&
            !commandContext.buildStatus.isFinish() &&
            commandContext.container.matrixGroupFlag == true
    }

    override fun execute(commandContext: ContainerContext) {

        val parentContainer = commandContext.container

        // 获取组内所有待执行容器
        val groupContainers = pipelineContainerService.listGroupContainers(
            projectId = parentContainer.projectId,
            buildId = parentContainer.buildId,
            matrixGroupId = parentContainer.containerId
        )
        val groupStatus = try {
            judgeGroupContainers(commandContext, parentContainer, groupContainers)
        } catch (ignore: Throwable) {
            LOG.error("ENGINE|${parentContainer.buildId}|MATRIX_LOOP_MONITOR_FAILED|" +
                "matrix(${parentContainer.containerId})|groupContainers=$groupContainers" +
                "parentContainer=$parentContainer", ignore)
            BuildStatus.FAILED
        }

        if (groupStatus.isFinish()) {
            commandContext.buildStatus = groupStatus
            commandContext.cmdFlowState = CmdFlowState.FINALLY
            commandContext.latestSummary = "Matrix(${commandContext.container.containerId})_loop_finish"
        } else {
            commandContext.cmdFlowState = CmdFlowState.LOOP
            commandContext.latestSummary = "Matrix(${commandContext.container.containerId})_loop_continue"
        }
    }

    private fun judgeGroupContainers(
        commandContext: ContainerContext,
        parentContainer: PipelineBuildContainer,
        groupContainers: List<PipelineBuildContainer>
    ): BuildStatus {
        val event = commandContext.event
        val matrixOption = parentContainer.controlOption?.matrixControlOption!!

        var failedCount = 0
        var finishCount = 0

        groupContainers.forEach { container ->
            when {
                container.status.isReadyToRun() -> {
                    sendBuildContainerEvent(
                        container = container,
                        actionType = ActionType.START,
                        userId = event.userId,
                        reason = commandContext.latestSummary
                    )
                }
                container.status.isFinish() -> {
                    if (container.status.isFailure()) failedCount++
                    finishCount++
                }
            }
        }

        // 判断是否要进行fastKill
        if (failedCount > 0 && matrixOption.fastKill == true) {

            LOG.info("ENGINE|${event.buildId}|MATRIX_GROUP_FAST_KILL|${event.stageId}|" +
                "j(${event.containerId})|count=${groupContainers.size}|groupContainers=$groupContainers")

            buildLogPrinter.addYellowLine(
                buildId = event.buildId,
                message = "Matrix(${parentContainer.containerId}) failed containers " +
                    "count($failedCount), start to kill conatainers",
                tag = VMUtils.genStartVMTaskId(parentContainer.containerId),
                jobId = parentContainer.containerHashId,
                executeCount = commandContext.executeCount
            )

            groupContainers.forEach { container ->
                if (!container.status.isFinish()) {
                    sendBuildContainerEvent(
                        container = container,
                        actionType = ActionType.TERMINATE,
                        userId = event.userId,
                        reason = "from_matrix(${parentContainer.containerId})_fastKill"
                    )
                    buildLogPrinter.addYellowLine(
                        buildId = event.buildId,
                        message = "Matrix(${parentContainer.containerId}) try to stop " +
                            "container(${container.containerId})",
                        tag = VMUtils.genStartVMTaskId(parentContainer.containerId),
                        jobId = parentContainer.containerHashId,
                        executeCount = commandContext.executeCount
                    )
                }
            }
        }

        // 实时刷新数据库状态
        matrixOption.finishCount = finishCount
        pipelineContainerService.updateMatrixGroupStatus(
            projectId = parentContainer.projectId,
            buildId = parentContainer.buildId,
            stageId = parentContainer.stageId,
            matrixGroupId = parentContainer.containerId,
            controlOption = parentContainer.controlOption!!.copy(matrixControlOption = matrixOption),
            modelContainer = null
        )

        return if (finishCount == matrixOption.totalCount) {
            if (failedCount > 0) {
                BuildStatus.FAILED
            } else {
                BuildStatus.SUCCEED
            }
        } else {
            BuildStatus.RUNNING
        }
    }

    private fun sendBuildContainerEvent(
        container: PipelineBuildContainer,
        actionType: ActionType,
        userId: String,
        reason: String
    ) {
        // 通知容器构建消息
        pipelineEventDispatcher.dispatch(
            PipelineBuildContainerEvent(
                source = "From_s(${container.stageId})",
                projectId = container.projectId,
                pipelineId = container.pipelineId,
                userId = userId,
                buildId = container.buildId,
                stageId = container.stageId,
                containerType = container.containerType,
                containerId = container.containerId,
                containerHashId = container.containerHashId,
                actionType = actionType,
                errorCode = 0,
                errorTypeName = null,
                reason = reason
            )
        )
    }
}