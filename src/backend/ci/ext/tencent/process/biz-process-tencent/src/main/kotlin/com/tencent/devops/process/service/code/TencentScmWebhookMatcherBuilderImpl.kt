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

package com.tencent.devops.process.service.code

import com.tencent.devops.common.webhook.pojo.code.git.GitEvent
import com.tencent.devops.common.webhook.pojo.code.github.GithubEvent
import com.tencent.devops.common.webhook.pojo.code.p4.P4Event
import com.tencent.devops.common.webhook.pojo.code.svn.SvnCommitEvent
import com.tencent.devops.common.webhook.service.code.matcher.GithubWebHookMatcher
import com.tencent.devops.common.webhook.service.code.matcher.GitlabWebHookMatcher
import com.tencent.devops.common.webhook.service.code.matcher.P4WebHookMatcher
import com.tencent.devops.common.webhook.service.code.matcher.ScmWebhookMatcher
import com.tencent.devops.common.webhook.service.code.matcher.SvnWebHookMatcher
import com.tencent.devops.process.engine.service.code.ScmWebhookMatcherBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service

@Service
@Primary
class TencentScmWebhookMatcherBuilderImpl : ScmWebhookMatcherBuilder {

    @Value("\${git.includeHost:#{null}}")
    private val gitIncludeHost = ""

    override fun createGitWebHookMatcher(event: GitEvent): ScmWebhookMatcher =
        TencentGitWebHookMatcher(gitEvent = event, gitIncludeHost = gitIncludeHost)

    override fun createSvnWebHookMatcher(
        event: SvnCommitEvent
    ): ScmWebhookMatcher = SvnWebHookMatcher(event)

    override fun createGitlabWebHookMatcher(event: GitEvent): ScmWebhookMatcher = GitlabWebHookMatcher(event)

    override fun createGithubWebHookMatcher(event: GithubEvent): ScmWebhookMatcher = GithubWebHookMatcher(event)

    override fun createP4WebHookMatcher(event: P4Event): ScmWebhookMatcher = P4WebHookMatcher(event)
}