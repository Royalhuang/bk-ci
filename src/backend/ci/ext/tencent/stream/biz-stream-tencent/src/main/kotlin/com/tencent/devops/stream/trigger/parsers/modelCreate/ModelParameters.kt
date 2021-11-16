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

package com.tencent.devops.stream.trigger.parsers.modelCreate

import com.tencent.devops.common.api.util.EmojiUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.ci.v2.ScriptBuildYaml
import com.tencent.devops.common.ci.v2.enums.gitEventKind.TGitObjectKind
import com.tencent.devops.common.pipeline.enums.BuildFormPropertyType
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.element.trigger.enums.CodeEventType
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_BASE_REF
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_BASE_REPO_URL
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_COMMIT_AUTHOR
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_COMMIT_MESSAGE
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_EVENT
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_EVENT_CONTENT
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_HEAD_REF
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_HEAD_REPO_URL
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_MR_ID
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_MR_IID
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_MR_URL
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_REF
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_REPO
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_REPO_GROUP
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_REPO_NAME
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_REPO_URL
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_SHA
import com.tencent.devops.common.pipeline.utils.PIPELINE_GIT_SHA_SHORT
import com.tencent.devops.stream.pojo.GitRequestEvent
import com.tencent.devops.stream.pojo.git.GitEvent
import com.tencent.devops.stream.pojo.git.GitMergeRequestEvent
import com.tencent.devops.stream.pojo.git.GitPushEvent
import com.tencent.devops.stream.pojo.git.GitTagPushEvent
import com.tencent.devops.stream.pojo.v2.GitCIBasicSetting
import com.tencent.devops.stream.trigger.v2.StreamYamlBuild
import com.tencent.devops.stream.v2.common.CommonVariables
import com.tencent.devops.process.utils.PIPELINE_BUILD_MSG
import com.tencent.devops.process.utils.PIPELINE_WEBHOOK_EVENT_TYPE
import com.tencent.devops.process.utils.PIPELINE_WEBHOOK_SOURCE_BRANCH
import com.tencent.devops.process.utils.PIPELINE_WEBHOOK_SOURCE_URL
import com.tencent.devops.process.utils.PIPELINE_WEBHOOK_TARGET_BRANCH
import com.tencent.devops.process.utils.PIPELINE_WEBHOOK_TARGET_URL
import com.tencent.devops.scm.pojo.BK_CI_RUN
import com.tencent.devops.scm.utils.code.git.GitUtils

@Suppress("ComplexMethod")
object ModelParameters {

