package com.tencent.devops.worker.common.service

import com.tencent.devops.common.expression.context.PipelineContextData
import com.tencent.devops.common.expression.context.RuntimeNamedValue
import com.tencent.devops.common.expression.context.StringContextData
import com.tencent.devops.worker.common.CI_TOKEN_CONTEXT
import com.tencent.devops.worker.common.api.ApiFactory
import com.tencent.devops.worker.common.api.engine.EngineBuildSDKApi

object CIKeywordsService {
    private val buildApi = ApiFactory.create(EngineBuildSDKApi::class)

    private var ciToken: String? = null

    fun getCiToken(): String? {
        if (!ciToken.isNullOrBlank()) {
            return ciToken
        }
        // 请求需要的值
        val token = buildApi.getJobContext()[CI_TOKEN_CONTEXT]
        ciToken = token
        return token
    }

    class CIKeywordsRuntimeNamedValue(
        override val key: String = "ci"
    ) : RuntimeNamedValue {
        override fun getValue(key: String): PipelineContextData? {
            // 不是需要的关键字直接返回空
            if (key != CI_TOKEN_CONTEXT.removePrefix("$key.")) {
                return null
            }
            return StringContextData(getCiToken() ?: "")
        }
    }
}
