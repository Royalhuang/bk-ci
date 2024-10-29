package com.tencent.devops.repository.sdk.github.service

import com.tencent.devops.repository.sdk.github.DefaultGithubClient
import com.tencent.devops.repository.sdk.github.request.GetUserEmailRequest
import com.tencent.devops.repository.sdk.github.request.GetUserRequest
import com.tencent.devops.repository.sdk.github.response.GetUserEmailResponse
import com.tencent.devops.repository.sdk.github.response.GetUserResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GithubUserService @Autowired constructor(
    private val defaultGithubClient: DefaultGithubClient
) {

    fun getUser(token: String): GetUserResponse {
        return defaultGithubClient.execute(oauthToken = token, request = GetUserRequest())
    }

    fun getUserEmail(token: String): List<GetUserEmailResponse> {
        return defaultGithubClient.execute(oauthToken = token, request = GetUserEmailRequest())
    }
}
