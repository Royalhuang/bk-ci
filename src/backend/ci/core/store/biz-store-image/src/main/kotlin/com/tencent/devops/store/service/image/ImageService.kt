package com.tencent.devops.store.service.image

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.exception.DataConsistencyException
import com.tencent.devops.common.api.exception.InvalidParamException
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.type.docker.ImageType
import com.tencent.devops.common.service.utils.MessageCodeUtil
import com.tencent.devops.model.store.tables.records.TImageRecord
import com.tencent.devops.project.api.service.ServiceProjectResource
import com.tencent.devops.store.constant.StoreMessageCode
import com.tencent.devops.store.dao.common.CategoryDao
import com.tencent.devops.store.dao.common.ClassifyDao
import com.tencent.devops.store.dao.common.StoreMemberDao
import com.tencent.devops.store.dao.common.StoreProjectRelDao
import com.tencent.devops.store.dao.common.StoreStatisticDao
import com.tencent.devops.store.dao.image.Constants
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_CODE
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_ICON_URL
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_ID
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_NAME
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_TYPE
import com.tencent.devops.store.dao.image.Constants.KEY_CLASSIFY_ID
import com.tencent.devops.store.dao.image.Constants.KEY_CREATE_TIME
import com.tencent.devops.store.dao.image.Constants.KEY_CREATOR
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_CODE
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_FEATURE_PUBLIC_FLAG
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_FEATURE_RECOMMEND_FLAG
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_ID
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_LOGO_URL
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_NAME
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_RD_TYPE
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_REPO_NAME
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_REPO_URL
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_SIZE
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_SOURCE_TYPE
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_STATUS
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_SUMMARY
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_TAG
import com.tencent.devops.store.dao.image.Constants.KEY_IMAGE_VERSION
import com.tencent.devops.store.dao.image.Constants.KEY_LABEL_CODE
import com.tencent.devops.store.dao.image.Constants.KEY_LABEL_ID
import com.tencent.devops.store.dao.image.Constants.KEY_LABEL_NAME
import com.tencent.devops.store.dao.image.Constants.KEY_LABEL_TYPE
import com.tencent.devops.store.dao.image.Constants.KEY_MODIFIER
import com.tencent.devops.store.dao.image.Constants.KEY_PUBLISHER
import com.tencent.devops.store.dao.image.Constants.KEY_PUB_TIME
import com.tencent.devops.store.dao.image.Constants.KEY_UPDATE_TIME
import com.tencent.devops.store.dao.image.ImageAgentTypeDao
import com.tencent.devops.store.dao.image.ImageCategoryRelDao
import com.tencent.devops.store.dao.image.ImageDao
import com.tencent.devops.store.dao.image.ImageFeatureDao
import com.tencent.devops.store.dao.image.ImageLabelRelDao
import com.tencent.devops.store.dao.image.ImageVersionLogDao
import com.tencent.devops.store.dao.image.MarketImageDao
import com.tencent.devops.store.dao.image.MarketImageFeatureDao
import com.tencent.devops.store.exception.image.CategoryNotExistException
import com.tencent.devops.store.exception.image.ImageNotExistException
import com.tencent.devops.store.pojo.common.MarketItem
import com.tencent.devops.store.pojo.common.STORE_IMAGE_STATUS
import com.tencent.devops.store.pojo.common.VersionInfo
import com.tencent.devops.store.pojo.common.enums.ReleaseTypeEnum
import com.tencent.devops.store.pojo.common.enums.StoreTypeEnum
import com.tencent.devops.store.pojo.image.enums.CategoryTypeEnum
import com.tencent.devops.store.pojo.image.enums.ImageAgentTypeEnum
import com.tencent.devops.store.pojo.image.enums.ImageRDTypeEnum
import com.tencent.devops.store.pojo.image.enums.ImageStatusEnum
import com.tencent.devops.store.pojo.image.enums.LabelTypeEnum
import com.tencent.devops.store.pojo.image.enums.MarketImageSortTypeEnum
import com.tencent.devops.store.pojo.image.exception.UnknownImageSourceType
import com.tencent.devops.store.pojo.image.request.ImageBaseInfoUpdateRequest
import com.tencent.devops.store.pojo.image.request.ImageFeatureUpdateRequest
import com.tencent.devops.store.pojo.image.request.ImageUpdateRequest
import com.tencent.devops.store.pojo.image.response.Category
import com.tencent.devops.store.pojo.image.response.ImageDetail
import com.tencent.devops.store.pojo.image.response.ImageRepoInfo
import com.tencent.devops.store.pojo.image.response.Label
import com.tencent.devops.store.pojo.image.response.MarketImageItem
import com.tencent.devops.store.pojo.image.response.MarketImageMain
import com.tencent.devops.store.pojo.image.response.MarketImageResp
import com.tencent.devops.store.pojo.image.response.MyImage
import com.tencent.devops.store.service.common.ClassifyService
import com.tencent.devops.store.service.common.StoreCommentService
import com.tencent.devops.store.service.common.StoreMemberService
import com.tencent.devops.store.service.common.StoreUserService
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import kotlin.math.ceil

