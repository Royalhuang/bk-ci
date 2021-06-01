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

package com.tencent.devops.dispatch.dao

import com.tencent.devops.model.dispatch.tables.TDispatchTstackVm
import com.tencent.devops.model.dispatch.tables.records.TDispatchTstackVmRecord
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class TstackVmDao {
    fun insertVm(
        dslContext: DSLContext,
        tstackVmId: String,
        vmIp: String,
        vmName: String,
        vmOs: String,
        vmOsVersion: String,
        vmCpu: String,
        vmMemory: String,
        status: String
    ): Long {
        with(TDispatchTstackVm.T_DISPATCH_TSTACK_VM) {
            val now = LocalDateTime.now()
            return dslContext.insertInto(this,
                    TSTACK_VM_ID,
                    VM_IP,
                    VM_NAME,
                    VM_OS,
                    VM_OS_VERSION,
                    VM_CPU,
                    VM_MEMORY,
                    STATUS,
                    CREATED_TIME,
                    UPDATED_TIME
            )
                    .values(
                            tstackVmId,
                            vmIp,
                            vmName,
                            vmOs,
                            vmOsVersion,
                            vmCpu,
                            vmMemory,
                            status,
                            now,
                            now
                    )
                    .returning(ID)
                    .fetchOne()!!.id.toLong()
        }
    }

    fun deleteVm(dslContext: DSLContext, id: Long) {
        with(TDispatchTstackVm.T_DISPATCH_TSTACK_VM) {
            dslContext.deleteFrom(this)
                    .where(ID.eq(id))
                    .execute()
        }
    }

    fun getOrNull(dslContext: DSLContext, id: Long): TDispatchTstackVmRecord? {
        with(TDispatchTstackVm.T_DISPATCH_TSTACK_VM) {
            return dslContext.selectFrom(this)
                    .where(ID.eq(id))
                    .fetchOne()
        }
    }

    fun updateStatus(dslContext: DSLContext, id: Long, status: String) {
        with(TDispatchTstackVm.T_DISPATCH_TSTACK_VM) {
            dslContext.update(this)
                    .set(STATUS, status)
                    .set(UPDATED_TIME, LocalDateTime.now())
                    .where(ID.eq(id))
                    .execute()
        }
    }

    fun listVmByStatus(dslContext: DSLContext, status: String): Result<TDispatchTstackVmRecord> {
        with(TDispatchTstackVm.T_DISPATCH_TSTACK_VM) {
            return dslContext.selectFrom(this)
                    .where(STATUS.eq(status))
                    .fetch()
        }
    }
}
