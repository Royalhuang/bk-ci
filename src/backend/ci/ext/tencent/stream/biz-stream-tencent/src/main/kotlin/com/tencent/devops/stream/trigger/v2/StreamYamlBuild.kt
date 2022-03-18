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

package com.tencent.devops.stream.trigger.v2

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.devops.common.api.exception.CustomException
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.ci.v2.ScriptBuildYaml
import com.tencent.devops.common.ci.v2.YamlTransferData
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.container.TriggerContainer
import com.tencent.devops.common.pipeline.pojo.element.trigger.ManualTriggerElement
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.stream.common.exception.CommitCheck
import com.tencent.devops.stream.common.exception.QualityRulesException
import com.tencent.devops.stream.common.exception.TriggerBaseException
import com.tencent.devops.stream.common.exception.TriggerException
import com.tencent.devops.stream.common.exception.Yamls
import com.tencent.devops.stream.pojo.GitCITriggerLock
import com.tencent.devops.stream.pojo.GitProjectPipeline
import com.tencent.devops.stream.pojo.GitRequestEvent
import com.tencent.devops.stream.pojo.enums.GitCICommitCheckState
import com.tencent.devops.stream.pojo.enums.TriggerReason
import com.tencent.devops.stream.pojo.v2.GitCIBasicSetting
import com.tencent.devops.stream.utils.GitCIPipelineUtils
import com.tencent.devops.stream.v2.dao.StreamBasicSettingDao
import com.tencent.devops.process.pojo.BuildId
import com.tencent.devops.common.webhook.enums.code.tgit.TGitObjectKind
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.webhook.pojo.code.git.GitEvent
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.scm.pojo.GitCIProjectInfo
import com.tencent.devops.stream.config.StreamStorageBean
import com.tencent.devops.stream.pojo.v2.StreamDeleteEvent
import com.tencent.devops.stream.service.GitCIPipelineService
import com.devops.process.yaml.modelCreate.ModelCreate
import com.devops.process.yaml.modelCreate.inner.GitData
import com.devops.process.yaml.modelCreate.inner.ModelCreateEvent
import com.devops.process.yaml.modelCreate.inner.PipelineInfo
import com.devops.process.yaml.modelCreate.inner.StreamData
import com.tencent.devops.common.client.Client
import com.tencent.devops.stream.trigger.parsers.modelCreate.InnerModelCreatorImpl
import com.tencent.devops.stream.trigger.parsers.modelCreate.ModelParameters
import com.tencent.devops.stream.trigger.timer.pojo.StreamTimer
import com.tencent.devops.stream.trigger.timer.service.StreamTimerService
import com.tencent.devops.stream.v2.service.DeleteEventService
import java.time.LocalDateTime
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class StreamYamlBuild @Autowired constructor(
    private val client: Client,
    private val objectMapper: ObjectMapper,
    private val streamYamlBaseBuild: StreamYamlBaseBuild,
    private val dslContext: DSLContext,
    private val streamBasicSettingDao: StreamBasicSettingDao,
    private val pipelineService: GitCIPipelineService,
    private val redisOperation: RedisOperation,
    private val modelCreateInnerImpl: InnerModelCreatorImpl,
    private val streamStorageBean: StreamStorageBean,
    private val streamTimerService: StreamTimerService,
    private val deleteEventService: DeleteEventService
) {

    @Value("\${rtx.v2GitUrl:#{null}}")
    private val v2GitUrl: String? = null

    private val modelCreate = ModelCreate(
        client = client,
        objectMapper = objectMapper,
        inner = modelCreateInnerImpl
    )

    companion object {
        private val logger = LoggerFactory.getLogger(StreamYamlBuild::class.java)
        private const val ymlVersion = "v2.0"
        const val VARIABLE_PREFIX = "variables."
        private val channelCode = ChannelCode.GIT
    }

    @Throws(TriggerBaseException::class, ErrorCodeException::class)
    @SuppressWarnings("LongParameterList")
    fun gitStartBuild(
        pipeline: GitProjectPipeline,
        event: GitRequestEvent,
        yaml: ScriptBuildYaml,
        originYaml: String,
        parsedYaml: String,
        normalizedYaml: String,
        gitBuildId: Long?,
        onlySavePipeline: Boolean,
        isTimeTrigger: Boolean,
        isDeleteTrigger: Boolean = false,
        gitProjectInfo: GitCIProjectInfo? = null,
        changeSet: Set<String>? = null,
        params: Map<String, String> = mapOf(),
        yamlTransferData: YamlTransferData
    ): BuildId? {
        val start = LocalDateTime.now().timestampmilli()
        // pipelineId可能为blank所以使用filePath为key
        val triggerLock = GitCITriggerLock(
            redisOperation = redisOperation,
            gitProjectId = event.gitProjectId,
            filePath = pipeline.filePath
        )
        try {
            val realPipeline: GitProjectPipeline
            val gitBasicSetting = streamBasicSettingDao.getSetting(dslContext, event.gitProjectId)!!
            // 避免出现多个触发拿到空的pipelineId后依次进来创建，所以需要在锁后重新获取pipeline
            try {
                triggerLock.lock()
                realPipeline = pipelineService.getPipelineByFile(
                    event.gitProjectId,
                    pipeline.filePath
                ) ?: pipeline
                // 优先创建流水线为了绑定红线
                if (realPipeline.pipelineId.isBlank()) {
                    streamYamlBaseBuild.savePipeline(
                        pipeline = realPipeline,
                        event = event,
                        gitCIBasicSetting = gitBasicSetting,
                        model = createTriggerModel(gitBasicSetting),
                        updateLastModifyUser = true
                    )
                }
            } finally {
                triggerLock.unlock()
            }

            // 改名时保存需要修改名称
            realPipeline.displayName = pipeline.displayName

            saveSpecialTriggerEvent(
                isTimeTrigger = isTimeTrigger,
                event = event,
                realPipeline = realPipeline,
                yaml = yaml,
                gitProjectInfo = gitProjectInfo,
                originYaml = originYaml,
                isDeleteTrigger = isDeleteTrigger
            )

            return if (gitBuildId != null) {
                startBuildPipeline(
                    pipeline = realPipeline,
                    event = event,
                    yaml = yaml,
                    gitBuildId = gitBuildId,
                    changeSet = changeSet,
                    gitBasicSetting = gitBasicSetting,
                    params = params,
                    yamlTransferData = yamlTransferData
                )
            } else if (onlySavePipeline) {
                savePipeline(
                    pipeline = realPipeline,
                    event = event,
                    yaml = yaml,
                    changeSet = changeSet,
                    gitBasicSetting = gitBasicSetting
                )
                null
            } else {
                null
            }
        } catch (e: Throwable) {
            logger.warn("Fail to start the git ci build($event)", e)
            val (block, message, reason) = when (e) {
                is JsonProcessingException, is ParamBlankException, is CustomException -> {
                    Triple(
                        (event.objectKind == TGitObjectKind.MERGE_REQUEST.value),
                        e.message,
                        TriggerReason.PIPELINE_PREPARE_ERROR
                    )
                }
                is QualityRulesException -> {
                    Triple(
                        false,
                        e.message,
                        TriggerReason.CREATE_QUALITY_RULRS_ERROR
                    )
                }
                // 指定异常直接扔出在外面统一处理
                is TriggerBaseException, is ErrorCodeException -> {
                    throw e
                }
                else -> {
                    logger.error("event: ${event.id} unknow error: ${e.message}")
                    Triple(false, e.message, TriggerReason.UNKNOWN_ERROR)
                }
            }
            TriggerException.triggerError(
                request = event,
                pipeline = pipeline,
                reason = reason,
                reasonParams = listOf(message ?: ""),
                yamls = Yamls(originYaml, parsedYaml, normalizedYaml),
                version = ymlVersion,
                commitCheck = CommitCheck(
                    block = block,
                    state = GitCICommitCheckState.FAILURE
                )
            )
        } finally {
            streamStorageBean.buildTime(LocalDateTime.now().timestampmilli() - start)
        }
    }

    private fun saveSpecialTriggerEvent(
        isTimeTrigger: Boolean,
        event: GitRequestEvent,
        realPipeline: GitProjectPipeline,
        yaml: ScriptBuildYaml,
        gitProjectInfo: GitCIProjectInfo?,
        originYaml: String,
        isDeleteTrigger: Boolean
    ) {
        // 如果是定时触发需要注册事件
        if (isTimeTrigger) {
            streamTimerService.saveTimer(
                StreamTimer(
                    projectId = GitCIPipelineUtils.genGitProjectCode(event.gitProjectId),
                    pipelineId = realPipeline.pipelineId,
                    userId = event.userId,
                    crontabExpressions = listOf(yaml.triggerOn?.schedules?.cron.toString()),
                    gitProjectId = event.gitProjectId,
                    // 未填写则在每次触发拉默认分支
                    branchs = yaml.triggerOn?.schedules?.branches?.ifEmpty {
                        listOf(gitProjectInfo?.defaultBranch!!)
                    } ?: listOf(gitProjectInfo?.defaultBranch!!),
                    always = yaml.triggerOn?.schedules?.always ?: false,
                    channelCode = channelCode,
                    eventId = event.id!!,
                    originYaml = originYaml
                )
            )
        }

        if (isDeleteTrigger && deleteEventService.getDeleteEvent(realPipeline.pipelineId) == null) {
            deleteEventService.saveDeleteEvent(
                StreamDeleteEvent(
                    gitProjectId = event.gitProjectId,
                    pipelineId = realPipeline.pipelineId,
                    userId = event.userId,
                    eventId = event.id!!,
                    originYaml = originYaml
                )
            )
        }
    }

    private fun createTriggerModel(gitBasicSetting: GitCIBasicSetting) = Model(
        name = GitCIPipelineUtils.genBKPipelineName(gitBasicSetting.gitProjectId),
        desc = "",
        stages = listOf(
            Stage(
                id = VMUtils.genStageId(1),
                name = VMUtils.genStageId(1),
                containers = listOf(
                    TriggerContainer(
                        id = "0",
                        name = "构建触发",
                        elements = listOf(
                            ManualTriggerElement(
                                name = "手动触发",
                                id = "T-1-1-1"
                            )
                        )
                    )
                )
            )
        )
    )

    @SuppressWarnings("LongParameterList")
    private fun startBuildPipeline(
        pipeline: GitProjectPipeline,
        event: GitRequestEvent,
        yaml: ScriptBuildYaml,
        gitBuildId: Long,
        gitBasicSetting: GitCIBasicSetting,
        changeSet: Set<String>? = null,
        params: Map<String, String> = mapOf(),
        yamlTransferData: YamlTransferData
    ): BuildId? {
        logger.info("Git request gitBuildId:$gitBuildId, pipeline:${pipeline.pipelineId}, event: ${event.id}")

        val (modelCreateEvent, modelParams) = getModelCreateEventAndParams(
            pipeline = pipeline,
            event = event,
            yaml = yaml,
            gitBasicSetting = gitBasicSetting,
            changeSet = changeSet,
            webhookParams = params,
            yamlTransferData = yamlTransferData
        )

        // create or refresh pipeline
        val model = modelCreate.createPipelineModel(
            modelName = GitCIPipelineUtils.genBKPipelineName(gitBasicSetting.gitProjectId),
            event = modelCreateEvent,
            yaml = yaml,
            pipelineParams = modelParams
        )
        logger.info("startBuildPipeline gitBuildId:$gitBuildId, pipeline:$pipeline, model: $model")

        // 判断是否更新最后修改人
        val updateLastModifyUser = !changeSet.isNullOrEmpty() && changeSet.contains(pipeline.filePath)

        return streamYamlBaseBuild.startBuild(
            pipeline = pipeline,
            event = event,
            gitCIBasicSetting = gitBasicSetting,
            model = model,
            gitBuildId = gitBuildId,
            yamlTransferData = yamlTransferData,
            updateLastModifyUser = updateLastModifyUser
        )
    }

    private fun savePipeline(
        pipeline: GitProjectPipeline,
        event: GitRequestEvent,
        yaml: ScriptBuildYaml,
        changeSet: Set<String>? = null,
        gitBasicSetting: GitCIBasicSetting
    ) {
        val (modelCreateEvent, modelParams) = getModelCreateEventAndParams(
            pipeline = pipeline,
            event = event,
            yaml = yaml,
            gitBasicSetting = gitBasicSetting,
            changeSet = changeSet,
            webhookParams = mapOf(),
            yamlTransferData = null
        )

        val model = modelCreate.createPipelineModel(
            modelName = GitCIPipelineUtils.genBKPipelineName(gitBasicSetting.gitProjectId),
            event = modelCreateEvent,
            yaml = yaml,
            pipelineParams = modelParams
        )
        logger.info("savePipeline pipeline:$pipeline, model: $model")

        // 判断是否更新最后修改人
        val updateLastModifyUser = !changeSet.isNullOrEmpty() && changeSet.contains(pipeline.filePath)

        streamYamlBaseBuild.savePipeline(pipeline, event, gitBasicSetting, model, updateLastModifyUser)
    }

    private fun getModelCreateEventAndParams(
        pipeline: GitProjectPipeline,
        event: GitRequestEvent,
        yaml: ScriptBuildYaml,
        gitBasicSetting: GitCIBasicSetting,
        webhookParams: Map<String, String>,
        changeSet: Set<String>?,
        yamlTransferData: YamlTransferData?
    ): Pair<ModelCreateEvent, List<BuildFormProperty>> {
        val modelParams = getModelParams(
            event = event,
            yaml = yaml,
            gitBasicSetting = gitBasicSetting,
            webhookParams = webhookParams,
            yamlTransferData = yamlTransferData
        )

        val modelCreateEvent = ModelCreateEvent(
            userId = event.userId,
            projectCode = gitBasicSetting.projectCode!!,
            pipelineInfo = PipelineInfo(pipeline.pipelineId),
            gitData = GitData(
                repositoryUrl = gitBasicSetting.gitHttpUrl,
                gitProjectId = gitBasicSetting.gitProjectId,
                commitId = event.commitId,
                branch = event.branch
            ),
            streamData = StreamData(
                gitProjectId = event.gitProjectId,
                enableUserId = gitBasicSetting.enableUserId,
                requestEventId = event.id!!,
                objectKind = getObjectKindFromValue(event.objectKind)
            ),
            changeSet = changeSet,
            yamlTransferData = yamlTransferData
        )

        return Pair(modelCreateEvent, modelParams)
    }

    private fun getModelParams(
        event: GitRequestEvent,
        yaml: ScriptBuildYaml,
        gitBasicSetting: GitCIBasicSetting,
        webhookParams: Map<String, String> = mapOf(),
        yamlTransferData: YamlTransferData? = null
    ): List<BuildFormProperty> {
        val originEvent = try {
            objectMapper.readValue<GitEvent>(event.event)
        } catch (e: Exception) {
            logger.warn("Fail to parse the git web hook commit event, errMsg: ${e.message}")
            null
        }

        return ModelParameters.createPipelineParams(
            yaml = yaml,
            gitBasicSetting = gitBasicSetting,
            event = event,
            v2GitUrl = v2GitUrl,
            originEvent = originEvent,
            webhookParams = webhookParams,
            yamlTransferData = yamlTransferData
        )
    }

    private fun getObjectKindFromValue(value: String): TGitObjectKind {
        return when (value) {
            TGitObjectKind.PUSH.value -> TGitObjectKind.PUSH
            TGitObjectKind.TAG_PUSH.value -> TGitObjectKind.TAG_PUSH
            TGitObjectKind.MERGE_REQUEST.value -> TGitObjectKind.MERGE_REQUEST
            TGitObjectKind.MANUAL.value -> TGitObjectKind.MANUAL
            TGitObjectKind.SCHEDULE.value -> TGitObjectKind.SCHEDULE
            TGitObjectKind.DELETE.value -> TGitObjectKind.DELETE
            TGitObjectKind.OPENAPI.value -> TGitObjectKind.OPENAPI
            else -> TGitObjectKind.PUSH
        }
    }
}