@Service
abstract class ImageService @Autowired constructor() {
    @Autowired
    lateinit var dslContext: DSLContext
    @Autowired
    lateinit var imageDao: ImageDao
    @Autowired
    lateinit var imageCategoryRelDao: ImageCategoryRelDao
    @Autowired
    lateinit var classifyDao: ClassifyDao
    @Autowired
    lateinit var categoryDao: CategoryDao
    @Autowired
    lateinit var imageFeatureDao: ImageFeatureDao
    @Autowired
    lateinit var imageAgentTypeDao: ImageAgentTypeDao
    @Autowired
    lateinit var imageVersionLogDao: ImageVersionLogDao
    @Autowired
    lateinit var marketImageDao: MarketImageDao
    @Autowired
    lateinit var marketImageFeatureDao: MarketImageFeatureDao
    @Autowired
    lateinit var imageLabelRelDao: ImageLabelRelDao
    @Autowired
    lateinit var storeMemberDao: StoreMemberDao
    @Autowired
    lateinit var storeProjectRelDao: StoreProjectRelDao
    @Autowired
    lateinit var storeStatisticDao: StoreStatisticDao
    @Autowired
    lateinit var imageCommonService: ImageCommonService
    @Autowired
    lateinit var storeCommentService: StoreCommentService
    @Autowired
    lateinit var storeUserService: StoreUserService
    @Autowired
    @Qualifier("imageMemberService")
    lateinit var storeMemberService: StoreMemberService
    @Autowired
    lateinit var classifyService: ClassifyService
    @Autowired
    lateinit var marketImageStatisticService: MarketImageStatisticService
    @Autowired
    lateinit var client: Client
    @Value("\${store.baseImageDocsLink}")
    private lateinit var baseImageDocsLink: String
    private val logger = LoggerFactory.getLogger(ImageService::class.java)

    fun getImageVersionListByCode(
        userId: String,
        imageCode: String,
        page: Int?,
        pageSize: Int?,
        interfaceName: String? = "Anon interface"
    ): Result<Page<ImageDetail>> {
        logger.info("$interfaceName:getImageVersionListByCode:Input:($userId,$imageCode,$page,$pageSize)")
        // 参数校验
        val validPage = PageUtil.getValidPage(page)
        // 默认拉取所有
        val validPageSize = pageSize ?: -1
        // 查数据库
        val count = imageDao.countByCode(dslContext, imageCode)
        val imageVersionList = imageDao.listImageByCode(
            dslContext = dslContext,
            imageCode = imageCode,
            page = page,
            pageSize = pageSize
        )?.map { it ->
            val imageId = it.get(KEY_IMAGE_ID) as String
            getImageDetailById(userId, imageId, interfaceName)
        } ?: emptyList()
        imageVersionList.sortBy {
            -it.createTime
        }
        val pageObj = Page(
            count = count.toLong(),
            page = validPage,
            pageSize = validPageSize,
            totalPages = if (validPageSize > 0) ceil(count * 1.0 / validPageSize).toInt() else -1,
            records = imageVersionList
        )
        logger.info("$interfaceName:getImageVersionListByCode:Output:Page($count,$validPage,$validPageSize,imageVersionList.size=${imageVersionList.size})")
        return Result(pageObj)
    }

    abstract fun generateInstallFlag(
        defaultFlag: Boolean,
        members: MutableList<String>?,
        userId: String,
        visibleList: MutableList<Int>?,
        userDeptList: List<Int>
    ): Boolean

