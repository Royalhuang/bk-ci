package com.tencent.devops.prebuild.component

import com.tencent.devops.common.api.exception.CustomException
import com.tencent.devops.common.ci.v2.ScriptBuildYaml
import com.tencent.devops.common.ci.v2.utils.ScriptYmlUtils
import com.tencent.devops.common.pipeline.container.NormalContainer
import com.tencent.devops.common.pipeline.container.VMBuildContainer
import com.tencent.devops.common.pipeline.type.agent.ThirdPartyAgentIDDispatchType
import com.tencent.devops.common.pipeline.type.devcloud.PublicDevCloudDispathcType
import com.tencent.devops.common.pipeline.type.docker.DockerDispatchType
import com.tencent.devops.prebuild.ServiceBaseTest
import com.tencent.devops.prebuild.pojo.CreateStagesRequest
import com.tencent.devops.prebuild.v2.component.PipelineLayout
import com.tencent.devops.prebuild.v2.component.PreCIYAMLValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class PipelineLayoutTest : ServiceBaseTest() {
    @InjectMocks
    lateinit var preCIYAMLValidator: PreCIYAMLValidator

    @Test
    @DisplayName("测试流水线模板_本地构建机")
    fun testLocal() {
        val scriptBuildYaml = getYamlObject(getYamlForLocal())
        val createStagesRequest = CreateStagesRequest(
            userId = userId,
            startUpReq = startUpReq,
            scriptBuildYaml = scriptBuildYaml,
            agentInfo = agentInfo,
            channelCode = channelCode
        )

        val model = PipelineLayout.Builder()
            .pipelineName(anyString())
            .description(anyString())
            .creator(userId)
            .stages(createStagesRequest)
            .build()

        // 测试是否VM
        assertTrue(
            model.stages.stream().anyMatch { x ->
                x.containers.stream().anyMatch { y -> y.getClassType() == VMBuildContainer.classType }
            }
        )

        val vmContainer = model.stages.stream()
            .flatMap { x -> x.containers.stream() }
            .filter { y -> y.getClassType() == VMBuildContainer.classType }
            .map { z -> z }
            .findFirst()
            .orElse(null)

        assertNotNull(vmContainer)
        // 测试调度类型
        assertTrue((vmContainer as VMBuildContainer).dispatchType is ThirdPartyAgentIDDispatchType)

        // 本地构建机展示的名字取自agentId
        val dispatchType = vmContainer.dispatchType as ThirdPartyAgentIDDispatchType
        assertEquals(agentInfo.agentId, dispatchType.displayName)
    }

    @Test
    @DisplayName("测试流水线模板_docker_on_vm")
    fun testDockerVM() {
        val scriptBuildYaml = getYamlObject(getYamlForDockerVM())
        val createStagesRequest = CreateStagesRequest(
            userId = userId,
            startUpReq = startUpReq,
            scriptBuildYaml = scriptBuildYaml,
            agentInfo = agentInfo,
            channelCode = channelCode
        )

        val model = PipelineLayout.Builder()
            .pipelineName(anyString())
            .description(anyString())
            .creator(userId)
            .stages(createStagesRequest)
            .build()

        assertTrue(
            model.stages.stream().anyMatch { x ->
                x.containers.stream().anyMatch { y -> y.getClassType() == VMBuildContainer.classType }
            }
        )

        val vmContainer = model.stages.stream()
            .flatMap { x -> x.containers.stream() }
            .filter { y -> y.getClassType() == VMBuildContainer.classType }
            .map { z -> z }
            .findFirst()
            .orElse(null)

        assertNotNull(vmContainer)
        assertTrue((vmContainer as VMBuildContainer).dispatchType is DockerDispatchType)
    }

    @Test
    @DisplayName("测试流水线模板_docker_on_devcloud")
    fun testDevCloud() {
        val scriptBuildYaml = getYamlObject(getYamlForDevCloud())
        val createStagesRequest = CreateStagesRequest(
            userId = userId,
            startUpReq = startUpReq,
            scriptBuildYaml = scriptBuildYaml,
            agentInfo = agentInfo,
            channelCode = channelCode
        )

        val model = PipelineLayout.Builder()
            .pipelineName(anyString())
            .description(anyString())
            .creator(userId)
            .stages(createStagesRequest)
            .build()

        assertTrue(
            model.stages.stream().anyMatch { x ->
                x.containers.stream().anyMatch { y -> y.getClassType() == VMBuildContainer.classType }
            }
        )

        val vmContainer = model.stages.stream()
            .flatMap { x -> x.containers.stream() }
            .filter { y -> y.getClassType() == VMBuildContainer.classType }
            .map { z -> z }
            .findFirst()
            .orElse(null)

        assertNotNull(vmContainer)
        assertTrue((vmContainer as VMBuildContainer).dispatchType is PublicDevCloudDispathcType)
    }

    @Test
    @DisplayName("测试流水线模板_无编译环境")
    fun testAgentLess() {
        val scriptBuildYaml = getYamlObject(getYamlForAgentLess())
        val createStagesRequest = CreateStagesRequest(
            userId = userId,
            startUpReq = startUpReq,
            scriptBuildYaml = scriptBuildYaml,
            agentInfo = agentInfo,
            channelCode = channelCode
        )

        val model = PipelineLayout.Builder()
            .pipelineName(anyString())
            .description(anyString())
            .creator(userId)
            .stages(createStagesRequest)
            .build()

        // 无编译环境均为NormalContainer，非VM没有dispatchType
        assertTrue(
            model.stages.stream().anyMatch { x ->
                x.containers.stream().anyMatch { y ->
                    y.getClassType() == NormalContainer.classType && y.name.contains("无编译环境")
                }
            }
        )
    }

    @Test
    @DisplayName("测试流水线模板_非法构建机")
    fun testInvalidDispatchType() {
        val scriptBuildYaml = getYamlObject(getYamlForInvalidDispatchType())
        val createStagesRequest = CreateStagesRequest(
            userId = userId,
            startUpReq = startUpReq,
            scriptBuildYaml = scriptBuildYaml,
            agentInfo = agentInfo,
            channelCode = channelCode
        )

        // 公共构建资源池不存在的
        assertThrows<CustomException> {
            PipelineLayout.Builder()
                .pipelineName(anyString())
                .description(anyString())
                .creator(userId)
                .stages(createStagesRequest)
                .build()
        }
    }

    private fun getYamlForDockerVM(): String {
        return PipelineLayoutTest::class.java.getResource("/docker_vm.yml").readText(Charsets.UTF_8)
    }

    private fun getYamlForDevCloud(): String {
        return PipelineLayoutTest::class.java.getResource("/docker_devcloud.yml").readText(Charsets.UTF_8)
    }

    private fun getYamlForLocal(): String {
        return PipelineLayoutTest::class.java.getResource("/local.yml").readText(Charsets.UTF_8)
    }

    private fun getYamlForAgentLess(): String {
        return PipelineLayoutTest::class.java.getResource("/agentless.yml").readText(Charsets.UTF_8)
    }

    private fun getYamlForInvalidDispatchType(): String {
        return PipelineLayoutTest::class.java.getResource("/error.yml").readText(Charsets.UTF_8)
    }

    private fun getYamlObject(yamlStr: String): ScriptBuildYaml {
        val (isPassed, preYamlObject, errorMsg) = preCIYAMLValidator.validate(yamlStr)
        val scriptBuildYaml = ScriptYmlUtils.normalizePreCiYaml(preYamlObject!!)

        return scriptBuildYaml
    }
}