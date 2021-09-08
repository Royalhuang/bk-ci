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

package com.tencent.devops.stream.trigger.timer.service

import com.cronutils.mapper.CronMapper
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.parser.CronParser
import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.event.enums.ActionType
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.model.stream.tables.records.TStreamTimerRecord
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_DEL_PIPELINE_TIMER
import com.tencent.devops.process.constant.ProcessMessageCode.ERROR_SAVE_PIPELINE_TIMER
import com.tencent.devops.stream.trigger.timer.pojo.StreamTimer
import com.tencent.devops.stream.trigger.timer.pojo.event.StreamChangeEvent
import com.tencent.devops.stream.trigger.timer.util.CronExpressionUtils
import com.tencent.devops.stream.v2.dao.StreamTimerDao
import org.jooq.DSLContext
import org.quartz.CronExpression
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 流水线定时服务
 * @version 1.0
 */
@Service
class StreamTimerService @Autowired constructor(
    private val dslContext: DSLContext,
    private val streamTimerDao: StreamTimerDao,
    private val pipelineEventDispatcher: PipelineEventDispatcher
) {

    companion object {
        private val logger = LoggerFactory.getLogger(StreamTimerService::class.java)
    }

    fun saveTimer(
        streamTimer: StreamTimer
    ): Result<Boolean> {
        with(streamTimer) {
            checkCrontab(crontabExpressions)
            val crontabJson = JsonUtil.toJson(crontabExpressions)
            return if (0 < streamTimerDao.save(dslContext, streamTimer)) {
                pipelineEventDispatcher.dispatch(
                    StreamChangeEvent(
                        source = "saveTimer",
                        projectId = projectId,
                        pipelineId = pipelineId,
                        userId = userId,
                        crontabExpressionJson = crontabJson
                    )
                )
                Result(true)
            } else { // 终止定时器
                pipelineEventDispatcher.dispatch(
                    StreamChangeEvent(
                        source = "saveTimer_fail",
                        projectId = projectId,
                        pipelineId = pipelineId,
                        userId = userId,
                        crontabExpressionJson = crontabJson,
                        actionType = ActionType.TERMINATE
                    )
                )
                Result(ERROR_SAVE_PIPELINE_TIMER.toInt(), "添加流水线的定时触发器保存失败！可能是定时器参数过长！")
            }
        }
    }

    @SuppressWarnings("ThrowsCount")
    private fun checkCrontab(crontabExpressions: List<String>) {
        if (crontabExpressions.isEmpty()) {
            throw ErrorCodeException(
                defaultMessage = "定时触发器的定时参数不合法",
                errorCode = ProcessMessageCode.ILLEGAL_TIMER_CRONTAB
            )
        }
        crontabExpressions.forEach { crontabExpression ->
            val newConvertExpression = convertExpression(crontabExpression)
            if (!CronExpression.isValidExpression(newConvertExpression)) {
                throw ErrorCodeException(
                    defaultMessage = "定时触发器的定时参数[$crontabExpression]不合法",
                    errorCode = ProcessMessageCode.ILLEGAL_TIMER_CRONTAB,
                    params = arrayOf(crontabExpression)
                )
            }
            if (!CronExpressionUtils.isValidTimeInterval(newConvertExpression)) {
                throw ErrorCodeException(
                    defaultMessage = "定时触发器的定时参数[$crontabExpression]不能秒级触发",
                    errorCode = ProcessMessageCode.ILLEGAL_TIMER_INTERVAL_CRONTAB,
                    params = arrayOf(crontabExpression)
                )
            }
        }
    }

    private fun convertExpression(expression: String): String {
        val unixDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
        val parser = CronParser(unixDefinition)
        return try {
            val qaurtzCron = parser.parse(expression)
            val mapper = CronMapper.fromUnixToQuartz()
            mapper.map(qaurtzCron).asString()
        } catch (ignore: IllegalArgumentException) {
            expression
        }
    }

    fun deleteTimer(pipelineId: String, userId: String): Result<Boolean> {
        var count = 0
        val timerRecord = streamTimerDao.get(dslContext, pipelineId)
        if (timerRecord != null) {
            count = streamTimerDao.delete(dslContext, pipelineId)
            // 终止定时器
            pipelineEventDispatcher.dispatch(
                StreamChangeEvent(
                    source = "deleteTimer",
                    projectId = timerRecord.projectId,
                    pipelineId = pipelineId,
                    userId = userId,
                    crontabExpressionJson = timerRecord.crontab,
                    actionType = ActionType.TERMINATE
                )
            )
        }
        return if (count > 0) {
            Result(true)
        } else {
            Result(ERROR_DEL_PIPELINE_TIMER.toInt(), "删除流水线${pipelineId}定时触发调度失败！")
        }
    }

    fun get(pipelineId: String): StreamTimer? {
        val timerRecord = streamTimerDao.get(dslContext, pipelineId) ?: return null
        return convert(timerRecord)
    }

    private fun convert(timerRecord: TStreamTimerRecord): StreamTimer? {
        return StreamTimer(
            projectId = timerRecord.projectId,
            pipelineId = timerRecord.pipelineId,
            userId = timerRecord.creator,
            crontabExpressions = try {
                JsonUtil.to(timerRecord.crontab, object : TypeReference<List<String>>() {})
            } catch (ignored: Throwable) {
                listOf(timerRecord.crontab)
            },
            gitProjectId = timerRecord.gitProjectId,
            branchs = try {
                JsonUtil.to(timerRecord.crontab, object : TypeReference<List<String>>() {})
            } catch (ignored: Throwable) {
                listOf(timerRecord.crontab)
            },
            always = timerRecord.always,
            channelCode = try {
                ChannelCode.valueOf(timerRecord.channel)
            } catch (e: IllegalArgumentException) {
                logger.warn("Unkown channel code", e)
                return null
            }
        )
    }

    fun list(start: Int, limit: Int): Result<Collection<StreamTimer>> {
        if (start < 0) {
            return Result(emptyList())
        }
        val list = streamTimerDao.list(dslContext, start, limit)
        val timerList = mutableListOf<StreamTimer>()
        list.forEach { record ->
            timerList.add(convert(record) ?: return@forEach)
        }
        return Result(timerList)
    }
}
