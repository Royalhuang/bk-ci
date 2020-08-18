package com.tencent.devops.monitoring.job

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.notify.enums.EnumEmailFormat
import com.tencent.devops.monitoring.client.InfluxdbClient
import com.tencent.devops.monitoring.util.EmailUtil
import com.tencent.devops.notify.api.service.ServiceNotifyResource
import com.tencent.devops.notify.pojo.EmailNotifyMessage
import org.apache.commons.lang3.tuple.MutablePair
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MonitorNotifyJob @Autowired constructor(
    private val client: Client,
    private val influxdbClient: InfluxdbClient
) {

    @Value("\${sla.receivers:#{null}}")
    private lateinit var receivers: String

    @Value("\${sla.title:#{null}}")
    private lateinit var title: String

    /**
     * 每天发送日报
     */
    @Scheduled(cron = "0 0 10 * * ?")
    fun notifyDaily() {
        /*OpenAPI访问成功率
        页面500错误率
        CodeCC插件非编译型工具扫描成功率(DONE)
        工蜂回写成功率
        日志API成功率
        核心插件故障率(DONE)
        公共构建机准备成功率
        登录成功率*/

        val startTime = 0L
        val endTime = 2597664799999L

        val moduleMap = hashMapOf(
            codecc(startTime, endTime),
            atomMonitor(startTime, endTime),
            dispatchStatus(startTime, endTime)
        )

        val message = EmailNotifyMessage()
        message.addAllReceivers(receivers.split(",").toHashSet())
        message.title = title
        message.body = EmailUtil.getEmailBody(startTime, endTime, moduleMap)
        message.format = EnumEmailFormat.HTML
        client.get(ServiceNotifyResource::class).sendEmailNotify(message)
    }

    fun dispatchStatus(startTime: Long, endTime: Long): Pair<String, MutableList<Triple<String, String, String>>> {
        val sql =
            "SELECT sum(devcloud_total_count),sum(devcloud_success_count) FROM DispatchStatus_success_rat_count WHERE time>${startTime}000000 AND time<${endTime}000000 GROUP BY buildType"
        val queryResult = influxdbClient.select(sql)

        val rowList = mutableListOf<Triple<String, String, String>>()

        if (null != queryResult && !queryResult.hasError()) {
            queryResult.results.forEach { result ->
                result.series.forEach { serie ->
                    serie.run {
                        val count = serie.values[0][1].let { if (it is Number) it.toInt() else 1 }
                        val success = serie.values[0][2].let { if (it is Number) it.toInt() else 0 }
                        rowList.add(
                            Triple(
                                tags["buildType"] ?: "Unknown",
                                "${success * 100 / count}%",
                                "www.tencent.com"
                            )
                        )
                    }
                }
            }
        } else {
            logger.error("atomMonitor , get map error , errorMsg:${queryResult?.error}")
        }

        return "公共构建机统计" to rowList
    }

    fun atomMonitor(startTime: Long, endTime: Long): Pair<String, List<Triple<String, String, String>>> {
        val sql =
            "SELECT sum(total_count),sum(success_count),sum(CODE_GIT_total_count),sum(CODE_GIT_success_count),sum(UploadArtifactory_total_count),sum(UploadArtifactory_success_count)," +
                "sum(linuxscript_total_count),sum(linuxscript_success_count) FROM AtomMonitorData_success_rat_count WHERE time>${startTime}000000 AND time<${endTime}000000"
        val queryResult = influxdbClient.select(sql)

        var totalCount = 0
        var totalSuccess = 0
        var gitCount = 0
        var gitSuccess = 0
        var artiCount = 0
        var artiSuccess = 0
        var shCount = 0
        var shSuccess = 0

        if (null != queryResult && !queryResult.hasError()) {
            queryResult.results.forEach { result ->
                result.series.forEach { serie ->
                    serie.run {
                        totalCount = serie.values[0][1].let { if (it is Number) it.toInt() else 1 }
                        totalSuccess = serie.values[0][2].let { if (it is Number) it.toInt() else 0 }
                        gitCount = serie.values[0][3].let { if (it is Number) it.toInt() else 1 }
                        gitSuccess = serie.values[0][4].let { if (it is Number) it.toInt() else 0 }
                        artiCount = serie.values[0][5].let { if (it is Number) it.toInt() else 1 }
                        artiSuccess = serie.values[0][6].let { if (it is Number) it.toInt() else 0 }
                        shCount = serie.values[0][7].let { if (it is Number) it.toInt() else 1 }
                        shSuccess = serie.values[0][8].let { if (it is Number) it.toInt() else 0 }
                    }
                }
            }
        } else {
            logger.error("atomMonitor , get map error , errorMsg:${queryResult?.error}")
        }

        val rowList = mutableListOf<Triple<String, String, String>>()
        rowList.add(Triple("所有插件", "${totalSuccess * 100 / totalCount}%", "www.tencent.com"))
        rowList.add(Triple("Git插件", "${gitSuccess * 100 / gitCount}%", "www.tencent.com"))
        rowList.add(Triple("artifactory插件", "${artiSuccess * 100 / artiCount}%", "www.tencent.com"))
        rowList.add(Triple("linuxScript插件", "${shSuccess * 100 / shCount}%", "www.tencent.com"))

        return "核心插件统计" to rowList
    }

    fun codecc(startTime: Long, endTime: Long): Pair<String, List<Triple<String, String, String>>> {
        val successSql =
            "SELECT SUM(total_count)  FROM CodeccMonitor_reduce WHERE time>${startTime}000000 AND time<${endTime}000000 AND errorCode='0' GROUP BY toolName"
        val errorSql =
            "SELECT SUM(total_count)  FROM CodeccMonitor_reduce WHERE time>${startTime}000000 AND time<${endTime}000000 AND errorCode!='0' GROUP BY toolName"

        val successMap = getCodeCCMap(successSql)
        val errorMap = getCodeCCMap(errorSql)

        val reduceMap = HashMap<String/*toolName*/, MutablePair<Int/*success*/, Int/*error*/>>()

        for ((k, v) in successMap) {
            reduceMap[k] = MutablePair(v, 0)
        }
        for ((k, v) in errorMap) {
            if (reduceMap.containsKey(k)) {
                reduceMap[k]?.right = v
            } else {
                reduceMap[k] = MutablePair(0, v)
            }
        }

        val rowList =
            reduceMap.asSequence().sortedBy { it.value.left * 100 / it.value.right }.take(10).map {
                Triple(
                    it.key,
                    "${it.value.left * 100 / (it.value.left + it.value.right)}%",
                    "http://www.tencent.com"
                )
            }.toList()

        return "CodeCC工具统计" to rowList
    }

    private fun getCodeCCMap(sql: String): HashMap<String, Int> {
        val queryResult = influxdbClient.select(sql)
        val codeCCMap = HashMap<String/*toolName*/, Int/*count*/>()
        if (null != queryResult && !queryResult.hasError()) {
            queryResult.results.forEach { result ->
                result.series.forEach { serie ->
                    serie.run {
                        val key = tags["toolName"]
                        if (null != key) {
                            val value = if (values.size > 0 && values[0].size > 1) values[0][1] else 0
                            codeCCMap[key] = if (value is Number) value.toInt() else 0
                        }
                    }
                }
            }
        } else {
            logger.error("codecc , get map error , errorMsg:${queryResult?.error}")
        }
        return codeCCMap
    }

    companion object {
        private val logger = LoggerFactory.getLogger(MonitorNotifyJob::class.java)
    }
}