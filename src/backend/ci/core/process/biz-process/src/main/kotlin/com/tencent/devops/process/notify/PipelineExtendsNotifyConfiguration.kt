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

package com.tencent.devops.process.notify

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.event.annotation.EventConsumer
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.stream.constants.StreamBinding
import com.tencent.devops.process.bean.PipelineUrlBean
import com.tencent.devops.process.engine.pojo.event.PipelineBuildNotifyEvent
import com.tencent.devops.process.service.ProjectCacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import java.util.function.Consumer

/**
 * 流水线扩展通知配置
 */
@Configuration
class PipelineExtendsNotifyConfiguration {

    companion object {
        private const val STREAM_CONSUMER_GROUP = "process-service"
    }

    @Bean
    fun notifyListener(
        @Autowired client: Client,
        @Autowired pipelineUrlBean: PipelineUrlBean,
        @Autowired projectCacheService: ProjectCacheService,
        @Autowired pipelineEventDispatcher: PipelineEventDispatcher
    ) = PipelineBuildNotifyListener(client, pipelineUrlBean, projectCacheService, pipelineEventDispatcher)

    /**
     * webhook构建触发广播监听
     */
    @EventConsumer(StreamBinding.QUEUE_PIPELINE_BUILD_NOTIFY, STREAM_CONSUMER_GROUP)
    fun pipelineBuildNotifyListener(
        @Autowired pipelineBuildNotifyListener: PipelineBuildNotifyListener
    ): Consumer<Message<PipelineBuildNotifyEvent>> {
        return Consumer { event: Message<PipelineBuildNotifyEvent> ->
            pipelineBuildNotifyListener.run(event.payload)
        }
    }

    @Bean
    fun pipelineBuildNotifyReminderQueue(): Queue {
        return Queue(MQ.QUEUE_PIPELINE_BUILD_REVIEW_REMINDER)
    }

    @Bean
    fun pipelineBuildNotifyReminderQueueBind(
        @Autowired pipelineBuildNotifyReminderQueue: Queue,
        @Autowired pipelineMonitorExchange: DirectExchange
    ): Binding {
        return BindingBuilder.bind(pipelineBuildNotifyReminderQueue).to(pipelineMonitorExchange)
            .with(MQ.ROUTE_PIPELINE_BUILD_REVIEW_REMINDER)
    }

    @Bean
    fun pipelineBuildNotifyReminderListenerContainer(
        @Autowired connectionFactory: ConnectionFactory,
        @Autowired pipelineBuildNotifyReminderQueue: Queue,
        @Autowired rabbitAdmin: RabbitAdmin,
        @Autowired pipelineAtomTaskReminderListener: PipelineAtomTaskReminderListener,
        @Autowired messageConverter: Jackson2JsonMessageConverter
    ): SimpleMessageListenerContainer {
        return Tools.createSimpleMessageListenerContainer(
            connectionFactory = connectionFactory,
            queue = pipelineBuildNotifyReminderQueue,
            rabbitAdmin = rabbitAdmin,
            buildListener = pipelineAtomTaskReminderListener,
            messageConverter = messageConverter,
            startConsumerMinInterval = 10000,
            consecutiveActiveTrigger = 5,
            concurrency = 1,
            maxConcurrency = 10
        )
    }
}
