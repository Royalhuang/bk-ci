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

package com.tencent.devops.process.engine.atom.plugin

import com.tencent.devops.common.pipeline.container.Container
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.pojo.element.SubPipelineCallElement
import com.tencent.devops.common.pipeline.pojo.element.atom.BeforeDeleteParam
import com.tencent.devops.common.pipeline.pojo.element.atom.ElementCheckResult
import com.tencent.devops.process.plugin.ElementBizPlugin
import com.tencent.devops.process.plugin.annotation.ElementBiz
import org.springframework.beans.factory.annotation.Autowired

@ElementBiz
class SubPipelineCallElementBizPlugin @Autowired constructor(
    private val elementBizPluginServices: List<IElementBizPluginService>
) : ElementBizPlugin<SubPipelineCallElement> {

    override fun elementClass(): Class<SubPipelineCallElement> {
        return SubPipelineCallElement::class.java
    }

    override fun check(
        projectId: String?,
        userId: String,
        stage: Stage,
        container: Container,
        element: SubPipelineCallElement,
        contextMap: Map<String, String>,
        appearedCnt: Int,
        isTemplate: Boolean
    ): ElementCheckResult {
        return elementBizPluginServices.find {
            it.supportElement(element)
        }?.check(
            projectId = projectId,
            userId = userId,
            stage = stage,
            container = container,
            element = element,
            contextMap = contextMap,
            appearedCnt = appearedCnt,
            isTemplate = isTemplate
        ) ?: ElementCheckResult(true)
    }

    override fun beforeDelete(element: SubPipelineCallElement, param: BeforeDeleteParam) = Unit

    override fun afterCreate(
        element: SubPipelineCallElement,
        projectId: String,
        pipelineId: String,
        pipelineName: String,
        userId: String,
        channelCode: ChannelCode,
        create: Boolean,
        container: Container
    ) = Unit
}
