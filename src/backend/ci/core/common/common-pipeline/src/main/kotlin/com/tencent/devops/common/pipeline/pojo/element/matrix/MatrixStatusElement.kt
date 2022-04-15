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

package com.tencent.devops.common.pipeline.pojo.element.matrix

import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.atom.ManualReviewParam
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线模型-矩阵纯运行状态插件", description = MatrixStatusElement.classType)
data class MatrixStatusElement(
    @ApiModelProperty("任务名称", required = true)
    override var name: String = "状态插件",
    @ApiModelProperty("插件ID", required = false)
    override var id: String? = null,
    @ApiModelProperty("执行状态", required = false)
    override var status: String? = null,
    @ApiModelProperty("执行次数", required = false)
    override var executeCount: Int = 1,
    @ApiModelProperty("执行时间", required = false)
    override var elapsed: Long? = null,
    @ApiModelProperty("启动时间", required = false)
    override var startEpoch: Long? = null,
    @ApiModelProperty("上下文标识", required = false)
    override var stepId: String?,
    @ApiModelProperty("原插件的类型标识")
    var originClassType: String,
    @ApiModelProperty("审核人列表", required = true)
    var reviewUsers: Set<String>? = null,
    // 当状态插件为质量红线时特有的参数
    @ApiModelProperty("拦截原子", required = false)
    var interceptTask: String? = null,
    @ApiModelProperty("拦截原子名称", required = false)
    var interceptTaskName: String? = null,
    // 当状态插件为人工审核时特有的参数
    @ApiModelProperty("描述", required = false)
    var desc: String? = "",
    @ApiModelProperty("审核意见", required = false)
    var suggest: String? = "",
    @ApiModelProperty("参数列表", required = false)
    var params: MutableList<ManualReviewParam> = mutableListOf(),
    @ApiModelProperty("输出变量名空间", required = false)
    var namespace: String? = "",
    @ApiModelProperty("发送的通知类型", required = false)
    var notifyType: MutableList<String>? = null,
    @ApiModelProperty("发送通知的标题", required = false)
    var notifyTitle: String? = null
) : Element(
    name = name,
    status = status,
    executeCount = executeCount,
    elapsed = elapsed,
    startEpoch = startEpoch
) {

    companion object {
        const val classType = "matrixStatus"
    }

    override fun getClassType() = classType

    override fun getTaskAtom() = originClassType
}
