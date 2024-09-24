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

package com.tencent.devops.auth.provider.rbac.service

import com.tencent.bk.sdk.iam.dto.InstancesDTO
import com.tencent.bk.sdk.iam.service.v2.V2ManagerService
import com.tencent.devops.auth.constant.AuthI18nConstants
import com.tencent.devops.auth.constant.AuthMessageCode
import com.tencent.devops.auth.dao.AuthResourceGroupDao
import com.tencent.devops.auth.dao.AuthResourceGroupPermissionDao
import com.tencent.devops.auth.pojo.RelatedResourceInfo
import com.tencent.devops.auth.pojo.dto.ResourceGroupPermissionDTO
import com.tencent.devops.auth.pojo.vo.GroupPermissionDetailVo
import com.tencent.devops.auth.service.AuthMonitorSpaceService
import com.tencent.devops.auth.service.iam.PermissionResourceGroupPermissionService
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.auth.api.AuthResourceType
import com.tencent.devops.common.auth.api.pojo.ProjectConditionDTO
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.service.trace.TraceTag
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.api.service.ServicePipelineViewResource
import com.tencent.devops.project.api.service.ServiceAllocIdResource
import com.tencent.devops.project.api.service.ServiceProjectResource
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import java.util.concurrent.Executors

@Suppress("LongParameterList")
class RbacPermissionResourceGroupPermissionService(
    private val v2ManagerService: V2ManagerService,
    private val rbacCacheService: RbacCacheService,
    private val monitorSpaceService: AuthMonitorSpaceService,
    private val authResourceGroupDao: AuthResourceGroupDao,
    private val dslContext: DSLContext,
    private val resourceGroupPermissionDao: AuthResourceGroupPermissionDao,
    private val converter: AuthResourceCodeConverter,
    private val client: Client
) : PermissionResourceGroupPermissionService {
    @Value("\${auth.iamSystem:}")
    private val systemId = ""

    @Value("\${monitor.register:false}")
    private val registerMonitor: Boolean = false

    @Value("\${monitor.iamSystem:}")
    private val monitorSystemId = ""

    companion object {
        private val logger = LoggerFactory.getLogger(RbacPermissionResourceGroupPermissionService::class.java)
        private const val AUTH_RESOURCE_GROUP_PERMISSION_ID_TAG = "AUTH_RESOURCE_GROUP_PERMISSION_ID"
        private const val ALL_RESOURCE = "*"
        private val syncExecutorService = Executors.newFixedThreadPool(5)
        private val syncProjectsExecutorService = Executors.newFixedThreadPool(10)
    }

    override fun listGroupsByPermissionConditions(
        projectCode: String,
        filterIamGroupIds: List<Int>?,
        relatedResourceType: String,
        relatedResourceCode: String?,
        action: String?
    ): List<Int> {
        val pipelineGroupIds = if (relatedResourceCode != null && relatedResourceType == AuthResourceType.PIPELINE_DEFAULT.value) {
            val viewIds = client.get(ServicePipelineViewResource::class).listViewIdsByPipelineId(
                projectId = projectCode,
                pipelineId = relatedResourceCode
            ).data ?: emptyList()
            viewIds.map { HashUtil.encodeLongId(it) }
        } else {
            emptyList()
        }
        return resourceGroupPermissionDao.listByConditions(
            dslContext = dslContext,
            projectCode = projectCode,
            filterIamGroupIds = filterIamGroupIds,
            resourceType = relatedResourceType,
            resourceCode = relatedResourceCode,
            pipelineGroupIds = pipelineGroupIds,
            action = action
        )
    }

    override fun getGroupPermissionDetail(groupId: Int): Map<String, List<GroupPermissionDetailVo>> {
        val groupPermissionMap = mutableMapOf<String, List<GroupPermissionDetailVo>>()
        groupPermissionMap[I18nUtil.getCodeLanMessage(AuthI18nConstants.BK_DEVOPS_NAME)] =
            getGroupPermissionDetailBySystem(systemId, groupId)
        if (registerMonitor) {
            val monitorGroupPermissionDetail = getGroupPermissionDetailBySystem(monitorSystemId, groupId)
            if (monitorGroupPermissionDetail.isNotEmpty()) {
                groupPermissionMap[I18nUtil.getCodeLanMessage(AuthI18nConstants.BK_MONITOR_NAME)] =
                    getGroupPermissionDetailBySystem(monitorSystemId, groupId)
            }
        }
        return groupPermissionMap
    }

    override fun getGroupPermissionDetailBySystem(iamSystemId: String, groupId: Int): List<GroupPermissionDetailVo> {
        val iamGroupPermissionDetailList = try {
            v2ManagerService.getGroupPermissionDetail(groupId, iamSystemId)
        } catch (e: Exception) {
            throw ErrorCodeException(
                errorCode = AuthMessageCode.GET_GROUP_PERMISSION_DETAIL_FAIL,
                params = arrayOf(groupId.toString()),
                defaultMessage = "Failed to get group($groupId) permission info"
            )
        }
        return iamGroupPermissionDetailList.map { detail ->
            val actionId = detail.id
            val relatedResourceTypesDTO = detail.resourceGroups[0].relatedResourceTypesDTO[0]
            val instances = relatedResourceTypesDTO.condition[0].instances
            val relatedResourceInfos = mutableListOf<RelatedResourceInfo>()
            instances.forEach { instance ->
                // 将resourceType转化为对应的资源类型名称
                buildRelatedResourceTypesName(
                    iamSystemId = iamSystemId,
                    instancesDTO = instance
                )
                relatedResourceInfos.add(
                    RelatedResourceInfo(
                        type = instance.type,
                        name = I18nUtil.getCodeLanMessage(
                            instance.type + AuthI18nConstants.RESOURCE_TYPE_NAME_SUFFIX
                        ),
                        instance = instance.path
                    )
                )
            }
            val (actionName, actionRelatedResourceType) = if (iamSystemId == monitorSystemId) {
                Pair(monitorSpaceService.getMonitorActionName(action = actionId), monitorSystemId)
            } else {
                val actionInfo = rbacCacheService.getActionInfo(action = actionId)
                Pair(actionInfo.actionName, actionInfo.relatedResourceType)
            }
            GroupPermissionDetailVo(
                actionId = actionId,
                name = actionName!!,
                actionRelatedResourceType = actionRelatedResourceType,
                relatedResourceInfos = relatedResourceInfos
            )
        }.sortedBy { it.actionId }
    }

    override fun syncGroup(projectCode: String, groupId: Int): Boolean {
        return try {
            val resourceGroupInfo = authResourceGroupDao.get(
                dslContext = dslContext,
                projectCode = projectCode,
                relationId = groupId.toString()
            ) ?: return true
            val groupPermissionDetails = getGroupPermissionDetailBySystem(systemId, groupId)
            logger.debug("sync group permissions: {}|{}|{}", projectCode, groupId, groupPermissionDetails)

            val latestResourceGroupPermissions = groupPermissionDetails.flatMap { permissionDetail ->
                permissionDetail.relatedResourceInfos.flatMap { relatedResourceInfo ->
                    relatedResourceInfo.instance.map { instancePathDTOs ->
                        val (relatedResourceType, relatedResourceCode, relatedIamResourceCode) = when {
                            instancePathDTOs.size > 1 && instancePathDTOs.last().id == ALL_RESOURCE -> {
                                Triple(AuthResourceType.PROJECT.value, projectCode, projectCode)
                            }

                            else -> {
                                val relatedIamResourceCode = converter.iamCode2Code(
                                    projectCode = projectCode,
                                    resourceType = instancePathDTOs.last().type,
                                    iamResourceCode = instancePathDTOs.last().id
                                )
                                Triple(instancePathDTOs.last().type, relatedIamResourceCode, instancePathDTOs.last().id)
                            }
                        }
                        ResourceGroupPermissionDTO(
                            projectCode = projectCode,
                            resourceType = resourceGroupInfo.resourceType,
                            resourceCode = resourceGroupInfo.resourceCode,
                            iamResourceCode = resourceGroupInfo.iamResourceCode,
                            groupCode = resourceGroupInfo.groupCode,
                            iamGroupId = groupId,
                            action = permissionDetail.actionId,
                            actionRelatedResourceType = permissionDetail.actionRelatedResourceType,
                            relatedResourceType = relatedResourceType,
                            relatedResourceCode = relatedResourceCode,
                            relatedIamResourceCode = relatedIamResourceCode
                        )
                    }
                }
            }.distinct()
            logger.debug("sync group | latest group permissions: {}", latestResourceGroupPermissions)

            val oldResourceGroupPermissions = resourceGroupPermissionDao.listByGroupId(dslContext, projectCode, groupId)
            logger.debug("sync group | old group permissions: {}", oldResourceGroupPermissions)

            val toDeleteRecords = oldResourceGroupPermissions.filter { it !in latestResourceGroupPermissions }
            logger.debug("sync group | to delete group permissions: {}", toDeleteRecords)

            val toAddRecords = latestResourceGroupPermissions.filter { it !in oldResourceGroupPermissions }.map {
                it.copy(
                    id = client.get(ServiceAllocIdResource::class).generateSegmentId(AUTH_RESOURCE_GROUP_PERMISSION_ID_TAG).data!!
                )
            }
            logger.debug("sync group | to add group permissions: {}", toAddRecords)

            dslContext.transaction { configuration ->
                val transactionContext = DSL.using(configuration)
                if (toDeleteRecords.isNotEmpty()) {
                    resourceGroupPermissionDao.batchDeleteByIds(
                        dslContext = transactionContext,
                        projectCode = projectCode,
                        ids = toDeleteRecords.map { it.id!! })
                }
                resourceGroupPermissionDao.batchCreate(
                    dslContext = dslContext,
                    records = toAddRecords
                )
            }
            true
        } catch (ex: Exception) {
            logger.warn("sync group permissions failed! $projectCode|$groupId|$ex")
            false
        }
    }

    override fun syncProject(projectCode: String): Boolean {
        val traceId = MDC.get(TraceTag.BIZID)
        syncProjectsExecutorService.submit {
            MDC.put(TraceTag.BIZID, traceId)
            logger.info("sync project group permissions:$projectCode")
            val iamGroupIds = authResourceGroupDao.listIamGroupIdsByConditions(
                dslContext = dslContext,
                projectCode = projectCode
            )
            logger.debug("sync project group permissions iamGroupIds:{}", iamGroupIds)
            iamGroupIds.forEach {
                syncGroup(
                    projectCode = projectCode,
                    groupId = it
                )
            }
        }
        return true
    }

    override fun syncByCondition(projectConditionDTO: ProjectConditionDTO): Boolean {
        logger.info("start to sync group permissions by condition by condition|$projectConditionDTO")
        val traceId = MDC.get(TraceTag.BIZID)
        syncExecutorService.submit {
            MDC.put(TraceTag.BIZID, traceId)
            var offset = 0
            val limit = PageUtil.MAX_PAGE_SIZE / 2
            do {
                val projectCodes = client.get(ServiceProjectResource::class).listProjectsByCondition(
                    projectConditionDTO = projectConditionDTO,
                    limit = limit,
                    offset = offset
                ).data ?: break
                projectCodes.forEach {
                    syncProject(it.englishName)
                }
                offset += limit
            } while (projectCodes.size == limit)
        }
        return true
    }

    private fun buildRelatedResourceTypesName(iamSystemId: String, instancesDTO: InstancesDTO) {
        instancesDTO.let {
            val resourceTypeName = if (iamSystemId == systemId) {
                rbacCacheService.getResourceTypeInfo(it.type).name
            } else {
                I18nUtil.getCodeLanMessage(AuthI18nConstants.BK_MONITOR_SPACE)
            }
            it.name = resourceTypeName
            it.path.forEach { element1 ->
                element1.forEach { element2 ->
                    element2.typeName = resourceTypeName
                }
            }
        }
    }
}
