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

package com.tencent.devops.metrics.config

import com.tencent.devops.common.event.annotation.EventConsumer
import com.tencent.devops.common.event.pojo.measure.QualityReportEvent
import com.tencent.devops.common.stream.constants.StreamBinding
import com.tencent.devops.metrics.listener.CodeCheckDailyMessageListener
import com.tencent.devops.metrics.listener.QualityReportDailyMessageListener
import com.tencent.devops.metrics.listener.TurboDailyReportMessageListener
import com.tencent.devops.metrics.service.MetricsThirdPlatformDataReportFacadeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import java.util.function.Consumer

@Configuration
class MetricsThirdPartyListenerConfiguration {

    @Bean
    fun codeCheckDailyListener(
        @Autowired thirdPlatformDataReportFacadeService: MetricsThirdPlatformDataReportFacadeService
    ) = CodeCheckDailyMessageListener(
        metricsThirdPlatformDataReportFacadeService = thirdPlatformDataReportFacadeService
    )

    @EventConsumer(EXCHANGE_METRICS_STATISTIC_CODECC_DAILY, STREAM_CONSUMER_GROUP)
    fun codeCheckDailyMessageListener(
        @Autowired listener: CodeCheckDailyMessageListener
    ): Consumer<Message<String>> {
        return Consumer { event: Message<String> ->
            listener.execute(event.payload)
        }
    }

    @EventConsumer(StreamBinding.EXCHANGE_QUALITY_DAILY_FANOUT, STREAM_CONSUMER_GROUP)
    fun metricsQualityDailyReportListener(
        @Autowired listener: QualityReportDailyMessageListener
    ): Consumer<Message<QualityReportEvent>> {
        return Consumer { event: Message<QualityReportEvent> ->
            listener.execute(event.payload)
        }
    }

    @EventConsumer(EXCHANGE_METRICS_STATISTIC_TURBO_DAILY, STREAM_CONSUMER_GROUP)
    fun metricsTurboDailyReportListener(
        @Autowired listener: TurboDailyReportMessageListener
    ): Consumer<Message<String>> {
        return Consumer { event: Message<String> ->
            listener.execute(event.payload)
        }
    }

    companion object {
        const val STREAM_CONSUMER_GROUP = "metrics-service"
        private const val EXCHANGE_METRICS_STATISTIC_CODECC_DAILY = "metrics.statistic.codecc.daily"
        private const val EXCHANGE_METRICS_STATISTIC_TURBO_DAILY = "metrics.statistic.turbo.daily"
    }
}
