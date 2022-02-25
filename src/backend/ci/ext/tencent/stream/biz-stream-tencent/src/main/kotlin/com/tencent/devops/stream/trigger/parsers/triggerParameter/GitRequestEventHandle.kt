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

package com.tencent.devops.stream.trigger.parsers.triggerParameter

import com.tencent.devops.common.api.exception.CustomException
import com.tencent.devops.common.webhook.enums.code.tgit.TGitIssueAction
import com.tencent.devops.stream.pojo.GitRequestEvent
import com.tencent.devops.stream.pojo.TriggerBuildReq
import com.tencent.devops.common.webhook.enums.code.tgit.TGitMergeActionKind
import com.tencent.devops.common.webhook.enums.code.tgit.TGitObjectKind
import com.tencent.devops.common.webhook.enums.code.tgit.TGitReviewEventKind
import com.tencent.devops.common.webhook.pojo.code.git.GitCommit
import com.tencent.devops.common.webhook.pojo.code.git.GitCommitAuthor
import com.tencent.devops.common.webhook.pojo.code.git.GitIssueEvent
import com.tencent.devops.common.webhook.pojo.code.git.GitMergeRequestEvent
import com.tencent.devops.common.webhook.pojo.code.git.GitNoteEvent
import com.tencent.devops.common.webhook.pojo.code.git.GitPushEvent
import com.tencent.devops.common.webhook.pojo.code.git.GitReviewEvent
import com.tencent.devops.common.webhook.pojo.code.git.GitTagPushEvent
import com.tencent.devops.common.webhook.pojo.code.git.isDeleteBranch
import com.tencent.devops.common.webhook.pojo.code.git.isDeleteTag
import com.tencent.devops.stream.trigger.timer.pojo.event.StreamTimerBuildEvent
import com.tencent.devops.stream.v2.service.StreamGitTokenService
import com.tencent.devops.stream.v2.service.StreamScmService
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.Date
import javax.ws.rs.core.Response