    fun createPipelineParams(
        yaml: ScriptBuildYaml,
        gitBasicSetting: GitCIBasicSetting,
        event: GitRequestEvent,
        v2GitUrl: String?,
        originEvent: GitEvent?
    ): MutableList<BuildFormProperty> {
        val result = mutableListOf<BuildFormProperty>()

        val startParams = mutableMapOf<String, String>()
        val parsedCommitMsg = EmojiUtil.removeAllEmoji(event.commitMsg ?: "")

        // 通用参数
        startParams[CommonVariables.CI_PIPELINE_NAME] = yaml.name ?: ""
        startParams[CommonVariables.CI_BUILD_URL] = v2GitUrl ?: ""
        startParams[BK_CI_RUN] = "true"
        startParams[CommonVariables.CI_ACTOR] = if (event.objectKind == TGitObjectKind.SCHEDULE.value) {
            "system"
        } else {
            event.userId
        }
        startParams[CommonVariables.CI_BRANCH] = event.branch
        startParams[PIPELINE_GIT_COMMIT_MESSAGE] = parsedCommitMsg
        startParams[PIPELINE_GIT_SHA] = event.commitId
        if (event.commitId.isNotBlank() && event.commitId.length >= 8) {
            startParams[PIPELINE_GIT_SHA_SHORT] = event.commitId.substring(0, 8)
        }

        // 替换BuildMessage为了展示commit信息
        startParams[PIPELINE_BUILD_MSG] = parsedCommitMsg

        val gitProjectName = when (originEvent) {
            is GitPushEvent -> {
                startParams[PIPELINE_GIT_EVENT_CONTENT] = JsonUtil.toJson(
                    bean = originEvent.copy(commits = null),
                    formatted = false
                )
                startParams[PIPELINE_GIT_REPO_URL] = originEvent.repository.git_http_url
                startParams[PIPELINE_GIT_REF] = originEvent.ref
                startParams[CommonVariables.CI_BRANCH] = ModelCommon.getBranchName(originEvent.ref)
                startParams[PIPELINE_GIT_EVENT] = GitPushEvent.classType
                startParams[PIPELINE_GIT_COMMIT_AUTHOR] =
                    originEvent.commits?.first { it.id == originEvent.after }?.author?.name ?: ""
                GitUtils.getProjectName(originEvent.repository.git_http_url)
            }
            is GitTagPushEvent -> {
                startParams[PIPELINE_GIT_EVENT_CONTENT] = JsonUtil.toJson(
                    bean = originEvent.copy(commits = null),
                    formatted = false
                )
                startParams[PIPELINE_GIT_REPO_URL] = originEvent.repository.git_http_url
                startParams[PIPELINE_GIT_REF] = originEvent.ref
                startParams[CommonVariables.CI_BRANCH] = ModelCommon.getBranchName(originEvent.ref)
                startParams[PIPELINE_GIT_EVENT] = GitTagPushEvent.classType
                startParams[PIPELINE_GIT_COMMIT_AUTHOR] =
                    originEvent.commits?.get(0)?.author?.name ?: ""
                GitUtils.getProjectName(originEvent.repository.git_http_url)
            }
            is GitMergeRequestEvent -> {
                startParams[PIPELINE_GIT_EVENT_CONTENT] = JsonUtil.toJson(
                    bean = originEvent,
                    formatted = false
                )
                startParams[PIPELINE_GIT_REPO_URL] = gitBasicSetting.gitHttpUrl
                startParams[PIPELINE_GIT_BASE_REPO_URL] = originEvent.object_attributes.source.http_url
                startParams[PIPELINE_GIT_HEAD_REPO_URL] = originEvent.object_attributes.target.http_url
                startParams[PIPELINE_GIT_MR_URL] = originEvent.object_attributes.url
                startParams[PIPELINE_GIT_EVENT] = GitMergeRequestEvent.classType
                startParams[PIPELINE_GIT_HEAD_REF] = originEvent.object_attributes.target_branch
                startParams[PIPELINE_GIT_BASE_REF] = originEvent.object_attributes.source_branch
                startParams[PIPELINE_WEBHOOK_EVENT_TYPE] = CodeEventType.MERGE_REQUEST.name
                startParams[PIPELINE_WEBHOOK_SOURCE_BRANCH] = originEvent.object_attributes.source_branch
                startParams[PIPELINE_WEBHOOK_TARGET_BRANCH] = originEvent.object_attributes.target_branch
                startParams[PIPELINE_WEBHOOK_SOURCE_URL] = originEvent.object_attributes.source.http_url
                startParams[PIPELINE_WEBHOOK_TARGET_URL] = originEvent.object_attributes.target.http_url
                startParams[PIPELINE_GIT_MR_ID] = originEvent.object_attributes.id.toString()
                startParams[PIPELINE_GIT_MR_IID] = originEvent.object_attributes.iid.toString()
                startParams[PIPELINE_GIT_COMMIT_AUTHOR] = originEvent.object_attributes.last_commit.author.name
                GitUtils.getProjectName(originEvent.object_attributes.source.http_url)
            }
            else -> {
                startParams[PIPELINE_GIT_EVENT] = if (event.objectKind == TGitObjectKind.SCHEDULE.value) {
                    TGitObjectKind.SCHEDULE.value
                } else {
                    startParams[PIPELINE_GIT_COMMIT_AUTHOR] = event.userId
                    TGitObjectKind.MANUAL.value
                }
                startParams[PIPELINE_GIT_REPO_URL] = gitBasicSetting.gitHttpUrl
                GitUtils.getProjectName(gitBasicSetting.gitHttpUrl)
            }
        }

        startParams[PIPELINE_GIT_REPO] = gitProjectName
        val repoName = gitProjectName.split("/")
        val repoProjectName = if (repoName.size >= 2) {
            val index = gitProjectName.lastIndexOf("/")
            gitProjectName.substring(index + 1)
        } else {
            gitProjectName
        }
        val repoGroupName = if (repoName.size >= 2) {
            gitProjectName.removeSuffix("/$repoProjectName")
        } else {
            gitProjectName
        }
        startParams[PIPELINE_GIT_REPO_NAME] = repoProjectName
        startParams[PIPELINE_GIT_REPO_GROUP] = repoGroupName

        // 用户自定义变量
        // startParams.putAll(yaml.variables ?: mapOf())
        // putVariables2StartParams(yaml, gitBasicSetting, startParams)
        val buildFormProperties = getBuildFormPropertyFromYmlVariable(yaml, startParams)

        startParams.forEach {
            result.add(
                BuildFormProperty(
                    id = it.key,
                    required = false,
                    type = BuildFormPropertyType.STRING,
                    defaultValue = it.value,
                    options = null,
                    desc = null,
                    repoHashId = null,
                    relativePath = null,
                    scmType = null,
                    containerType = null,
                    glob = null,
                    properties = null
                )
            )
        }
        result.addAll(buildFormProperties)

        return result
    }

    private fun getBuildFormPropertyFromYmlVariable(
        yaml: ScriptBuildYaml,
        startParams: MutableMap<String, String>
    ): List<BuildFormProperty> {
        if (yaml.variables == null) {
            return emptyList()
        }
        val buildFormProperties = mutableListOf<BuildFormProperty>()
        yaml.variables!!.forEach { (key, variable) ->
            buildFormProperties.add(
                BuildFormProperty(
                    id = StreamYamlBuild.VARIABLE_PREFIX + key,
                    required = false,
                    type = BuildFormPropertyType.STRING,
                    defaultValue = ModelCommon.formatVariablesValue(variable.value, startParams) ?: "",
                    options = null,
                    desc = null,
                    repoHashId = null,
                    relativePath = null,
                    scmType = null,
                    containerType = null,
                    glob = null,
                    properties = null,
                    readOnly = variable.readonly
                )
            )
        }
        return buildFormProperties
    }
}