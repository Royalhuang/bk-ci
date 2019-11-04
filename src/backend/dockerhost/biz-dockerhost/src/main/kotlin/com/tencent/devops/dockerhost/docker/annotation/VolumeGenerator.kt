package com.tencent.devops.dockerhost.docker.annotation

/**
 * Docker Volume生成器注解，标示生成器
 */
annotation class VolumeGenerator(
    /**
     * 生成器说明
     */
    val description: String
)