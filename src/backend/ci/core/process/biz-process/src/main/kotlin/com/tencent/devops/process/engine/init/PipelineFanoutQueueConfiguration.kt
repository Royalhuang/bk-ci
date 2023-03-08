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

package com.tencent.devops.process.engine.init

import com.tencent.devops.common.event.pojo.pipeline.PipelineBuildFinishBroadCastEvent
import com.tencent.devops.common.event.pojo.pipeline.PipelineBuildStartBroadCastEvent
import com.tencent.devops.common.event.annotation.EventConsumer
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.stream.constants.StreamBinding
import com.tencent.devops.process.engine.listener.run.PipelineNotifyQueueListener
import com.tencent.devops.process.engine.listener.run.PipelineWebHookQueueListener
import com.tencent.devops.process.engine.service.PipelineNotifyService
import com.tencent.devops.process.engine.service.PipelineWebHookQueueService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.messaging.Message
import java.util.function.Consumer

@Configuration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
class PipelineFanoutQueueConfiguration {

    companion object {
        private const val STREAM_CONSUMER_GROUP = "process-service"
    }

    @Bean
    fun pipelineWebHookQueueListener(
        @Autowired pipelineWebHookQueueService: PipelineWebHookQueueService
    ) = PipelineWebHookQueueListener(pipelineWebHookQueueService)

    @Bean
    fun pipelineNotifyQueueListener(
        @Autowired pipelineNotifyService: PipelineNotifyService,
        @Autowired pipelineEventDispatcher: PipelineEventDispatcher
    ) = PipelineNotifyQueueListener(pipelineNotifyService, pipelineEventDispatcher)

    /**
     * webhook构建触发广播监听
     */
    @EventConsumer(StreamBinding.EXCHANGE_PIPELINE_BUILD_START_FANOUT, STREAM_CONSUMER_GROUP)
    fun webHookQueueBuildStartListener(
        @Autowired buildListener: PipelineWebHookQueueListener
    ): Consumer<Message<PipelineBuildStartBroadCastEvent>> {
        return Consumer { event: Message<PipelineBuildStartBroadCastEvent> ->
            buildListener.onBuildStart(event.payload)
        }
    }

    /**
     * webhook和notify构建结束广播监听
     */
    @EventConsumer(StreamBinding.EXCHANGE_PIPELINE_BUILD_FINISH_FANOUT, STREAM_CONSUMER_GROUP)
    fun webHookQueueBuildFinishListener(
        @Autowired webhookListener: PipelineWebHookQueueListener,
        @Autowired notifyListener: PipelineNotifyQueueListener
    ): Consumer<Message<PipelineBuildFinishBroadCastEvent>> {
        return Consumer { event: Message<PipelineBuildFinishBroadCastEvent> ->
            webhookListener.onBuildFinish(event.payload)
            notifyListener.run(event.payload)
        }
    }
}