package com.tencent.devops.notify.service.inner

import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.notify.enums.EnumNotifyPriority
import com.tencent.devops.common.notify.enums.EnumNotifySource
import com.tencent.devops.common.notify.pojo.RtxNotifyPost
import com.tencent.devops.common.notify.utils.ChineseStringUtil
import com.tencent.devops.common.notify.utils.CommonUtils
import com.tencent.devops.model.notify.tables.records.TNotifyRtxRecord
import com.tencent.devops.notify.EXCHANGE_NOTIFY
import com.tencent.devops.notify.ROUTE_RTX
import com.tencent.devops.notify.dao.RtxNotifyDao
import com.tencent.devops.notify.model.RtxNotifyMessageWithOperation
import com.tencent.devops.notify.pojo.NotificationResponse
import com.tencent.devops.notify.pojo.NotificationResponseWithPage
import com.tencent.devops.notify.pojo.RtxNotifyMessage
import com.tencent.devops.notify.service.RtxService
import com.tencent.devops.notify.utils.TOFService.Companion.RTX_URL
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.LinkedList
import java.util.stream.Collectors

@Service
class RtxServiceImpl @Autowired constructor(
    private val tofService: com.tencent.devops.notify.utils.TOFService,
    private val rtxNotifyDao: RtxNotifyDao,
    private val rabbitTemplate: RabbitTemplate,
    private val tofConfiguration: com.tencent.devops.notify.utils.TOFConfiguration
) : RtxService {
    private val logger = LoggerFactory.getLogger(RtxServiceImpl::class.java)

    @Value("\${tof.defaultSystem.default-rtx-sender}")
    private lateinit var defaultRtxSender: String

    override fun sendMqMsg(message: RtxNotifyMessage) {
        rabbitTemplate.convertAndSend(EXCHANGE_NOTIFY, ROUTE_RTX, message)
    }

    /**
     * 发送RTX消息
     * @param rtxNotifyMessageWithOperation 消息对象
     */
    override fun sendMessage(rtxNotifyMessageWithOperation: RtxNotifyMessageWithOperation) {
        val rtxNotifyPosts = generateRtxNotifyPost(rtxNotifyMessageWithOperation)
        if (rtxNotifyPosts.isEmpty()) {
            logger.warn("List<RtxNotifyPost> is empty after being processed, RtxNotifyMessageWithOperation: $rtxNotifyMessageWithOperation")
            return
        }

        val retryCount = rtxNotifyMessageWithOperation.retryCount
        val batchId = rtxNotifyMessageWithOperation.batchId ?: UUIDUtil.generate()
        val tofConfs = tofConfiguration.getConfigurations(rtxNotifyMessageWithOperation.tofSysId)
        for (notifyPost in rtxNotifyPosts) {
            val id = rtxNotifyMessageWithOperation.id ?: UUIDUtil.generate()
            val result = tofService.post(
                RTX_URL, notifyPost, tofConfs!!)
            if (result.Ret == 0) {
                // 成功
                rtxNotifyDao.insertOrUpdateRtxNotifyRecord(
                    success = true,
                    source = rtxNotifyMessageWithOperation.source,
                    batchId = batchId,
                    id = id,
                    retryCount = retryCount,
                    lastErrorMessage = null,
                    receivers = notifyPost.receiver,
                    sender = notifyPost.sender,
                    title = notifyPost.title,
                    body = notifyPost.msgInfo,
                    priority = notifyPost.priority.toInt(),
                    contentMd5 = notifyPost.contentMd5,
                    frequencyLimit = notifyPost.frequencyLimit,
                    tofSysId = tofConfs["sys-id"],
                    fromSysId = notifyPost.fromSysId
                )
            } else {
                // 写入失败记录
                rtxNotifyDao.insertOrUpdateRtxNotifyRecord(
                    success = false,
                    source = rtxNotifyMessageWithOperation.source,
                    batchId = batchId,
                    id = id,
                    retryCount = retryCount,
                    lastErrorMessage = result.ErrMsg,
                    receivers = notifyPost.receiver,
                    sender = notifyPost.sender,
                    title = notifyPost.title,
                    body = notifyPost.msgInfo,
                    priority = notifyPost.priority.toInt(),
                    contentMd5 = notifyPost.contentMd5,
                    frequencyLimit = notifyPost.frequencyLimit,
                    tofSysId = tofConfs["sys-id"],
                    fromSysId = notifyPost.fromSysId
                )
                if (retryCount < 3) {
                    // 开始重试
                    reSendMessage(
                        post = notifyPost,
                        notifySource = rtxNotifyMessageWithOperation.source,
                        retryCount = retryCount + 1,
                        id = id,
                        batchId = batchId
                    )
                }
            }
        }
    }

    private fun reSendMessage(
        post: RtxNotifyPost,
        notifySource: EnumNotifySource,
        retryCount: Int,
        id: String,
        batchId: String
    ) {
        val rtxNotifyMessageWithOperation = RtxNotifyMessageWithOperation()
        rtxNotifyMessageWithOperation.apply {
            this.batchId = batchId
            this.id = id
            this.retryCount = retryCount
            this.source = notifySource
            this.sender = post.sender
            this.addAllReceivers(Sets.newHashSet(post.receiver.split(",")))
            this.priority = EnumNotifyPriority.parse(post.priority)
            this.body = post.msgInfo
            this.title = post.title
            this.frequencyLimit = post.frequencyLimit
            this.tofSysId = post.tofSysId
            this.fromSysId = post.fromSysId
        }

        rabbitTemplate.convertAndSend(EXCHANGE_NOTIFY, ROUTE_RTX, rtxNotifyMessageWithOperation) { message ->
            var delayTime = 0
            when (retryCount) {
                1 -> delayTime = 30000
                2 -> delayTime = 120000
                3 -> delayTime = 300000
            }
            if (delayTime > 0) {
                message.messageProperties.setHeader("x-delay", delayTime)
            }
            message
        }
    }

    private fun generateRtxNotifyPost(rtxNotifyMessage: RtxNotifyMessage): List<RtxNotifyPost> {
        val list = LinkedList<RtxNotifyPost>()
        val bodyList = ChineseStringUtil.split(rtxNotifyMessage.body, 960) ?: return list
        for (i in bodyList.indices) {
            val body = if (bodyList.size > 1) {
                String.format(
                    "%s(%d/%d)",
                    bodyList[i],
                    i + 1,
                    bodyList.size
                )
            } else {
                bodyList[i]
            }

            val contentMd5 = CommonUtils.getMessageContentMD5(rtxNotifyMessage.title, body)
            val receivers = Lists.newArrayList(
                filterReceivers(
                    receivers = rtxNotifyMessage.getReceivers(),
                    contentMd5 = contentMd5,
                    frequencyLimit = rtxNotifyMessage.frequencyLimit
                )
            )
            if (receivers == null || receivers.isEmpty()) {
                continue
            }

            for (j in 0 until receivers.size / 100 + 1) {
                val startIndex = j * 100
                var toIndex = j * 100 + 99
                if (toIndex > receivers.size - 1) {
                    toIndex = receivers.size
                }
                val subReceivers = receivers.subList(startIndex, toIndex)

                val post = RtxNotifyPost()
                post.apply {
                    receiver = subReceivers.joinToString(",")
                    msgInfo = body
                    title = rtxNotifyMessage.title
                    priority = rtxNotifyMessage.priority.getValue()
                    sender = if (rtxNotifyMessage.sender.isEmpty()) {
                        defaultRtxSender
                    } else {
                        rtxNotifyMessage.sender
                    }
                    this.contentMd5 = contentMd5
                    frequencyLimit = rtxNotifyMessage.frequencyLimit
                    tofSysId = rtxNotifyMessage.tofSysId
                    fromSysId = rtxNotifyMessage.fromSysId
                }

                list.add(post)
            }
        }
        return list
    }

    private fun filterReceivers(receivers: Set<String>, contentMd5: String, frequencyLimit: Int?): Set<String> {
        val filteredReceivers = HashSet(receivers)
        val filteredOutReceivers = HashSet<String>()
        if (frequencyLimit != null && frequencyLimit > 0) {
            val recordedReceivers = rtxNotifyDao.getReceiversByContentMd5AndTime(
                contentMd5, (frequencyLimit * 60).toLong()
            )
            receivers.forEach { rec ->
                for (recordedRec in recordedReceivers) {
                    if (",$recordedRec,".contains(rec)) {
                        filteredReceivers.remove(rec)
                        filteredOutReceivers.add(rec)
                        break
                    }
                }
            }
            logger.warn("Filtered out receivers:$filteredOutReceivers")
        }
        return filteredReceivers
    }

    override fun listByCreatedTime(
        page: Int,
        pageSize: Int,
        success: Boolean?,
        fromSysId: String?,
        createdTimeSortOrder: String?
    ): NotificationResponseWithPage<RtxNotifyMessageWithOperation> {
        val count = rtxNotifyDao.count(success, fromSysId)
        val result: List<NotificationResponse<RtxNotifyMessageWithOperation>> = if (count == 0) {
            listOf()
        } else {
            val records = rtxNotifyDao.list(
                page = page,
                pageSize = pageSize,
                success = success,
                fromSysId = fromSysId,
                createdTimeSortOrder = createdTimeSortOrder
            )
            records.stream().map(this::parseFromTNotifyRtxToResponse)?.collect(Collectors.toList()) ?: listOf()
        }
        return NotificationResponseWithPage(
            count = count,
            page = page,
            pageSize = pageSize,
            data = result
        )
    }

    private fun parseFromTNotifyRtxToResponse(record: TNotifyRtxRecord): NotificationResponse<RtxNotifyMessageWithOperation> {
        val receivers: MutableSet<String> = mutableSetOf()
        if (!record.receivers.isNullOrEmpty())
            receivers.addAll(record.receivers.split(";"))

        val message = RtxNotifyMessageWithOperation()
        message.apply {
            frequencyLimit = record.frequencyLimit
            fromSysId = record.fromSysId
            tofSysId = record.tofSysId
            body = record.body
            sender = record.sender
            title = record.title
            priority = EnumNotifyPriority.parse(record.priority.toString())
            source = EnumNotifySource.parseName(record.source)
            addAllReceivers(receivers)
            retryCount = record.retryCount
            lastError = record.lastError
        }

        return NotificationResponse(record.id, record.success,
            if (record.createdTime == null) null
            else
                DateTimeUtil.convertLocalDateTimeToTimestamp(record.createdTime),
            if (record.updatedTime == null) null
            else
                DateTimeUtil.convertLocalDateTimeToTimestamp(record.updatedTime),
            record.contentMd5, message)
    }
}