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

package com.tencent.devops.common.pipeline.element

import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.element.enums.ZhiyunOperation
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("织云-启动,停止,重启,热重启,卸载", description = ZhiyunInstanceMaintenanceElement.classType)
data class ZhiyunInstanceMaintenanceElement(
    @ApiModelProperty("任务名称", required = true)
    override val name: String = "织云-启动,停止,重启,热重启,卸载",
    @ApiModelProperty("id", required = false)
    override var id: String? = null,
    @ApiModelProperty("状态", required = false)
    override var status: String? = null,
    @ApiModelProperty("业务名", required = true)
    val product: String = "",
    @ApiModelProperty("包名", required = true)
    val pkgName: String = "",
    @ApiModelProperty("安装路径，如 /usr/local/services/taylor-1.0", required = true)
    val installPath: String = "",
    @ApiModelProperty("IP数组，不可重复，逗号分隔", required = true)
    val ips: String = "",
    @ApiModelProperty("\"start\"，\"stop\"，\"restart\"，\"reload\"，\"uninstall\"", required = true)
    val operation: ZhiyunOperation,
    @ApiModelProperty("有效值为\"true\"，不关注请传\"\"，传入为字符串\"true\"时，表示热启动", required = false)
    val graceful: Boolean?,
    @ApiModelProperty("分批升级的每批数量", required = false)
    val batchNum: String?,
    @ApiModelProperty("分批升级的间隔时间（秒）", required = false)
    val batchInterval: String?,
    @ApiModelProperty("当前版本号，当操作类型是ROLLBACK时必选", required = false)
    val curVersion: String?
) : Element(name, id, status) {
    companion object {
        const val classType = "zhiyunInstanceMaintenance"
    }

    override fun getTaskAtom(): String {
        return "zhiyunInstanceMaintenanceTaskAtom"
    }

    override fun getClassType() = classType
}
