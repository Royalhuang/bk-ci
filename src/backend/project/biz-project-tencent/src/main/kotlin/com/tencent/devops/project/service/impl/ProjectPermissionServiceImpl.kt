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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.project.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.auth.api.BSAuthProjectApi
import com.tencent.devops.common.auth.api.pojo.ResourceRegisterInfo
import com.tencent.devops.common.auth.code.BkProjectAuthServiceCode
import com.tencent.devops.project.service.ProjectPermissionService
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class ProjectPermissionServiceImpl @Autowired constructor(
        private val objectMapper: ObjectMapper,
        private val authProjectApi: BSAuthProjectApi,
        private val bkProjectAuthServiceCode: BkProjectAuthServiceCode
) : ProjectPermissionService {

    @Value("\${auth.url}")
    private lateinit var authUrl: String

    override fun createResources(userId: String, projectList: List<ResourceRegisterInfo>) {

    }

    override fun deleteResource(projectCode: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun modifyResource(projectCode: String, projectName: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUserProjects(userId: String): List<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getUserProjectsAvailable(userId: String): Map<String, String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun request(request: Request, errorMessage: String): String {
//        val httpClient = okHttpClient.newBuilder().build()
        OkhttpUtils.doHttp(request).use { response ->
            //        httpClient.newCall(request).execute().use { response ->
            val responseContent = response.body()!!.string()
            if (!response.isSuccessful) {
                logger.warn("Fail to request($request) with code ${response.code()} , message ${response.message()} and response $responseContent")
                throw OperationException(errorMessage)
            }
            return responseContent
        }
    }

    override fun verifyUserProjectPermission(projectCode: String, userId: String): Boolean {
        return authProjectApi.isProjectUser(userId,bkProjectAuthServiceCode, projectCode, null)
    }

    companion object{
        val logger = LoggerFactory.getLogger(this:: class.java)
    }
}
