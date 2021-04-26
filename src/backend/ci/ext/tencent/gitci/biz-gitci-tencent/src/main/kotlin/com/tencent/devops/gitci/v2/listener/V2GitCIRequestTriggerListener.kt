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

package com.tencent.devops.gitci.v2.listener

import com.tencent.devops.gitci.pojo.V2GitCIRequestTriggerEvent
import com.tencent.devops.gitci.constant.MQ
import com.tencent.devops.gitci.v2.service.trigger.TriggerBuildService
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.ExchangeTypes
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class V2GitCIRequestTriggerListener @Autowired
constructor(private val triggerBuildService: TriggerBuildService) {

    @RabbitListener(
        bindings = [(QueueBinding(
            key = [MQ.ROUTE_GITCI_REQUEST_TRIGGER_V2_EVENT], value = Queue(value = MQ.QUEUE_GITCI_REQUEST_TRIGGER_V2_EVENT, durable = "true"),
            exchange = Exchange(
                value = MQ.EXCHANGE_GITCI_REQUEST_TRIGGER_EVENT,
                durable = "true",
                delayed = "true",
                type = ExchangeTypes.DIRECT
            )
        ))]
    )
    fun listenGitCIRequestTriggerEvent(v2GitCIRequestTriggerEvent: V2GitCIRequestTriggerEvent) {
        try {
            triggerBuildService.gitStartBuild(
                pipeline = v2GitCIRequestTriggerEvent.pipeline,
                event = v2GitCIRequestTriggerEvent.event,
                scriptBuildYaml = v2GitCIRequestTriggerEvent.yaml,
                gitBuildId = v2GitCIRequestTriggerEvent.gitBuildId
            )
        } catch (e: Throwable) {
            logger.error("Fail to start the git ci build(${v2GitCIRequestTriggerEvent.event})", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(V2GitCIRequestTriggerListener::class.java)
    }
}