@Component
class GitRequestEventHandle @Autowired constructor(
    private val streamGitTokenService: StreamGitTokenService,
    private val streamScmService: StreamScmService
) {

    fun createPushEvent(gitPushEvent: GitPushEvent, e: String): GitRequestEvent {
        val latestCommit = if (gitPushEvent.isDeleteBranch()) {
            // 删除事件，暂时无法获取到latest commit 相关信息
            null
        } else {
            getLatestCommit(
                gitPushEvent.after,
                gitPushEvent.commits
            )
        }
        return GitRequestEvent(
            id = null,
            objectKind = TGitObjectKind.PUSH.value,
            operationKind = gitPushEvent.operation_kind,
            extensionAction = gitPushEvent.action_kind,
            gitProjectId = gitPushEvent.project_id,
            sourceGitProjectId = null,
            branch = gitPushEvent.ref.removePrefix("refs/heads/"),
            targetBranch = null,
            commitId = gitPushEvent.after,
            commitMsg = latestCommit?.message,
            commitTimeStamp = getCommitTimeStamp(latestCommit?.timestamp),
            commitAuthorName = latestCommit?.author?.name,
            userId = gitPushEvent.user_name,
            totalCommitCount = gitPushEvent.total_commits_count.toLong(),
            mergeRequestId = null,
            event = e,
            description = "",
            mrTitle = null,
            gitEvent = gitPushEvent
        )
    }

    fun createMergeEvent(gitMrEvent: GitMergeRequestEvent, e: String): GitRequestEvent {
        val latestCommit = gitMrEvent.object_attributes.last_commit
        return GitRequestEvent(
            id = null,
            objectKind = TGitObjectKind.MERGE_REQUEST.value,
            operationKind = null,
            extensionAction = gitMrEvent.object_attributes.extension_action,
            gitProjectId = gitMrEvent.object_attributes.target_project_id,
            sourceGitProjectId = gitMrEvent.object_attributes.source_project_id,
            // Merged动作使用目标分支，因为源分支可能已被删除
            branch = if (gitMrEvent.object_attributes.action == TGitMergeActionKind.MERGE.value) {
                gitMrEvent.object_attributes.target_branch
            } else {
                gitMrEvent.object_attributes.source_branch
            },
            targetBranch = gitMrEvent.object_attributes.target_branch,
            commitId = latestCommit.id,
            commitMsg = latestCommit.message,
            commitTimeStamp = getCommitTimeStamp(latestCommit.timestamp),
            commitAuthorName = latestCommit.author.name,
            userId = gitMrEvent.user.username,
            totalCommitCount = 0,
            mergeRequestId = gitMrEvent.object_attributes.iid,
            event = e,
            description = "",
            mrTitle = gitMrEvent.object_attributes.title,
            gitEvent = gitMrEvent
        )
    }

    fun createTagPushEvent(gitTagPushEvent: GitTagPushEvent, e: String): GitRequestEvent {
        val latestCommit = if (gitTagPushEvent.isDeleteTag()) {
            // 删除事件，暂时无法获取到latest commit 相关信息
            null
        } else {
            getLatestCommit(
                gitTagPushEvent.after,
                gitTagPushEvent.commits
            )
        }
        return GitRequestEvent(
            id = null,
            objectKind = TGitObjectKind.TAG_PUSH.value,
            operationKind = gitTagPushEvent.operation_kind,
            extensionAction = null,
            gitProjectId = gitTagPushEvent.project_id,
            sourceGitProjectId = null,
            branch = gitTagPushEvent.ref.removePrefix("refs/tags/"),
            targetBranch = null,
            commitId = gitTagPushEvent.after,
            commitMsg = latestCommit?.message,
            commitTimeStamp = getCommitTimeStamp(latestCommit?.timestamp),
            commitAuthorName = latestCommit?.author?.name,
            userId = gitTagPushEvent.user_name,
            totalCommitCount = gitTagPushEvent.total_commits_count.toLong(),
            mergeRequestId = null,
            event = e,
            description = "",
            mrTitle = null,
            gitEvent = gitTagPushEvent
        )
    }

    fun createIssueEvent(gitIssueEvent: GitIssueEvent, e: String): GitRequestEvent {
        val gitProjectId = gitIssueEvent.objectAttributes.projectId
        val token = streamGitTokenService.getToken(gitProjectId)
        val defaultBranch = streamScmService.getProjectInfoRetry(
            token = token,
            gitProjectId = gitProjectId.toString(),
            useAccessToken = true
        ).defaultBranch!!
        val latestCommit = streamScmService.getCommitInfo(
            gitToken = token,
            projectName = gitProjectId.toString(),
            sha = defaultBranch
        )
        return GitRequestEvent(
            id = null,
            objectKind = TGitObjectKind.ISSUE.value,
            operationKind = "",
            extensionAction = TGitIssueAction.getDesc(gitIssueEvent.objectAttributes.action ?: ""),
            gitProjectId = gitProjectId,
            sourceGitProjectId = null,
            branch = defaultBranch,
            targetBranch = null,
            commitId = latestCommit?.id ?: "0",
            commitMsg = gitIssueEvent.objectAttributes.title,
            commitTimeStamp = getCommitTimeStamp(latestCommit?.committed_date),
            commitAuthorName = latestCommit?.author_name,
            userId = gitIssueEvent.user.username,
            totalCommitCount = 1,
            mergeRequestId = gitIssueEvent.objectAttributes.iid.toLong(),
            event = e,
            description = "",
            mrTitle = gitIssueEvent.objectAttributes.title,
            gitEvent = gitIssueEvent
        )
    }

    fun createNoteEvent(gitNoteEvent: GitNoteEvent, e: String): GitRequestEvent {
        val gitProjectId = gitNoteEvent.objectAttributes.projectId
        val token = streamGitTokenService.getToken(gitProjectId)
        val defaultBranch = streamScmService.getProjectInfoRetry(
            token = token,
            gitProjectId = gitProjectId.toString(),
            useAccessToken = true
        ).defaultBranch!!
        val latestCommit = streamScmService.getCommitInfo(
            gitToken = token,
            projectName = gitProjectId.toString(),
            sha = defaultBranch
        )
        return GitRequestEvent(
            id = null,
            objectKind = TGitObjectKind.NOTE.value,
            operationKind = "",
            extensionAction = "submitted",
            gitProjectId = gitProjectId,
            sourceGitProjectId = null,
            branch = defaultBranch,
            targetBranch = null,
            commitId = latestCommit?.id ?: "0",
            commitMsg = gitNoteEvent.objectAttributes.note,
            commitTimeStamp = getCommitTimeStamp(latestCommit?.committed_date),
            commitAuthorName = latestCommit?.author_name,
            userId = latestCommit?.author_name ?: "",
            totalCommitCount = 1,
            mergeRequestId = null,
            event = e,
            description = "",
            mrTitle = null,
            gitEvent = gitNoteEvent
        )
    }

    fun createReviewEvent(gitReviewEvent: GitReviewEvent, e: String): GitRequestEvent {
        val gitProjectId = gitReviewEvent.projectId
        val token = streamGitTokenService.getToken(gitProjectId)
        val defaultBranch = streamScmService.getProjectInfoRetry(
            token = token,
            gitProjectId = gitProjectId.toString(),
            useAccessToken = true
        ).defaultBranch!!
        val latestCommit = streamScmService.getCommitInfo(
            gitToken = token,
            projectName = gitProjectId.toString(),
            sha = defaultBranch
        )
        return GitRequestEvent(
            id = null,
            objectKind = TGitObjectKind.REVIEW.value,
            operationKind = "",
            extensionAction = when (gitReviewEvent.event) {
                TGitReviewEventKind.CREATE.value -> "created"
                TGitReviewEventKind.INVITE.value -> "updated"
                else -> gitReviewEvent.state
            },
            gitProjectId = gitProjectId,
            sourceGitProjectId = null,
            branch = defaultBranch,
            targetBranch = null,
            commitId = latestCommit?.id ?: "0",
            commitMsg = latestCommit?.message,
            commitTimeStamp = getCommitTimeStamp(latestCommit?.committed_date),
            commitAuthorName = latestCommit?.author_name,
            userId = if (gitReviewEvent.reviewer == null) {
                gitReviewEvent.author.username
            } else {
                gitReviewEvent.reviewer!!.reviewer.username
            },
            totalCommitCount = 1,
            mergeRequestId = gitReviewEvent.iid.toLong(),
            event = e,
            description = "",
            mrTitle = "",
            gitEvent = gitReviewEvent
        )
    }

    companion object {
        fun createManualTriggerEvent(userId: String, triggerBuildReq: TriggerBuildReq): GitRequestEvent {
            if (triggerBuildReq.branch.isBlank()) {
                throw CustomException(
                    status = Response.Status.BAD_REQUEST,
                    message = "branche cannot be empty"
                )
            }
            return GitRequestEvent(
                id = null,
                objectKind = triggerBuildReq.objectKind,
                operationKind = "",
                extensionAction = null,
                gitProjectId = triggerBuildReq.gitProjectId,
                sourceGitProjectId = null,
                branch = getBranchName(triggerBuildReq.branch!!),
                targetBranch = null,
                commitId = triggerBuildReq.commitId ?: "",
                commitMsg = triggerBuildReq.customCommitMsg,
                commitTimeStamp = getCommitTimeStamp(null),
                commitAuthorName = userId,
                userId = userId,
                totalCommitCount = 0,
                mergeRequestId = null,
                event = "",
                description = triggerBuildReq.description,
                mrTitle = "",
                gitEvent = null
            )
        }

        fun createScheduleTriggerEvent(
            streamTimerEvent: StreamTimerBuildEvent,
            buildBranch: String,
            buildCommit: String,
            buildCommitMessage: String,
            buildCommitAuthorName: String
        ): GitRequestEvent {
            return GitRequestEvent(
                id = null,
                objectKind = TGitObjectKind.SCHEDULE.value,
                operationKind = null,
                extensionAction = null,
                gitProjectId = streamTimerEvent.gitProjectId,
                sourceGitProjectId = null,
                branch = buildBranch,
                targetBranch = null,
                commitId = buildCommit,
                commitMsg = buildCommitMessage,
                commitTimeStamp = getCommitTimeStamp(null),
                commitAuthorName = buildCommitAuthorName,
                userId = streamTimerEvent.userId,
                totalCommitCount = 0,
                mergeRequestId = null,
                event = "",
                description = null,
                mrTitle = null,
                gitEvent = null
            )
        }

        private fun getLatestCommit(commitId: String?, commits: List<GitCommit>?): GitCommit? {
            if (commitId == null) {
                return if (commits.isNullOrEmpty()) {
                    null
                } else {
                    commits.last()
                }
            }
            commits?.forEach {
                if (it.id == commitId) {
                    return it
                }
            }
            return null
        }

        private fun getCommitTimeStamp(commitTimeStamp: String?): String {
            return if (commitTimeStamp.isNullOrBlank()) {
                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                formatter.format(Date())
            } else {
                val time = DateTime.parse(commitTimeStamp)
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                sdf.format(time.toDate())
            }
        }

        private fun getBranchName(ref: String): String {
            return when {
                ref.startsWith("refs/heads/") ->
                    ref.removePrefix("refs/heads/")
                ref.startsWith("refs/tags/") ->
                    ref.removePrefix("refs/tags/")
                else -> ref
            }
        }
    }

    private fun getLatestCommit(
        commitId: String?,
        commits: List<GitCommit>?
    ): GitCommit? {
        if (commitId == null) {
            return if (commits.isNullOrEmpty()) {
                null
            } else {
                commits.last()
            }
        }
        commits?.forEach {
            if (it.id == commitId) {
                return it
            }
        }
        return null
    }

    private fun getLatestCommit(
        commitId: String,
        gitProjectId: Long
    ): GitCommit? {
        return streamScmService.getCommitInfo(
            streamGitTokenService.getToken(gitProjectId),
            gitProjectId.toString(),
            commitId
        )?.let {
            GitCommit(
                id = it.id,
                message = it.message,
                timestamp = it.committed_date,
                author = GitCommitAuthor(
                    name = it.author_name,
                    email = it.author_email
                ),
                modified = null,
                added = null,
                removed = null
            )
        }
    }
}