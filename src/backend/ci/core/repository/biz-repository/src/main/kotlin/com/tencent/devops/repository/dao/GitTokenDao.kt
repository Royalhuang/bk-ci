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

package com.tencent.devops.repository.dao

import com.tencent.devops.common.api.util.AESUtil
import com.tencent.devops.model.repository.tables.TRepositoryGitToken
import com.tencent.devops.model.repository.tables.records.TRepositoryGitTokenRecord
import com.tencent.devops.repository.pojo.oauth.GitToken
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import org.jooq.UpdateSetMoreStep
import org.jooq.impl.DSL

@Repository
class GitTokenDao {
    fun getAccessToken(dslContext: DSLContext, userId: String): TRepositoryGitTokenRecord? {
        with(TRepositoryGitToken.T_REPOSITORY_GIT_TOKEN) {
            return dslContext.selectFrom(this)
                .where(USER_ID.eq(userId))
                .fetchOne()
        }
    }

    fun saveAccessToken(dslContext: DSLContext, userId: String, token: GitToken): Int {
        with(TRepositoryGitToken.T_REPOSITORY_GIT_TOKEN) {
            return dslContext.insertInto(
                this,
                USER_ID,
                ACCESS_TOKEN,
                REFRESH_TOKEN,
                TOKEN_TYPE,
                EXPIRES_IN,
                CREATE_TIME
            )
                .values(
                    userId,
                    token.accessToken,
                    token.refreshToken,
                    token.tokenType,
                    token.expiresIn,
                    LocalDateTime.now()
                )
                .onDuplicateKeyUpdate()
                .set(ACCESS_TOKEN, token.accessToken)
                .set(REFRESH_TOKEN, token.refreshToken)
                .set(TOKEN_TYPE, token.tokenType)
                .set(EXPIRES_IN, token.expiresIn)
                .set(CREATE_TIME, LocalDateTime.now())
                .execute()
        }
    }

    fun deleteToken(dslContext: DSLContext, userId: String): Int {
        with(TRepositoryGitToken.T_REPOSITORY_GIT_TOKEN) {
            return dslContext.deleteFrom(this)
                .where(USER_ID.eq(userId))
                .execute()
        }
    }

    fun convertEncryptedToken(dslContext: DSLContext, oldKey: String, newKey: String): Int {
        with(TRepositoryGitToken.T_REPOSITORY_GIT_TOKEN) {
            var count = 0
            dslContext.transaction { configuration ->
                val updates = mutableListOf<UpdateSetMoreStep<TRepositoryGitTokenRecord>>()
                val transactionContext = DSL.using(configuration)
                transactionContext.selectFrom(this)
                    .fetch().forEach { record ->
                        val originContent = AESUtil.decrypt(oldKey, record.accessToken)
                        val update = transactionContext.update(this)
                            .set(ACCESS_TOKEN, AESUtil.encrypt(newKey, originContent))
                        update.where(ID.eq(record.id))
                        updates.add(update)
                        count++
                    }
                transactionContext.batch(updates).execute()
            }
            return count
        }
    }
}
