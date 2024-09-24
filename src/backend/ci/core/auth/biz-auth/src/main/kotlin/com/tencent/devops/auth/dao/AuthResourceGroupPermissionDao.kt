package com.tencent.devops.auth.dao

import com.tencent.devops.auth.pojo.dto.ResourceGroupPermissionDTO
import com.tencent.devops.common.auth.api.AuthResourceType
import com.tencent.devops.model.auth.tables.TAuthResourceGroupPermission
import com.tencent.devops.model.auth.tables.records.TAuthResourceGroupPermissionRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
@Suppress("LongParameterList")
class AuthResourceGroupPermissionDao {
    fun batchCreate(
        dslContext: DSLContext,
        records: List<ResourceGroupPermissionDTO>
    ) {
        with(TAuthResourceGroupPermission.T_AUTH_RESOURCE_GROUP_PERMISSION) {
            records.forEach {
                dslContext.insertInto(
                    this,
                    PROJECT_CODE,
                    RESOURCE_TYPE,
                    RESOURCE_CODE,
                    IAM_RESOURCE_CODE,
                    GROUP_CODE,
                    IAM_GROUP_ID,
                    ACTION,
                    ACTION_RELATED_RESOURCE_TYPE,
                    RELATED_RESOURCE_TYPE,
                    RELATED_RESOURCE_CODE,
                    RELATED_IAM_RESOURCE_CODE
                ).values(
                    it.projectCode,
                    it.resourceType,
                    it.resourceCode,
                    it.iamResourceCode,
                    it.groupCode,
                    it.iamGroupId,
                    it.action,
                    it.actionRelatedResourceType,
                    it.relatedResourceType,
                    it.relatedResourceCode,
                    it.relatedIamResourceCode
                ).onDuplicateKeyIgnore()
                    .execute()
            }
        }
    }

    fun batchDeleteByIds(
        dslContext: DSLContext,
        projectCode: String,
        ids: List<Long>
    ) {
        with(TAuthResourceGroupPermission.T_AUTH_RESOURCE_GROUP_PERMISSION) {
            dslContext.deleteFrom(this)
                .where(ID.`in`(ids))
                .and(PROJECT_CODE.eq(projectCode))
                .execute()
        }
    }

    fun listByGroupId(
        dslContext: DSLContext,
        projectCode: String,
        iamGroupId: Int
    ): List<ResourceGroupPermissionDTO> {
        return with(TAuthResourceGroupPermission.T_AUTH_RESOURCE_GROUP_PERMISSION) {
            dslContext.selectFrom(this)
                .where(PROJECT_CODE.eq(projectCode))
                .and(IAM_GROUP_ID.eq(iamGroupId))
                .fetch().map { it.convert() }
        }
    }

    fun listByConditions(
        dslContext: DSLContext,
        projectCode: String,
        filterIamGroupIds: List<Int>?,
        resourceType: String,
        resourceCode: String?,
        pipelineGroupIds: List<String>,
        action: String?
    ): List<Int> {
        return with(TAuthResourceGroupPermission.T_AUTH_RESOURCE_GROUP_PERMISSION) {
            dslContext.select(IAM_GROUP_ID)
                .from(this)
                .where(
                    buildConditions(
                        projectCode = projectCode,
                        filterIamGroupIds = filterIamGroupIds,
                        resourceType = resourceType,
                        resourceCode = resourceCode,
                        pipelineGroupIds = pipelineGroupIds,
                        action = action
                    )
                )
                .groupBy(IAM_GROUP_ID)
                .fetch().map { it.value1() }
        }
    }

    fun buildConditions(
        projectCode: String,
        filterIamGroupIds: List<Int>?,
        resourceType: String,
        resourceCode: String?,
        pipelineGroupIds: List<String>,
        action: String?
    ): List<Condition> {
        with(TAuthResourceGroupPermission.T_AUTH_RESOURCE_GROUP_PERMISSION) {
            val conditions = mutableListOf<Condition>()
            conditions.add(PROJECT_CODE.eq(projectCode))
            conditions.add(ACTION_RELATED_RESOURCE_TYPE.eq(resourceType))
            action?.let { conditions.add(ACTION.eq(action)) }
            if (!filterIamGroupIds.isNullOrEmpty()) {
                conditions.add(IAM_GROUP_ID.`in`(filterIamGroupIds))
            }
            if (resourceCode != null) {
                conditions.add(
                    RELATED_RESOURCE_TYPE.eq(AuthResourceType.PROJECT.value).and(RELATED_RESOURCE_CODE.eq(projectCode))
                        .or(
                            RELATED_RESOURCE_TYPE.eq(resourceType).and(RELATED_RESOURCE_CODE.eq(resourceCode))
                        )
                        .let {
                            if (resourceType == AuthResourceType.PIPELINE_DEFAULT.value && pipelineGroupIds.isNotEmpty()) {
                                it.or(
                                    RELATED_RESOURCE_TYPE.eq(AuthResourceType.PIPELINE_GROUP.value).and(RELATED_RESOURCE_CODE.`in`(pipelineGroupIds))
                                )
                            } else {
                                it
                            }
                        }
                )
            }
            return conditions
        }
    }

    fun TAuthResourceGroupPermissionRecord.convert(): ResourceGroupPermissionDTO {
        return ResourceGroupPermissionDTO(
            id = id,
            projectCode = projectCode,
            resourceType = resourceType,
            resourceCode = resourceCode,
            iamResourceCode = iamResourceCode,
            groupCode = groupCode,
            iamGroupId = iamGroupId,
            action = action,
            actionRelatedResourceType = actionRelatedResourceType,
            relatedResourceType = relatedResourceType,
            relatedResourceCode = relatedResourceCode,
            relatedIamResourceCode = iamResourceCode
        )
    }
}
