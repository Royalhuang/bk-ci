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
 *
 */

package com.tencent.devops.project.api.op

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.project.pojo.ProjectTagUpdateDTO
import com.tencent.devops.project.pojo.ProjectExtSystemTagDTO
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.Consumes
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Api(tags = ["OP_PROJECT_TAG"], description = "项目TAG")
@Path("/op/projects/tag")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface OpProjectTagResource {

    @ApiOperation("按项目设置consul Tag")
    @PUT
    @Path("/setTagByProject")
    fun setTagByProject(
        @ApiParam(value = "consulTag请求入参", required = true)
        projectTagUpdateDTO: ProjectTagUpdateDTO
    ): Result<Boolean>

    @ApiOperation("按组织设置consul Tag")
    @PUT
    @Path("/setTagByOrg")
    fun setTagByOrg(
        @ApiParam(value = "consulTag请求入参", required = true)
        projectTagUpdateDTO: ProjectTagUpdateDTO
    ): Result<Boolean>

    @ApiOperation("按组织设置consul Tag")
    @PUT
    @Path("/setTagByChannel")
    fun setTagByChannel(
        @ApiParam(value = "consulTag请求入参", required = true)
        projectTagUpdateDTO: ProjectTagUpdateDTO
    ): Result<Boolean>

    @ApiOperation("按项目扩展系统设置consul Tag")
    @PUT
    @Path("/ext/system/setTagByProject")
    fun setExtSystemTagByProject(
        @ApiParam(value = "consulTag请求入参", required = true)
        opProjectTagUpdateDTO: ProjectExtSystemTagDTO
    ): Result<Boolean>
}