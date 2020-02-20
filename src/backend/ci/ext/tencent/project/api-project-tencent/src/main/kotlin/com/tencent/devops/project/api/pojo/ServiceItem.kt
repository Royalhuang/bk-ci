package com.tencent.devops.project.api.pojo

data class ServiceItem (
    val itemId: String,
    val itemName: String,
    val itemCode: String,
    val parentId: String,
    val htmlPath: String? = null,
    val htmlType: String? = null
)