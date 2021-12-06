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

import com.tencent.devops.common.api.exception.DependNotFoundException
import com.tencent.devops.common.api.exception.ExecuteException
import com.tencent.devops.common.event.enums.ActionType
import com.tencent.devops.common.pipeline.container.NormalContainer
import com.tencent.devops.common.pipeline.container.VMBuildContainer
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.common.pipeline.enums.VMBaseOS
import com.tencent.devops.common.pipeline.option.MatrixControlOption
import com.tencent.devops.common.pipeline.option.MatrixControlOption.Companion.MATRIX_CASE_MAX_COUNT
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.matrix.MatrixStatusElement
import com.tencent.devops.common.pipeline.type.agent.ThirdPartyAgentEnvDispatchType
import com.tencent.devops.process.engine.atom.parser.DispatchTypeParser
import com.tencent.devops.process.engine.cfg.ModelContainerIdGenerator
import com.tencent.devops.process.engine.cfg.ModelTaskIdGenerator
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.engine.context.MatrixBuildContext
import com.tencent.devops.process.engine.control.command.CmdFlowState
import com.tencent.devops.process.engine.control.command.container.ContainerCmd
import com.tencent.devops.process.engine.control.command.container.ContainerContext
import com.tencent.devops.process.engine.pojo.PipelineBuildContainer
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.engine.service.PipelineContainerService
import com.tencent.devops.process.engine.service.PipelineTaskService
import com.tencent.devops.process.engine.service.detail.ContainerBuildDetailService
import com.tencent.devops.process.utils.PIPELINE_RETRY_ALL_FAILED_CONTAINER
import com.tencent.devops.process.utils.PIPELINE_SKIP_FAILED_TASK
import com.tencent.devops.process.utils.PIPELINE_START_CHANNEL
import com.tencent.devops.process.utils.PIPELINE_START_PARENT_BUILD_ID
import com.tencent.devops.process.utils.PIPELINE_START_PARENT_BUILD_TASK_ID
import com.tencent.devops.process.utils.PIPELINE_START_TYPE
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Stage计算构建矩阵事件命令处理
 */
