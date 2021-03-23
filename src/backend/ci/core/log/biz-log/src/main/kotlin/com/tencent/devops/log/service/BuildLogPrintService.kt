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

package com.tencent.devops.log.service

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.event.annotation.Event
import com.tencent.devops.common.log.pojo.ILogEvent
import com.tencent.devops.common.log.pojo.LogEvent
import com.tencent.devops.common.service.utils.CommonUtils
import com.tencent.devops.common.web.mq.EXTEND_RABBIT_TEMPLATE_NAME
import com.tencent.devops.log.configuration.LogServiceConfig
import com.tencent.devops.log.jmx.LogPrintBean
import com.tencent.devops.log.util.LogErrorCodeEnum
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.RejectedExecutionException
import javax.annotation.Resource

class BuildLogPrintService @Autowired constructor(
    @Resource(name = EXTEND_RABBIT_TEMPLATE_NAME)
    private val rabbitTemplate: RabbitTemplate,
    private val logPrintBean: LogPrintBean,
    private val logServiceConfig: LogServiceConfig
) {

    private val logExecutorService = ThreadPoolExecutor(
        logServiceConfig.corePoolSize ?: 100,
        logServiceConfig.maxPoolSize ?: 100,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(logServiceConfig.taskQueueSize ?: 1000)
    )

    fun dispatchEvent(event: ILogEvent) {
        try {
            // 如果配置了长度限制才做截断处理
            if (event is LogEvent && logServiceConfig.lineMaxLength != null) fixEvent(event)
            val eventType = event::class.java.annotations.find { s -> s is Event } as Event
            rabbitTemplate.convertAndSend(eventType.exchange, eventType.routeKey, event) { message ->
                // 事件中的变量指定
                if (event.delayMills > 0) {
                    message.messageProperties.setHeader("x-delay", event.delayMills)
                } else if (eventType.delayMills > 0) {
                    // 事件类型固化默认值
                    message.messageProperties.setHeader("x-delay", eventType.delayMills)
                }
                message
            }
        } catch (ignored: Throwable) {
            logger.error("Fail to dispatch the event($event)", ignored)
        }
    }

    fun asyncDispatchEvent(event: ILogEvent): Result<Boolean> {
        return try {
            logExecutorService.execute {
                dispatchEvent(event)
            }
            Result(true)
        } catch (e: RejectedExecutionException) {
            // 队列满时的处理逻辑
            logger.error("[${event.buildId}] asyncDispatchEvent failed with queue tasks exceed the limit", e)
            Result(
                status = 509,
                message = LogErrorCodeEnum.PRINT_QUEUE_LIMIT.formatErrorMessage,
                data = false
            )
        }
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 10000)
    fun logExecutorPerformance() {
        logPrintBean.savePrintTaskCount(logExecutorService.taskCount)
        logPrintBean.savePrintActiveCount(logExecutorService.activeCount)
        logPrintBean.savePrintQueueSize(logExecutorService.queue.size)
    }

    private fun fixEvent(logEvent: LogEvent) {
        // 字符数超过32766时analyzer索引分析将失效，同时为保护系统稳定性，若配置值为空或负数则限制为32KB
        val maxLength = if (logServiceConfig.lineMaxLength == null || logServiceConfig.lineMaxLength!! <= 0) {
            32766
        } else {
            logServiceConfig.lineMaxLength!!
        }
        logEvent.logs.forEach {
            it.message = CommonUtils.interceptStringInLength(it.message, maxLength) ?: ""
        }
    }

    private val logger = LoggerFactory.getLogger(BuildLogPrintService::class.java)
}