    @Suppress("UNCHECKED_CAST")
    fun count(
        userId: String,
        userDeptList: List<Int>,
        imageName: String?,
        classifyCodeList: List<String>?,
        categoryCodeList: List<String>?,
        rdType: ImageRDTypeEnum?,
        labelCode: String?,
        score: Int?,
        imageSourceType: ImageType?,
        interfaceName: String? = "Anon interface"
    ): Int {
        // 获取镜像
        val labelCodeList = if (labelCode.isNullOrEmpty()) listOf() else labelCode?.split(",")
        return marketImageDao.count(
            dslContext = dslContext,
            imageName = imageName,
            classifyCodeList = classifyCodeList,
            categoryCodeList = categoryCodeList,
            rdType = rdType,
            labelCodeList = labelCodeList,
            score = score,
            imageSourceType = imageSourceType
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun doList(
        userId: String,
        userDeptList: List<Int>,
        imageName: String?,
        classifyCodeList: List<String>?,
        categoryCodeList: List<String>?,
        rdType: ImageRDTypeEnum?,
        labelCode: String?,
        score: Int?,
        imageSourceType: ImageType?,
        sortType: MarketImageSortTypeEnum?,
        desc: Boolean?,
        page: Int?,
        pageSize: Int?,
        interfaceName: String? = "Anon interface"
    ): List<MarketImageItem> {
        val results = mutableListOf<MarketImageItem>()

        // 获取镜像
        val labelCodeList = if (labelCode.isNullOrEmpty()) listOf() else labelCode?.split(",")
        val images = marketImageDao.list(
            dslContext = dslContext,
            imageName = imageName,
            classifyCodeList = classifyCodeList,
            categoryCodeList = categoryCodeList,
            rdType = rdType,
            labelCodeList = labelCodeList,
            score = score,
            imageSourceType = imageSourceType,
            sortType = sortType,
            desc = desc,
            page = page,
            pageSize = pageSize
        )
            ?: return emptyList()

        val imageCodeList = images.map {
            it[KEY_IMAGE_CODE] as String
        }.toList()
        logger.info("$interfaceName:doList:Inner:imageCodeList.size=${imageCodeList.size},imageCodeList=$imageCodeList")

        // 获取可见范围
        val imageVisibleData = batchGetVisibleDept(imageCodeList, StoreTypeEnum.IMAGE)
        val imageVisibleDataStr = StringBuilder("\n")
        imageVisibleData?.forEach {
            imageVisibleDataStr.append("${it.key}->${it.value}\n")
        }
        logger.info("$interfaceName:doList:Inner:imageVisibleData=$imageVisibleDataStr")

        // 获取热度
        val statField = mutableListOf<String>()
        statField.add("DOWNLOAD")
        val imageStatisticData = marketImageStatisticService.getStatisticByCodeList(imageCodeList).data

        // 获取用户
        val memberData = storeMemberService.batchListMember(imageCodeList, StoreTypeEnum.IMAGE).data

        // 获取分类
        val classifyList = classifyService.getAllClassify(StoreTypeEnum.IMAGE.type.toByte()).data
        val classifyMap = mutableMapOf<String, String>()
        classifyList?.forEach {
            classifyMap[it.id] = it.classifyCode
        }

        images.forEach {
            val imageCode = it[KEY_IMAGE_CODE] as String
            val visibleList = imageVisibleData?.get(imageCode)
            val statistic = imageStatisticData?.get(imageCode)
            val members = memberData?.get(imageCode)

            val installFlag = generateInstallFlag(it[KEY_IMAGE_FEATURE_PUBLIC_FLAG] as Boolean, members, userId, visibleList, userDeptList)
            val classifyId = it[KEY_CLASSIFY_ID] as String
            val (imageSizeNum, imageSize) = getImageSizeInfoByStr(it.get(KEY_IMAGE_SIZE) as String)
            results.add(
                MarketImageItem(
                    id = it[KEY_IMAGE_ID] as String,
                    code = imageCode,
                    name = it[KEY_IMAGE_NAME] as String,
                    rdType = ImageRDTypeEnum.getImageRDType((it[KEY_IMAGE_RD_TYPE] as Byte).toInt()),
                    imageSourceType = ImageType.getType(it[KEY_IMAGE_SOURCE_TYPE] as String).name,
                    imageSize = imageSize,
                    imageSizeNum = imageSizeNum,
                    classifyCode = if (classifyMap.containsKey(classifyId)) classifyMap[classifyId] ?: "" else "",
                    logoUrl = it[KEY_IMAGE_LOGO_URL] as? String,
                    version = it[KEY_IMAGE_VERSION] as String,
                    summary = it[KEY_IMAGE_SUMMARY] as? String,
                    score = statistic?.score ?: 0.toDouble(),
                    downloads = statistic?.downloads ?: 0,
                    publicFlag = it[KEY_IMAGE_FEATURE_PUBLIC_FLAG] as Boolean,
                    flag = installFlag,
                    recommendFlag = it[KEY_IMAGE_FEATURE_RECOMMEND_FLAG] as Boolean,
                    publisher = it[KEY_PUBLISHER] as String,
                    pubTime = (it[KEY_PUB_TIME] as LocalDateTime?)?.timestampmilli(),
                    creator = it[KEY_CREATOR] as String,
                    modifier = it[KEY_MODIFIER] as String,
                    createTime = (it[KEY_CREATE_TIME] as LocalDateTime).timestampmilli(),
                    updateTime = (it[KEY_UPDATE_TIME] as LocalDateTime).timestampmilli(),
                    isInstalled = null
                )
            )
        }
        return results
    }

    abstract fun batchGetVisibleDept(imageCodeList: List<String>, image: StoreTypeEnum): HashMap<String, MutableList<Int>>?

    /**
     * 镜像市场搜索镜像
     */
    fun searchImage(
        userId: String,
        imageName: String?,
        imageSourceType: ImageType?,
        classifyCode: String?,
        categoryCode: String?,
        rdType: ImageRDTypeEnum?,
        labelCode: String?,
        score: Int?,
        sortType: String?,
        page: Int?,
        pageSize: Int?,
        interfaceName: String? = "Anon interface"
    ): Result<MarketImageResp> {
        logger.info("$interfaceName:searchImage:Input:($userId,$imageName,$imageSourceType,$classifyCode,$categoryCode,$labelCode,$score,$sortType,$page,$pageSize)")
        // 获取用户组织架构
        val userDeptList = storeUserService.getUserDeptList(userId)
        logger.info("$interfaceName:searchImage:Inner:userDeptList=$userDeptList")
        val result = MarketImageResp(
            count = count(
                userId = userId,
                userDeptList = userDeptList,
                imageName = imageName,
                classifyCodeList = if (null != classifyCode) listOf(classifyCode) else null,
                categoryCodeList = if (null != categoryCode) listOf(categoryCode) else null,
                rdType = rdType,
                labelCode = labelCode,
                score = score,
                imageSourceType = imageSourceType
            ),
            page = page,
            pageSize = pageSize,
            records = doList(
                userId = userId,
                userDeptList = userDeptList,
                imageName = imageName,
                classifyCodeList = if (null != classifyCode) listOf(classifyCode) else null,
                categoryCodeList = if (null != categoryCode) listOf(categoryCode) else null,
                rdType = rdType,
                labelCode = labelCode,
                score = score,
                imageSourceType = imageSourceType,
                sortType = MarketImageSortTypeEnum.getSortTypeEnum(sortType),
                desc = false,
                page = page,
                pageSize = pageSize,
                interfaceName = interfaceName
            ).map {
                val categories = imageCategoryRelDao.getCategorysByImageId(dslContext, it.id)?.map { categoryRecord ->
                    categoryRecord.get(KEY_CATEGORY_CODE) as String
                } ?: emptyList()
                MarketItem(
                    id = it.id,
                    name = it.name,
                    code = it.code,
                    // 仅用于插件区分Agent/AgentLess
                    type = "",
                    rdType = it.rdType,
                    classifyCode = it.classifyCode,
                    category = categories.joinToString(","),
                    logoUrl = it.logoUrl,
                    publisher = it.publisher ?: "",
                    os = emptyList(),
                    downloads = it.downloads,
                    score = it.score,
                    summary = it.summary,
                    flag = it.flag,
                    publicFlag = it.publicFlag,
                    buildLessRunFlag = false,
                    docsLink = baseImageDocsLink + it.code,
                    recommendFlag = it.recommendFlag
                )
            }
        )
        return Result(result)
    }

    /**
     * 首页镜像列表
     */
    fun mainPageList(
        userId: String,
        page: Int?,
        pageSize: Int?,
        interfaceName: String? = "Anon interface"
    ): Result<List<MarketImageMain>> {
        logger.info("$interfaceName:mainPageList:Input:($userId,$page,$pageSize)")
        val result = mutableListOf<MarketImageMain>()
        // 获取用户组织架构
        val userDeptList = storeUserService.getUserDeptList(userId)
        logger.info("$interfaceName:mainPageList:Inner:userDeptList=$userDeptList")

        result.add(
            MarketImageMain(
                key = "latest",
                label = "最新",
                records = doList(
                    userId = userId,
                    userDeptList = userDeptList,
                    imageName = null,
                    classifyCodeList = null,
                    categoryCodeList = null,
                    rdType = null,
                    labelCode = null,
                    score = null,
                    imageSourceType = null,
                    sortType = MarketImageSortTypeEnum.UPDATE_TIME,
                    desc = true,
                    page = page,
                    pageSize = pageSize,
                    interfaceName = interfaceName
                )
            )
        )
        result.add(
            MarketImageMain(
                key = "hottest",
                label = "最热",
                records = doList(
                    userId = userId,
                    userDeptList = userDeptList,
                    imageName = null,
                    classifyCodeList = null,
                    categoryCodeList = null,
                    rdType = null,
                    labelCode = null,
                    score = null,
                    imageSourceType = null,
                    sortType = MarketImageSortTypeEnum.DOWNLOAD_COUNT,
                    desc = true,
                    page = page,
                    pageSize = pageSize,
                    interfaceName = interfaceName
                )
            )
        )
        val classifyList = classifyDao.getAllClassify(dslContext, StoreTypeEnum.IMAGE.type.toByte())
        classifyList.forEach {
            val classifyCode = it.classifyCode
            if (classifyCode != "trigger") {
                result.add(
                    MarketImageMain(
                        key = classifyCode,
                        label = it.classifyName,
                        records = doList(
                            userId = userId,
                            userDeptList = userDeptList,
                            imageName = null,
                            classifyCodeList = listOf(classifyCode),
                            categoryCodeList = null,
                            rdType = null,
                            labelCode = null,
                            score = null,
                            imageSourceType = null,
                            sortType = MarketImageSortTypeEnum.DOWNLOAD_COUNT,
                            desc = true,
                            page = page,
                            pageSize = pageSize,
                            interfaceName = interfaceName
                        )
                    )
                )
            }
        }
        logger.info("$interfaceName:mainPageList:Output:result.size=${result.size}")
        return Result(result)
    }

    fun getMyImageList(
        userId: String,
        imageName: String?,
        page: Int?,
        pageSize: Int?,
        interfaceName: String? = "Anon interface"
    ): Result<Page<MyImage>> {
        logger.info("$interfaceName:getMyImageList:Input:($userId:$imageName:$page:$pageSize)")
        // 参数校验
        val validPage = PageUtil.getValidPage(page)
        // 默认拉取所有
        val validPageSize = pageSize ?: -1
        val projectCodeList = mutableListOf<String>()
        val myImageCodeList = mutableListOf<String>()
        // 查数据库，弱一致，无需事务
        // 1.查总数
        val count = imageDao.countByUserIdAndName(dslContext, userId, imageName)
        // 2.查分页列表
        val myImageRecords = imageDao.listImageByNameLike(
            dslContext = dslContext,
            userId = userId,
            imageName = imageName,
            page = validPage,
            pageSize = validPageSize
        )
        myImageRecords.forEach {
            val imageCode = it.get(KEY_IMAGE_CODE) as String
            val projectCode = storeProjectRelDao.getInitProjectCodeByStoreCode(
                dslContext = dslContext,
                storeCode = imageCode,
                storeType = StoreTypeEnum.IMAGE.type.toByte()
            )
                ?: throw DataConsistencyException(
                    "storeCode=$imageCode,storeType=${StoreTypeEnum.IMAGE.name}",
                    "T_STORE_PROJECT_REL.projectCode",
                    "Data does not exist"
                )
            myImageCodeList.add(imageCode)
            projectCodeList.add(projectCode)
        }

        // 根据projectCodeList调用一次微服务接口批量获取projectName
        val projectListResult = client.get(ServiceProjectResource::class).listByProjectCodeList(projectCodeList)
        val projectList = projectListResult.data!!
        val projectListIdsStr = StringBuilder()
        projectList.forEach {
            projectListIdsStr.append(it.id)
            projectListIdsStr.append(",")
        }
        logger.info("$interfaceName:getMyImageList:Inner:projectList.size=${projectList.size}:$projectListIdsStr")
        // 封装结果返回
        val myImageList = ArrayList<MyImage>()
        for (i in 0 until myImageRecords.size) {
            val it = myImageRecords[i]
            val imageCode = myImageCodeList[i]
            val projectCode = projectCodeList[i]
            val projectV0 = projectList[i]
            val projectName = projectV0.projectName
            // enable字段为null时默认为true
            val projectEnabled = projectV0.enabled ?: true
            val (imageSizeNum, imageSize) = getImageSizeInfoByStr(it.get(KEY_IMAGE_SIZE) as String)
            myImageList.add(
                MyImage(
                    imageId = it.get(KEY_IMAGE_ID) as String,
                    imageCode = imageCode,
                    imageName = it.get(KEY_IMAGE_NAME) as String,
                    imageSourceType = ImageType.getType((it.get(KEY_IMAGE_SOURCE_TYPE) as String)).name,
                    imageRepoUrl = (it.get(KEY_IMAGE_REPO_URL) as String?) ?: "",
                    imageRepoName = it.get(KEY_IMAGE_REPO_NAME) as String,
                    version = it.get(KEY_IMAGE_VERSION) as String,
                    imageTag = (it.get(KEY_IMAGE_TAG) as String?) ?: "",
                    imageSize = imageSize,
                    imageSizeNum = imageSizeNum,
                    imageStatus = ImageStatusEnum.getImageStatus((it.get(KEY_IMAGE_STATUS) as Byte).toInt()),
                    projectCode = projectCode,
                    projectName = projectName,
                    projectEnabled = projectEnabled,
                    creator = it.get(KEY_CREATOR) as String,
                    modifier = it.get(KEY_MODIFIER) as String,
                    createTime = (it.get(KEY_CREATE_TIME) as LocalDateTime).timestampmilli(),
                    updateTime = (it.get(KEY_UPDATE_TIME) as LocalDateTime).timestampmilli()
                )
            )
        }
        val pageObj = Page(
            count = count.toLong(),
            page = validPage,
            pageSize = validPageSize,
            totalPages = if (validPageSize > 0) ceil(count * 1.0 / validPageSize).toInt() else -1,
            records = myImageList
        )
        logger.info("$interfaceName:getMyImageList:Output:Page($validPage:$validPageSize:$count:myImageList.size=${myImageList.size})")
        return Result(pageObj)
    }

    fun getImageDetailById(
        userId: String,
        imageId: String,
        interfaceName: String? = "Anon interface"
    ): ImageDetail {
        logger.info("$interfaceName:getImageDetailById:Input:($userId,$imageId)")
        val imageRecord =
            imageDao.getImage(dslContext, imageId) ?: throw InvalidParamException(
                "image is null,imageId=$imageId",
                params = arrayOf(imageId)
            )
        return getImageDetail(userId, imageRecord)
    }

    fun getImageRepoInfoByCodeAndVersion(
        userId: String,
        projectCode: String,
        imageCode: String,
        imageVersion: String?,
        interfaceName: String? = "Anon interface"
    ): ImageRepoInfo {
        logger.info("$interfaceName:getImageRepoInfoByCodeAndVersion:Input:($userId,$projectCode,$imageCode,$imageVersion)")
        // 区分是否为调试项目
        val imageStatusList = imageCommonService.generateImageStatusList(imageCode, projectCode)
        val imageRecord =
            imageDao.getLatestImageByBaseVersion(
                dslContext = dslContext,
                imageCode = imageCode,
                imageStatusSet = imageStatusList.toSet(),
                baseVersion = imageVersion?.replace("*", "")
            )
                ?: throw InvalidParamException(
                    message = "image is null,projectCode=$projectCode,imageCode=$imageCode,imageVersion=$imageVersion",
                    params = arrayOf(imageCode, imageVersion ?: "")
                )
        return getImageRepoInfoByRecord(imageRecord)
    }

    fun getImageRepoInfoByRecord(imageRecord: Record): ImageRepoInfo {
        val id = imageRecord.get(KEY_IMAGE_ID) as String
        val sourceType = ImageType.getType(imageRecord.get(KEY_IMAGE_SOURCE_TYPE) as String)
        val repoUrl = imageRecord.get(KEY_IMAGE_REPO_URL) as String? ?: ""
        val repoName = imageRecord.get(KEY_IMAGE_REPO_NAME) as String? ?: ""
        val tag = imageRecord.get(KEY_IMAGE_TAG) as String? ?: ""
        val ticketId = imageRecord.get(Constants.KEY_IMAGE_TICKET_ID) as String? ?: ""
        val ticketProject = imageRecord.get(Constants.KEY_IMAGE_INIT_PROJECT) as String? ?: ""
        var completeImageName = ""
        val cleanImageRepoUrl = repoUrl.trimEnd { ch ->
            ch == '/'
        }
        val cleanImageRepoName = repoName.trimStart { ch ->
            ch == '/'
        }
        if (ImageType.BKDEVOPS == sourceType) {
            // 蓝盾项目源镜像
            completeImageName = cleanImageRepoName
        } else if (ImageType.THIRD == sourceType) {
            // 第三方源镜像
            completeImageName = "$cleanImageRepoUrl/$cleanImageRepoName"
            // dockerhub镜像名称不带斜杠前缀
            if (cleanImageRepoUrl.isBlank()) {
                completeImageName = completeImageName.removePrefix("/")
            }
        } else {
            throw UnknownImageSourceType(
                "imageId=$id,imageSourceType=${sourceType.name}",
                StoreMessageCode.USER_IMAGE_UNKNOWN_SOURCE_TYPE
            )
        }
        completeImageName += if (!tag.isBlank()) {
            ":$tag"
        } else {
            ":latest"
        }
        logger.info("getImageRepoInfoByRecord:Output($completeImageName)")
        return ImageRepoInfo(
            sourceType = sourceType,
            completeImageName = completeImageName,
            ticketId = ticketId,
            ticketProject = ticketProject
        )
    }

    fun getImageDetailByCode(
        userId: String,
        imageCode: String,
        interfaceName: String? = "Anon interface"
    ): ImageDetail {
        logger.info("$interfaceName:getImageDetailByCode:Input:($userId,$imageCode)")
        val imageRecord =
            imageDao.getLatestImageByCode(dslContext, imageCode) ?: throw InvalidParamException(
                message = "image is null,imageCode=$imageCode",
                params = arrayOf(imageCode)
            )
        return getImageDetail(userId, imageRecord)
    }

    /**
     * 镜像大小信息格式化
     */
    private fun getImageSizeInfoByStr(imageSizeStr: String): Pair<Long, String> {
        var imageSizeNum = 0L
        try {
            imageSizeNum = if ("" == imageSizeStr.trim()) {
                0L
            } else {
                imageSizeStr.toLong()
            }
        } catch (e: NumberFormatException) {
            logger.warn("imageSizeStr=$imageSizeStr", e)
        }
        val imageSize = if (0L == imageSizeNum) {
            "-"
        } else {
            String.format("%.2f", imageSizeNum / 1024.0 / 1024.0) + " MB"
        }
        return Pair(imageSizeNum, imageSize)
    }

    private fun getImageDetail(userId: String, imageRecord: TImageRecord): ImageDetail {
        val imageId = imageRecord.id
        val storeStatisticRecord =
            storeStatisticDao.getStatisticByStoreId(
                dslContext = dslContext,
                storeId = imageId,
                storeType = StoreTypeEnum.IMAGE.type.toByte()
            )
        val classifyRecord = classifyDao.getClassify(dslContext, imageRecord.classifyId)
        val imageFeatureRecord = imageFeatureDao.getImageFeature(dslContext, imageRecord.imageCode)
            ?: throw InvalidParamException("imageFeature is null,imageCode=${imageRecord.imageCode}")
        val imageVersionLog = imageVersionLogDao.getLatestImageVersionLogByImageId(dslContext, imageId)?.get(0)
        val imageCode = imageRecord.imageCode
        val publicFlag = imageFeatureRecord.publicFlag
        // 生成icon
        val icon = imageRecord.icon
        // 判断installFlag
        val installFlag =
            storeUserService.isCanInstallStoreComponent(
                defaultFlag = publicFlag,
                userId = userId,
                storeCode = imageCode,
                storeType = StoreTypeEnum.IMAGE
            ) // 是否能安装
        // 判断releaseFlag
        var releaseFlag = false
        val count = marketImageDao.countReleaseImageByCode(dslContext, imageCode)
        if (count > 0) {
            releaseFlag = true
        }
        // 查LabelList
        val labelList = ArrayList<Label>()
        val records = imageLabelRelDao.getLabelsByImageId(dslContext, imageId)
        records?.forEach {
            labelList.add(
                Label(
                    id = it[KEY_LABEL_ID] as String,
                    labelCode = it[KEY_LABEL_CODE] as String,
                    labelName = it[KEY_LABEL_NAME] as String,
                    labelType = LabelTypeEnum.getLabelType((it[KEY_LABEL_TYPE] as Byte).toInt()),
                    createTime = (it[KEY_CREATE_TIME] as LocalDateTime).timestampmilli(),
                    updateTime = (it[KEY_UPDATE_TIME] as LocalDateTime).timestampmilli()
                )
            )
        }
        labelList.sortedBy { it.labelName }
        // 查CategoryList
        val categoryList = ArrayList<Category>()
        val categoryRecords = imageCategoryRelDao.getCategorysByImageId(dslContext, imageId)
        categoryRecords?.forEach {
            categoryList.add(
                Category(
                    id = it[KEY_CATEGORY_ID] as String,
                    categoryCode = it[KEY_CATEGORY_CODE] as String,
                    categoryName = it[KEY_CATEGORY_NAME] as String,
                    categoryType = CategoryTypeEnum.getCategoryType((it[KEY_CATEGORY_TYPE] as Byte).toInt()),
                    iconUrl = (it[KEY_CATEGORY_ICON_URL] as String?) ?: "",
                    createTime = (it[KEY_CREATE_TIME] as LocalDateTime).timestampmilli(),
                    updateTime = (it[KEY_UPDATE_TIME] as LocalDateTime).timestampmilli()
                )
            )
        }
        // 查UserCommentInfo
        val userCommentInfo = storeCommentService.getStoreUserCommentInfo(
            userId = userId,
            storeCode = imageCode,
            storeType = StoreTypeEnum.IMAGE
        )
        // 查关联镜像时的调试项目
        val projectCode =
            storeProjectRelDao.getInitProjectCodeByStoreCode(dslContext, imageCode, StoreTypeEnum.IMAGE.type.toByte())
                ?: throw DataConsistencyException(
                    "imageCode:$imageCode",
                    "projectCode of Table StoreProjectRel",
                    "No initial projectCode"
                )
        val (imageSizeNum, imageSize) = getImageSizeInfoByStr(imageRecord.imageSize as String)
        val agentTypeScope = if (ImageStatusEnum.getInprocessStatusSet().contains(imageRecord.imageStatus.toInt())) {
            // 非终止态镜像应采用当前版本范畴与适用机器类型
            JsonUtil.to(imageRecord.agentTypeScope!!, object : TypeReference<List<ImageAgentTypeEnum>>() {})
        } else {
            // 终止态镜像采用最终适用机器类型
            imageAgentTypeDao.getAgentTypeByImageCode(dslContext, imageCode)?.map {
                ImageAgentTypeEnum.getImageAgentType(it.get(Constants.KEY_IMAGE_AGENT_TYPE) as String)!!
            } ?: emptyList()
        }
        val category = if (categoryList.isNotEmpty()) {
            categoryList[0]
        } else {
            null
        }
        // 组装返回
        return ImageDetail(
            imageId = imageId,
            id = imageId,
            imageCode = imageCode,
            code = imageCode,
            imageName = imageRecord.imageName,
            name = imageRecord.imageName,
            logoUrl = imageRecord.logoUrl ?: "",
            icon = icon ?: "",
            summary = imageRecord.summary ?: "",
            docsLink = baseImageDocsLink + imageCode,
            projectCode = projectCode,
            score = storeStatisticRecord?.value3()?.toDouble() ?: 0.0,
            downloads = storeStatisticRecord?.value1()?.toInt() ?: 0,
            classifyCode = classifyRecord?.classifyCode ?: "",
            classifyName = classifyRecord?.classifyName ?: "",
            imageSourceType = ImageType.getType(imageRecord.imageSourceType).name,
            imageRepoUrl = imageRecord.imageRepoUrl ?: "",
            imageRepoName = imageRecord.imageRepoName ?: "",
            rdType = ImageRDTypeEnum.getImageRDType(imageFeatureRecord.imageType.toInt()),
            agentTypeScope = agentTypeScope,
            ticketId = imageRecord.ticketId ?: "",
            imageTag = imageRecord.imageTag ?: "",
            imageSize = imageSize,
            imageSizeNum = imageSizeNum,
            imageStatus = ImageStatusEnum.getImageStatus(imageRecord.imageStatus.toInt()),
            description = imageRecord.description ?: "",
            labelList = labelList,
            category = category?.categoryCode ?: "",
            categoryName = category?.categoryName ?: "",
            latestFlag = imageRecord.latestFlag,
            publisher = imageRecord.publisher ?: "",
            pubTime = imageRecord.pubTime?.timestampmilli(),
            publicFlag = imageFeatureRecord.publicFlag,
            flag = installFlag,
            releaseFlag = releaseFlag,
            recommendFlag = imageFeatureRecord.recommendFlag,
            certificationFlag = imageFeatureRecord.certificationFlag,
            userCommentInfo = userCommentInfo,
            version = imageRecord.version ?: "",
            releaseType = ReleaseTypeEnum.getReleaseType(imageVersionLog?.releaseType?.toInt() ?: 0),
            versionContent = imageVersionLog?.content ?: "",
            creator = imageVersionLog?.creator,
            modifier = imageVersionLog?.modifier,
            createTime = (imageVersionLog?.createTime ?: imageRecord.createTime).timestampmilli(),
            updateTime = (imageVersionLog?.updateTime ?: imageRecord.updateTime).timestampmilli()
        )
    }

    fun deleteById(
        userId: String,
        imageId: String,
        interfaceName: String? = "Anon interface"
    ): Result<Boolean> {
        val imageRecord = imageDao.getImage(dslContext, imageId) ?: throw ImageNotExistException("imageId=$imageId")
        return delete(userId, imageRecord.imageCode, interfaceName)
    }

    fun delete(
        userId: String,
        imageCode: String,
        interfaceName: String? = "Anon interface"
    ): Result<Boolean> {
        logger.info("$interfaceName:delete:Input:($userId,$imageCode)")
        val type = StoreTypeEnum.IMAGE.type.toByte()
        val isOwner = storeMemberDao.isStoreAdmin(dslContext, userId, imageCode, type)
        if (!isOwner) {
            return MessageCodeUtil.generateResponseDataObject(CommonMessageCode.PERMISSION_DENIED, arrayOf(imageCode))
        }

        val releasedCnt = marketImageDao.countReleaseImageByCode(dslContext, imageCode)
        if (releasedCnt > 0) {
            return MessageCodeUtil.generateResponseDataObject(StoreMessageCode.USER_IMAGE_RELEASED, arrayOf(imageCode))
        }
        logger.info("$interfaceName:delete:Inner:releasedCnt=$releasedCnt")

        // 如果已经被安装到其他项目下使用，不能删除关联
        val installedCnt = storeProjectRelDao.countInstalledProject(dslContext, imageCode, type)
        logger.info("$interfaceName:delete:Inner:installedCnt=$installedCnt")
        if (installedCnt > 0) {
            return MessageCodeUtil.generateResponseDataObject(StoreMessageCode.USER_IMAGE_USED, arrayOf(imageCode))
        }
        deleteImageLogically(userId, imageCode)
        return Result(true)
    }

    /**
     * 软删除，主表置删除态
     */
    fun deleteImageLogically(
        userId: String,
        imageCode: String
    ) {
        dslContext.transaction { t ->
            val context = DSL.using(t)
            marketImageDao.updateImageBaseInfoByCode(context, userId, imageCode, ImageBaseInfoUpdateRequest(deleteFlag = true))
            marketImageFeatureDao.updateImageFeature(context, userId, ImageFeatureUpdateRequest(imageCode = imageCode, deleteFlag = true))
        }
    }

    fun saveImageCategory(
        context: DSLContext,
        userId: String,
        imageId: String,
        categoryCode: String?
    ) {
        if (!categoryCode.isNullOrBlank()) {
            if (categoryDao.countByCode(context, categoryCode!!, StoreTypeEnum.IMAGE.type.toByte()) == 0) {
                throw CategoryNotExistException(
                    message = "category does not exist, categoryCode:$categoryCode",
                    params = arrayOf(categoryCode)
                )
            }
            imageCategoryRelDao.deleteByImageId(context, imageId)
            val categoryId = categoryDao.getCategoryByCodeAndType(
                dslContext = context,
                categoryCode = categoryCode,
                type = StoreTypeEnum.IMAGE.type.toByte()
            )!!.id
            imageCategoryRelDao.batchAdd(context, userId, imageId, listOf(categoryId))
        }
    }

    fun update(
        userId: String,
        imageId: String,
        imageUpdateRequest: ImageUpdateRequest,
        interfaceName: String? = "Anon interface"
    ): Result<Boolean> {
        val imageRecord = imageDao.getImage(dslContext, imageId) ?: throw ImageNotExistException("imageId=$imageId")
        dslContext.transaction { configuration ->
            val context = DSL.using(configuration)
            imageDao.updateImage(
                dslContext = context,
                imageId = imageId,
                imageUpdateBean = ImageDao.ImageUpdateBean(
                    imageName = imageUpdateRequest.imageName,
                    classifyId = imageUpdateRequest.classifyId,
                    version = imageUpdateRequest.version,
                    imageSourceType = imageUpdateRequest.imageSourceType,
                    imageRepoUrl = imageUpdateRequest.imageRepoUrl,
                    imageRepoName = imageUpdateRequest.imageRepoName,
                    imageRepoPath = imageUpdateRequest.imageRepoPath,
                    ticketId = imageUpdateRequest.ticketId,
                    imageStatus = null,
                    imageStatusMsg = null,
                    imageSize = imageUpdateRequest.imageSize,
                    imageTag = imageUpdateRequest.imageTag,
                    agentTypeList = imageUpdateRequest.agentTypeScope,
                    logoUrl = imageUpdateRequest.logoUrl,
                    icon = imageUpdateRequest.icon,
                    summary = imageUpdateRequest.summary,
                    description = imageUpdateRequest.description,
                    publisher = imageUpdateRequest.publisher,
                    // 是否为最新版本镜像只走发布和下架逻辑更新
                    latestFlag = null,
                    modifier = userId
                )
            )
            imageFeatureDao.update(
                dslContext = context,
                imageCode = imageRecord.imageCode,
                publicFlag = imageUpdateRequest.publicFlag,
                recommendFlag = imageUpdateRequest.recommendFlag,
                certificationFlag = imageUpdateRequest.certificationFlag,
                rdType = imageUpdateRequest.rdType,
                weight = imageUpdateRequest.weight,
                modifier = userId
            )
            val categoryCode = imageUpdateRequest.category
            saveImageCategory(context, userId, imageId, categoryCode)
        }
        return Result(true)
    }

    /**
     * 根据项目代码、插件代码和版本号获取插件信息
     */
    @Suppress("UNCHECKED_CAST")
    fun getPipelineImageVersions(projectCode: String, imageCode: String): List<VersionInfo> {
        logger.info("the projectCode is: $projectCode,imageCode is: $imageCode")
        val imageStatusList = imageCommonService.generateImageStatusList(imageCode, projectCode)
        val versionList = mutableListOf<VersionInfo>()
        val versionRecords =
            imageDao.getVersionsByImageCode(dslContext, projectCode, imageCode, imageStatusList) // 查询插件版本信息
        var tmpVersionPrefix = ""
        versionRecords?.forEach {
            // 通用处理
            val imageVersion = it["version"] as String
            val imageTag = it["imageTag"] as String
            val index = imageVersion.indexOf(".")
            val versionPrefix = imageVersion.substring(0, index + 1)
            var versionName = "$imageVersion / tag=$imageTag"
            var latestVersionName = "${versionPrefix}latest / tag=$imageTag"
            val imageStatus = it["imageStatus"] as Byte
            val imageVersionStatusList = listOf(
                ImageStatusEnum.TESTING.status.toByte(),
                ImageStatusEnum.UNDERCARRIAGING.status.toByte(),
                ImageStatusEnum.UNDERCARRIAGED.status.toByte()
            )
            // 特殊情况单独覆盖处理
            if (imageVersionStatusList.contains(imageStatus)) {
                // 处于测试中、下架中、已下架的插件版本的版本名称加下说明
                val imageStatusName = ImageStatusEnum.getImageStatus(imageStatus.toInt())
                val storeImageStatusPrefix = STORE_IMAGE_STATUS + "_"
                val imageStatusMsg = MessageCodeUtil.getCodeLanMessage("$storeImageStatusPrefix$imageStatusName")
                versionName = "$versionName / $imageStatusMsg"
                latestVersionName = "$latestVersionName / $imageStatusMsg"
            }
            if (tmpVersionPrefix != versionPrefix) {
                versionList.add(VersionInfo(latestVersionName, "$versionPrefix*")) // 添加大版本号的通用最新模式（如1.*）
                tmpVersionPrefix = versionPrefix
            }
            versionList.add(VersionInfo(versionName, imageVersion)) // 添加具体的版本号
        }
        logger.info("the imageCode is: $imageCode,versionList is: $versionList")
        return versionList
    }

    fun updateImageBaseInfo(
        userId: String,
        imageCode: String,
        imageBaseInfoUpdateRequest: ImageBaseInfoUpdateRequest,
        interfaceName: String? = "Anon interface"
    ): Result<Boolean> {
        logger.info("$interfaceName:updateImageBaseInfo:Input($userId,$imageCode,$imageBaseInfoUpdateRequest")
        // 判断当前用户是否是该镜像的成员
        if (!storeMemberDao.isStoreMember(dslContext, userId, imageCode, StoreTypeEnum.IMAGE.type.toByte())) {
            return MessageCodeUtil.generateResponseDataObject(CommonMessageCode.PERMISSION_DENIED)
        }
        // 查询镜像的最新记录
        val newestImageRecord = marketImageDao.getNewestImageByCode(dslContext, imageCode)
        logger.info("$interfaceName:updateImageBaseInfo:Inner:newestImageRecord=(${newestImageRecord?.id},${newestImageRecord?.imageName},${newestImageRecord?.version},${newestImageRecord?.imageStatus})")
        if (null == newestImageRecord) {
            return MessageCodeUtil.generateResponseDataObject(
                CommonMessageCode.PARAMETER_IS_INVALID,
                arrayOf(imageCode),
                false
            )
        }
        val imageFinalStatusList = listOf(
            ImageStatusEnum.AUDIT_REJECT.status.toByte(),
            ImageStatusEnum.RELEASED.status.toByte(),
            ImageStatusEnum.GROUNDING_SUSPENSION.status.toByte(),
            ImageStatusEnum.UNDERCARRIAGED.status.toByte()
        )
        // 判断最近一个镜像版本的状态，只有处于审核驳回、已发布、上架中止和已下架的状态才允许修改基本信息
        if (!imageFinalStatusList.contains(newestImageRecord.imageStatus)) {
            return MessageCodeUtil.generateResponseDataObject(
                StoreMessageCode.USER_IMAGE_VERSION_IS_NOT_FINISH,
                arrayOf(newestImageRecord.imageName, newestImageRecord.version)
            )
        }
        val imageIdList = mutableListOf(newestImageRecord.id)
        val latestImageRecord = imageDao.getLatestImageByCode(dslContext, imageCode)
        logger.info("updateImageBaseInfo latestImageRecord is :$latestImageRecord")
        if (null != latestImageRecord) {
            logger.info("$interfaceName:updateImageBaseInfo:Inner:latestImageRecord=(${latestImageRecord.id},${latestImageRecord.imageName},${latestImageRecord.version},${latestImageRecord.imageStatus})")
            imageIdList.add(latestImageRecord.id)
        }
        logger.info("$interfaceName:updateImageBaseInfo:Inner:imageIdList:$imageIdList")
        dslContext.transaction { t ->
            val context = DSL.using(t)
            marketImageDao.updateImageBaseInfo(
                dslContext = context,
                userId = userId,
                imageIdList = imageIdList,
                imageBaseInfoUpdateRequest = imageBaseInfoUpdateRequest
            )
            // 更新标签信息
            val labelIdList = imageBaseInfoUpdateRequest.labelIdList
            if (null != labelIdList) {
                imageIdList.forEach {
                    imageLabelRelDao.deleteByImageId(context, it)
                    if (labelIdList.isNotEmpty())
                        imageLabelRelDao.batchAdd(
                            dslContext = context,
                            userId = userId,
                            imageId = it,
                            labelIdList = labelIdList
                        )
                }
            }
            // 更新范畴信息
            imageIdList.forEach {
                imageCategoryRelDao.updateCategory(
                    dslContext = context,
                    userId = userId,
                    imageId = it,
                    categoryCode = imageBaseInfoUpdateRequest.category
                )
            }
        }
        return Result(true)
    }
}
