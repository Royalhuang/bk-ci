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

package com.tencent.devops.gitci.resources.user

import com.tencent.devops.common.api.exception.CustomException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.gitci.api.user.UserGitCIGitCodeResource
import com.tencent.devops.gitci.v2.service.OauthService
import com.tencent.devops.gitci.v2.service.ScmService
import com.tencent.devops.scm.pojo.Commit
import com.tencent.devops.scm.pojo.GitCICreateFile
import com.tencent.devops.scm.pojo.GitCIProjectInfo
import org.springframework.beans.factory.annotation.Autowired
import javax.ws.rs.core.Response

@RestResource
class UserGitCIGitCodeResourceImpl @Autowired constructor(
    private val scmService: ScmService,
    private val oauthService: OauthService
) : UserGitCIGitCodeResource {
    override fun getGitCodeProjectInfo(userId: String, gitProjectId: Long): Result<GitCIProjectInfo?> {
        return Result(
            scmService.getProjectInfo(
                token = getToken(userId),
                gitProjectId = gitProjectId.toString(),
                useAccessToken = true
            )
        )
    }

    override fun getGitCodeCommits(
        userId: String,
        gitProjectId: Long,
        filePath: String?,
        branch: String?,
        since: String?,
        until: String?,
        page: Int,
        perPage: Int
    ): Result<List<Commit>?> {
        return Result(
            scmService.getCommits(
                token = getToken(userId = userId),
                gitProjectId = gitProjectId,
                filePath = filePath,
                branch = branch,
                since = since,
                until = until,
                page = page,
                perPage = perPage
            )
        )
    }

    override fun gitCodeCreateFile(
        userId: String,
        gitProjectId: String,
        gitCICreateFile: GitCICreateFile
    ): Result<Boolean> {
        return Result(
            scmService.createNewFile(
                token = getToken(userId = userId),
                gitProjectId = gitProjectId,
                gitCICreateFile = gitCICreateFile
            )
        )
    }

    private fun getToken(userId: String): String {
        val token = oauthService.getOauthToken(userId) ?: throw CustomException(
            Response.Status.FORBIDDEN,
            "用户$userId 无OAuth权限"
        )
        return token.accessToken
    }
}
