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

package com.tencent.devops.process.yaml.modelTransfer.inner

import com.tencent.devops.common.pipeline.pojo.element.market.MarketBuildAtomElement
import com.tencent.devops.common.pipeline.pojo.element.market.MarketBuildLessAtomElement
import com.tencent.devops.process.yaml.v3.models.step.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class TransferCreatorImpl @Autowired constructor() : TransferCreator {
    @Value("\${marketRun.enable:#{false}}")
    private val marketRunTaskData: Boolean = false

    @Value("\${marketRun.atomCode:#{null}}")
    private val runPlugInAtomCodeData: String? = null

    @Value("\${marketRun.atomVersion:#{null}}")
    private val runPlugInVersionData: String? = null

    @Value("\${container.defaultImage:#{null}}")
    private val defaultImageData: String = "mirrors.tencent.com/ci/tlinux3_ci:2.5.0"

    companion object {
        private const val STREAM_CHECK_AUTH_TYPE = "AUTH_USER_TOKEN"
    }

    override val marketRunTask: Boolean
        get() = marketRunTaskData

    override val runPlugInAtomCode: String?
        get() = runPlugInAtomCodeData

    override val runPlugInVersion: String?
        get() = runPlugInVersionData

    override val defaultImage: String
        get() = defaultImageData

    override fun transferCheckoutElement(
        step: Step
    ): MarketBuildAtomElement {
        // checkout插件装配
        val inputMap = mutableMapOf<String, Any?>()
        if (!step.with.isNullOrEmpty()) {
            inputMap.putAll(step.with!!)
        }

        inputMap["repositoryUrl"] = step.checkout!!

        // 用户未指定时缺省为 AUTH_USER_TOKEN 同时指定 开启人
        if (inputMap["authType"] == null) {
            inputMap["authType"] = STREAM_CHECK_AUTH_TYPE
        }

        // 拼装插件固定参数
        inputMap["repositoryType"] = "URL"

        val data = mutableMapOf<String, Any>()
        data["input"] = inputMap

        return MarketBuildAtomElement(
            id = step.taskId,
            name = step.name ?: "checkout",
            stepId = step.id,
            atomCode = "checkout",
            version = "1.*",
            data = data
        )
    }

    override fun transferMarketBuildAtomElement(
        step: Step
    ): MarketBuildAtomElement {
        val data = mutableMapOf<String, Any>()
        data["input"] = step.with ?: Any()
        return MarketBuildAtomElement(
            id = step.taskId,
            name = step.name ?: step.uses!!.split('@')[0],
            stepId = step.id,
            atomCode = step.uses!!.split('@')[0],
            version = step.uses!!.split('@')[1],
            data = data
        )
    }

    override fun transferMarketBuildLessAtomElement(step: Step): MarketBuildLessAtomElement {
        val data = mutableMapOf<String, Any>()
        data["input"] = step.with ?: Any()
        return MarketBuildLessAtomElement(
            id = step.taskId,
            name = step.name ?: step.uses!!.split('@')[0],
            stepId = step.id,
            atomCode = step.uses!!.split('@')[0],
            version = step.uses!!.split('@')[1],
            data = data
        )
    }
}