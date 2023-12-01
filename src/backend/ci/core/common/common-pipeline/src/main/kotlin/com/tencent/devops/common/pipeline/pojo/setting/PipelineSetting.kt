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

package com.tencent.devops.common.pipeline.pojo.setting

import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.pipeline.utils.PIPELINE_RES_NUM_MIN
import com.tencent.devops.common.pipeline.utils.PIPELINE_SETTING_CONCURRENCY_GROUP_DEFAULT
import com.tencent.devops.common.pipeline.utils.PIPELINE_SETTING_MAX_CON_QUEUE_SIZE_MAX
import com.tencent.devops.common.pipeline.utils.PIPELINE_SETTING_MAX_QUEUE_SIZE_DEFAULT
import com.tencent.devops.common.pipeline.utils.PIPELINE_SETTING_WAIT_QUEUE_TIME_MINUTE_DEFAULT
import com.tencent.devops.common.web.annotation.BkField
import com.tencent.devops.common.web.constant.BkStyleEnum
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线配置")
data class PipelineSetting(
    @ApiModelProperty("项目id", required = false, accessMode = ApiModelProperty.AccessMode.READ_ONLY)
    var projectId: String = "",
    @ApiModelProperty("流水线id", required = false, accessMode = ApiModelProperty.AccessMode.READ_ONLY)
    var pipelineId: String = "",

    // 流水线基础配置
    @ApiModelProperty("流水线名称", required = false)
    var pipelineName: String = "",
    @ApiModelProperty("版本", required = false)
    var version: Int = 1,
    @ApiModelProperty("描述", required = false)
    var desc: String = "",
    @ApiModelProperty("标签列表", required = false)
    var labels: List<String> = emptyList(),
    @field:BkField(patternStyle = BkStyleEnum.BUILD_NUM_RULE_STYLE, required = false)
    @ApiModelProperty("构建号生成规则", required = false)
    var buildNumRule: String? = null, // 构建号生成规则

    // 通知订阅相关配置
    @Deprecated("被successSubscriptionList取代")
    @ApiModelProperty("订阅成功相关", required = false)
    var successSubscription: Subscription = Subscription(),
    @Deprecated("被failSubscriptionList取代")
    @ApiModelProperty("订阅失败相关", required = false)
    var failSubscription: Subscription = Subscription(),
    @ApiModelProperty("订阅成功通知组", required = false)
    var successSubscriptionList: List<Subscription>? = listOf(Subscription()),
    @ApiModelProperty("订阅失败通知组", required = false)
    var failSubscriptionList: List<Subscription>? = listOf(Subscription()),

    // 运行控制、流水线禁用相关配置
    @ApiModelProperty("Lock 类型", required = false)
    var runLockType: PipelineRunLockType = PipelineRunLockType.SINGLE_LOCK,
    @ApiModelProperty("最大排队时长", required = false)
    var waitQueueTimeMinute: Int = PIPELINE_SETTING_WAIT_QUEUE_TIME_MINUTE_DEFAULT,
    @ApiModelProperty("最大排队数量", required = false)
    var maxQueueSize: Int = PIPELINE_SETTING_MAX_QUEUE_SIZE_DEFAULT,
    @ApiModelProperty("并发时,设定的group", required = false)
    var concurrencyGroup: String? = PIPELINE_SETTING_CONCURRENCY_GROUP_DEFAULT,
    @ApiModelProperty("并发时,是否相同group取消正在执行的流水线", required = false)
    var concurrencyCancelInProgress: Boolean = false,

    // 平台系统控制相关配置 —— 不作为生成版本的配置
    @ApiModelProperty("并发构建数量限制", required = false)
    var maxConRunningQueueSize: Int? = PIPELINE_SETTING_MAX_CON_QUEUE_SIZE_MAX, // MULTIPLE类型时，并发构建数量限制
    @ApiModelProperty("保存流水线编排的最大个数", required = false)
    val maxPipelineResNum: Int = PIPELINE_RES_NUM_MIN, // 保存流水线编排的最大个数
    @ApiModelProperty("重试时清理引擎变量表", required = false)
    val cleanVariablesWhenRetry: Boolean? = false,
    @ApiModelProperty("YAML流水线特殊配置", required = false)
    var pipelineAsCodeSettings: PipelineAsCodeSettings?
) {

    // 校验流水线的通知设置是否为空，即用户为配置或使用默认配置
    fun notifySettingIsNull(): Boolean {
        var res = true
        if (this.successSubscription.types.isNotEmpty()) {
            res = false
        }
        if (this.failSubscription.types.isNotEmpty()) {
            res = false
        }
        if (!this.successSubscriptionList.isNullOrEmpty() &&
            this.successSubscriptionList!!.any { it.types.isNotEmpty() }
        ) {
            res = false
        }
        if (!this.failSubscriptionList.isNullOrEmpty() &&
            this.failSubscriptionList!!.any { it.types.isNotEmpty() }
        ) {
            res = false
        }
        return res
    }

    // 校验流水线的并发组设置是否为空，即用户为配置或使用默认配置
    fun concurrencySettingIsNull(): Boolean {
        return this.runLockType != PipelineRunLockType.GROUP_LOCK
    }
}