@Suppress(
    "ComplexMethod",
    "LongMethod",
    "ReturnCount",
    "NestedBlockDepth",
    "ThrowsCount"
)
@Service
class InitializeMatrixGroupStageCmd(
    private val dslContext: DSLContext,
    private val containerBuildDetailService: ContainerBuildDetailService,
    private val pipelineContainerService: PipelineContainerService,
    private val pipelineTaskService: PipelineTaskService,
    private val modelContainerIdGenerator: ModelContainerIdGenerator,
    private val modelTaskIdGenerator: ModelTaskIdGenerator,
    private val dispatchTypeParser: DispatchTypeParser
) : ContainerCmd {

    companion object {
        private val LOG = LoggerFactory.getLogger(InitializeMatrixGroupStageCmd::class.java)
    }

    override fun canExecute(commandContext: ContainerContext): Boolean {
        // 仅在初次准备并发执行Stage下Container是执行
        return commandContext.cmdFlowState == CmdFlowState.CONTINUE &&
            commandContext.container.status.isReadyToRun() &&
            commandContext.container.matrixGroupFlag == true
    }

    override fun execute(commandContext: ContainerContext) {

        // 在下发构建机任务前进行构建矩阵计算
        val parentContainer = commandContext.container
        val count = try {
            generateMatrixGroup(commandContext, parentContainer)
        } catch (ignore: Throwable) {
            LOG.error("ENGINE|${parentContainer.buildId}|MATRIX_CONTAINER_INIT_FAILED|" +
                "matrix(${parentContainer.containerId})|" +
                "parentContainer=$parentContainer", ignore)
            0
        }

        LOG.info("ENGINE|${parentContainer.buildId}|MATRIX_CONTAINER_INIT|" +
            "matrix(${parentContainer.containerId})|newContainerCount=$count")

        if (count > 0) {
            commandContext.cmdFlowState = CmdFlowState.CONTINUE
            commandContext.latestSummary = "j(${parentContainer.containerId}) matrix failed"
        } else {
            commandContext.buildStatus = BuildStatus.FAILED
            commandContext.cmdFlowState = CmdFlowState.FINALLY
            commandContext.latestSummary = "Matrix(${parentContainer.containerId}) generateNew($count)"
        }
    }

    private fun generateMatrixGroup(
        commandContext: ContainerContext,
        parentContainer: PipelineBuildContainer
    ): Int {

        val event = commandContext.event
        val variables = commandContext.variables
        val modelStage = containerBuildDetailService.getBuildModel(
            buildId = parentContainer.buildId
        )?.getStage(parentContainer.stageId) ?: throw DependNotFoundException(
            "stage(${parentContainer.stageId}) cannot be found in model"
        )
        val modelContainer = modelStage.getContainer(
            vmSeqId = parentContainer.seq.toString()
        ) ?: throw DependNotFoundException(
            "container(${parentContainer.containerId}) cannot be found in model"
        )

        // #4518 待生成的分裂后container表和task表记录
        val buildContainerList = mutableListOf<PipelineBuildContainer>()
        val buildTaskList = mutableListOf<PipelineBuildTask>()

        // #4518 根据当前上下文对每一个构建矩阵进行裂变
        val groupContainers = mutableListOf<PipelineBuildContainer>()
        val matrixGroupId = parentContainer.containerId
        val context = MatrixBuildContext(
            actionType = ActionType.START,
            executeCount = commandContext.executeCount,
            firstTaskId = "",
            stageRetry = false,
            retryStartTaskId = null,
            userId = event.userId,
            triggerUser = event.userId,
            startType = StartType.valueOf(variables[PIPELINE_START_TYPE] as String),
            parentBuildId = variables[PIPELINE_START_PARENT_BUILD_ID],
            parentTaskId = variables[PIPELINE_START_PARENT_BUILD_TASK_ID],
            channelCode = if (variables[PIPELINE_START_CHANNEL] != null) {
                ChannelCode.valueOf(variables[PIPELINE_START_CHANNEL].toString())
            } else {
                ChannelCode.BS
            },
            retryFailedContainer = variables[PIPELINE_RETRY_ALL_FAILED_CONTAINER]?.toBoolean() ?: false,
            skipFailedTask = variables[PIPELINE_SKIP_FAILED_TASK]?.toBoolean() ?: false,
            // #4518 裂变的容器的seq id需要以父容器的seq id作为前缀
            containerSeq = VMUtils.genMatrixContainerSeq(matrixGroupId.toInt(), 0)
        )

        LOG.info("ENGINE|${event.buildId}|${event.source}|INIT_MATRIX_CONTAINER|${event.stageId}|" +
            "matrixGroupId=$matrixGroupId|containerHashId=${modelContainer.containerHashId}" +
            "|context=$context")

        val matrixOption: MatrixControlOption
        if (modelContainer is VMBuildContainer && modelContainer.matrixControlOption != null) {

            // 每一种上下文组合都是一个新容器
            matrixOption = modelContainer.matrixControlOption!!
            val jobControlOption = modelContainer.jobControlOption!!
            val contextCaseList = matrixOption.getAllContextCase(commandContext.variables)
            if (contextCaseList.size > MATRIX_CASE_MAX_COUNT) {
                throw ExecuteException("Matrix case(${contextCaseList.size}) exceeds " +
                    "the limit($MATRIX_CASE_MAX_COUNT)")
            }

            contextCaseList.forEachIndexed { index, contextCase ->

                // 包括matrix.xxx的所有上下文，矩阵生成的要覆盖原变量
                val allContext = (modelContainer.customBuildEnv ?: mapOf()).plus(contextCase)

                // 对自定义构建环境的做特殊解析
                // customDispatchType决定customBaseOS是否计算，请勿填充默认值
                val customDispatchType = matrixOption.runsOnStr?.let { self ->
                    dispatchTypeParser.parseRunsOn(self, allContext)
                }
                val customBaseOS = customDispatchType.let { self ->
                    if (self is ThirdPartyAgentEnvDispatchType) VMBaseOS.ALL else null
                }

                val newContainerSeq = context.containerSeq++

                // 刷新所有插件的ID，并生成对应的纯状态插件
                val statusElements = generateSampleStatusElements(modelContainer.elements)
                val newContainer = VMBuildContainer(
                    name = VMUtils.genContainerName(matrixGroupId.toInt(), index),
                    id = newContainerSeq.toString(),
                    containerId = newContainerSeq.toString(),
                    containerHashId = modelContainerIdGenerator.getNextId(),
                    matrixGroupId = matrixGroupId,
                    matrixContext = contextCase,
                    elements = modelContainer.elements,
                    canRetry = modelContainer.canRetry,
                    enableExternal = modelContainer.enableExternal,
                    jobControlOption = jobControlOption,
                    executeCount = modelContainer.executeCount,
                    containPostTaskFlag = modelContainer.containPostTaskFlag,
                    customBuildEnv = allContext,
                    baseOS = customBaseOS ?: modelContainer.baseOS,
                    vmNames = modelContainer.vmNames,
                    dockerBuildVersion = modelContainer.dockerBuildVersion,
                    dispatchType = customDispatchType ?: modelContainer.dispatchType,
                    buildEnv = modelContainer.buildEnv,
                    thirdPartyAgentId = modelContainer.thirdPartyAgentId,
                    thirdPartyAgentEnvId = modelContainer.thirdPartyAgentEnvId,
                    thirdPartyWorkspace = modelContainer.thirdPartyWorkspace
                )

                groupContainers.add(pipelineContainerService.prepareMatrixBuildContainer(
                    projectId = event.projectId,
                    pipelineId = event.pipelineId,
                    buildId = event.buildId,
                    container = newContainer,
                    stage = modelStage,
                    context = context,
                    buildTaskList = buildTaskList,
                    jobControlOption = jobControlOption,
                    matrixGroupId = matrixGroupId
                ))

                // 如为空就初始化，如有元素就直接追加
                if (modelContainer.groupContainers.isNullOrEmpty()) {
                    modelContainer.groupContainers = mutableListOf(newContainer.copy(
                        elements = statusElements
                    ))
                } else {
                    modelContainer.groupContainers!!.add(newContainer.copy(
                        elements = statusElements
                    ))
                }
            }
        } else if (modelContainer is NormalContainer && modelContainer.matrixControlOption != null) {

            // 每一种上下文组合都是一个新容器
            val newContainerSeq = context.containerSeq++
            matrixOption = modelContainer.matrixControlOption!!

            val contextCaseList = matrixOption.getAllContextCase(commandContext.variables)
            val jobControlOption = modelContainer.jobControlOption!!

            contextCaseList.forEachIndexed { index, contextCase ->

                // 刷新所有插件的ID，并生成对应的纯状态插件
                val statusElements = generateSampleStatusElements(modelContainer.elements)
                val newContainer = NormalContainer(
                    name = VMUtils.genContainerName(matrixGroupId.toInt(), index),
                    id = newContainerSeq.toString(),
                    containerId = newContainerSeq.toString(),
                    containerHashId = modelContainerIdGenerator.getNextId(),
                    matrixGroupId = matrixGroupId,
                    matrixContext = contextCase,
                    elements = modelContainer.elements,
                    canRetry = modelContainer.canRetry,
                    jobControlOption = jobControlOption.copy(),
                    executeCount = modelContainer.executeCount,
                    containPostTaskFlag = modelContainer.containPostTaskFlag
                )

                groupContainers.add(pipelineContainerService.prepareMatrixBuildContainer(
                    projectId = event.projectId,
                    pipelineId = event.pipelineId,
                    buildId = event.buildId,
                    container = newContainer,
                    stage = modelStage,
                    context = context,
                    buildTaskList = buildTaskList,
                    jobControlOption = jobControlOption,
                    matrixGroupId = matrixGroupId
                ))

                // 如为空就初始化，如有元素就直接追加
                if (modelContainer.groupContainers.isNullOrEmpty()) {
                    modelContainer.groupContainers = mutableListOf(newContainer.copy(
                        elements = statusElements
                    ))
                } else {
                    modelContainer.groupContainers!!.add(newContainer.copy(
                        elements = statusElements
                    ))
                }
            }
        } else {
            throw DependNotFoundException("matrix(${parentContainer.containerId}) option not found")
        }

        // 新增容器全部添加到Container表中
        buildContainerList.addAll(groupContainers)
        matrixOption.totalCount = groupContainers.size

        LOG.info("ENGINE|${event.buildId}|${event.source}|INIT_MATRIX_CONTAINER" +
            "|${event.stageId}|${modelContainer.id}|containerHashId=" +
            "${modelContainer.containerHashId}|context=$context|" +
            "groupContainers=$groupContainers|buildTaskList=$buildTaskList")

        // 在表中增加所有分裂的矩阵和插件
        dslContext.transaction { configuration ->
            val transactionContext = DSL.using(configuration)
            pipelineContainerService.batchSave(transactionContext, buildContainerList)
            pipelineTaskService.batchSave(transactionContext, buildTaskList)
        }

        // 在详情中刷新所有分裂后的矩阵
        pipelineContainerService.updateMatrixGroupStatus(
            projectId = parentContainer.projectId,
            buildId = parentContainer.buildId,
            stageId = parentContainer.stageId,
            matrixGroupId = matrixGroupId,
            controlOption = parentContainer.controlOption!!.copy(matrixControlOption = matrixOption),
            modelContainer = modelContainer
        )

        return buildContainerList.size
    }

    private fun generateSampleStatusElements(elements: List<Element>): List<MatrixStatusElement> {
        return elements.map {
            it.id = modelTaskIdGenerator.getNextId()
            MatrixStatusElement(
                name = it.name,
                id = it.id,
                executeCount = it.executeCount
            )
        }
    }
}