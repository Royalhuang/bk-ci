USE devops_ci_process;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for T_BUILD_STARTUP_PARAM
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_BUILD_STARTUP_PARAM` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `BUILD_ID` varchar(64) NOT NULL,
  `PARAM` mediumtext NOT NULL,
  `PROJECT_ID` varchar(64) DEFAULT NULL,
  `PIPELINE_ID` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `BUILD_ID` (`BUILD_ID`),
  KEY `IDX_DEL` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_METADATA
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_METADATA` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` varchar(32) NOT NULL,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `BUILD_ID` varchar(34) NOT NULL,
  `META_DATA_ID` varchar(128) NOT NULL,
  `META_DATA_VALUE` varchar(255) NOT NULL,
  `CREATE_TIME` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ID`),
  KEY `BUILD_ID` (`BUILD_ID`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_CONTAINER
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_CONTAINER` (
   `PROJECT_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '项目ID',
  `PIPELINE_ID` varchar(64) NOT NULL COMMENT '流水线ID',
  `BUILD_ID` varchar(64) NOT NULL COMMENT '构建ID',
  `STAGE_ID` varchar(64) NOT NULL COMMENT '阶段ID',
  `CONTAINER_ID` varchar(64) NOT NULL COMMENT '容器JOB_ID',
  `CONTAINER_TYPE` varchar(45) DEFAULT NULL COMMENT '容器类型',
  `SEQ` int(11) NOT NULL COMMENT '容器顺序',
  `STATUS` int(11) DEFAULT NULL COMMENT '状态',
  `START_TIME` timestamp(3) NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '启动时间',
  `END_TIME` timestamp(3) NULL DEFAULT NULL COMMENT '结束时间',
  `COST` int(11) DEFAULT '0' COMMENT '总耗时，秒',
  `EXECUTE_COUNT` int(11) DEFAULT '1' COMMENT '执行次数',
  `CONDITIONS` text COMMENT '跳过的条件JSON',
  `CREATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`BUILD_ID`,`STAGE_ID`,`CONTAINER_ID`,`CREATE_TIME`),
  KEY `idx_proj_pipeline` (`PROJECT_ID`,`PIPELINE_ID`),
  KEY `IDX_STATUS` (`STATUS`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_DETAIL
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_DETAIL` (
  `BUILD_ID` varchar(34) NOT NULL,
  `BUILD_NUM` int(20) DEFAULT NULL,
  `MODEL` mediumtext,
  `START_USER` varchar(32) DEFAULT NULL,
  `TRIGGER` varchar(32) DEFAULT NULL,
  `START_TIME` datetime DEFAULT NULL,
  `END_TIME` datetime DEFAULT NULL,
  `STATUS` varchar(32) DEFAULT NULL,
  `CANCEL_USER` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`BUILD_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_HISTORY
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_HISTORY` (
  `BUILD_ID` varchar(34) NOT NULL,
  `PARENT_BUILD_ID` varchar(34) DEFAULT NULL,
  `PARENT_TASK_ID` varchar(34) DEFAULT NULL,
  `BUILD_NUM` int(20) DEFAULT '0',
  `PROJECT_ID` varchar(64) NOT NULL,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `VERSION` int(11) DEFAULT NULL,
  `START_USER` varchar(64) DEFAULT NULL,
  `TRIGGER` varchar(32) NOT NULL,
  `START_TIME` timestamp NULL DEFAULT NULL,
  `END_TIME` timestamp NULL DEFAULT NULL,
  `STATUS` int(11) DEFAULT NULL,
  `STAGE_STATUS` text DEFAULT NULL COMMENT '流水线各阶段状态',
  `TASK_COUNT` int(11) DEFAULT NULL,
  `FIRST_TASK_ID` varchar(34) DEFAULT NULL,
  `CHANNEL` varchar(32) DEFAULT NULL,
  `TRIGGER_USER` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `MATERIAL` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  `QUEUE_TIME` timestamp NULL DEFAULT NULL,
  `ARTIFACT_INFO` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  `REMARK` varchar(4096) DEFAULT NULL,
  `EXECUTE_TIME` bigint(20) DEFAULT NULL,
  `BUILD_PARAMETERS` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  `WEBHOOK_TYPE` varchar(64) DEFAULT NULL,
  `RECOMMEND_VERSION` varchar(64) DEFAULT NULL,
  `ERROR_TYPE` int(11) DEFAULT NULL,
  `ERROR_CODE` int(11) DEFAULT NULL COMMENT '错误码',
  `ERROR_MSG` text COMMENT '错误描述',
  `WEBHOOK_INFO` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin,
  `IS_RETRY` BIT(1) DEFAULT b'0',
  `ERROR_INFO` text COMMENT '错误信息',
  `BUILD_MSG` VARCHAR(255) DEFAULT NULL,
  PRIMARY KEY (`BUILD_ID`),
  KEY `STATUS_KEY` (`PROJECT_ID`,`PIPELINE_ID`,`STATUS`),
  KEY `LATEST_BUILD_KEY` (`PIPELINE_ID`,`BUILD_NUM`),
  KEY `inx_tpbh_status` (`STATUS`),
  KEY `inx_tpbh_start_time` (`START_TIME`),
  KEY `inx_tpbh_end_time` (`END_TIME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_STAGE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_STAGE` (
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `PROJECT_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '项目ID',
  `BUILD_ID` varchar(64) NOT NULL COMMENT '构建ID',
  `STAGE_ID` varchar(64) NOT NULL COMMENT '阶段ID',
  `SEQ` int(11) NOT NULL COMMENT 'Stage顺序',
  `STATUS` int(11) DEFAULT NULL COMMENT '状态',
  `START_TIME` timestamp(3) NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '开始时间',
  `END_TIME` timestamp(3) NULL DEFAULT NULL COMMENT '结束时间',
  `COST` int(11) DEFAULT '0' COMMENT '总耗时：秒',
  `EXECUTE_COUNT` int(11) DEFAULT '1' COMMENT '执行次数',
  `CONDITIONS` text COMMENT '跳过的条件JSON',
  `CREATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`BUILD_ID`,`STAGE_ID`,`CREATE_TIME`),
  KEY `idx_proj_pipeline` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_SUMMARY
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_SUMMARY` (
  `PIPELINE_ID` varchar(34) NOT NULL,
  `PROJECT_ID` varchar(64) NOT NULL,
  `BUILD_NUM` int(11) DEFAULT '0',
  `BUILD_NO` int(11) DEFAULT '0',
  `FINISH_COUNT` int(11) DEFAULT '0',
  `RUNNING_COUNT` int(11) DEFAULT '0',
  `QUEUE_COUNT` int(11) DEFAULT '0',
  `LATEST_BUILD_ID` varchar(34) DEFAULT NULL,
  `LATEST_TASK_ID` varchar(34) DEFAULT NULL,
  `LATEST_START_USER` varchar(64) DEFAULT NULL,
  `LATEST_START_TIME` timestamp NULL DEFAULT NULL,
  `LATEST_END_TIME` timestamp NULL DEFAULT NULL,
  `LATEST_TASK_COUNT` int(11) DEFAULT NULL,
  `LATEST_TASK_NAME` varchar(128) DEFAULT NULL,
  `LATEST_STATUS` int(11) DEFAULT NULL,
  PRIMARY KEY (`PIPELINE_ID`),
  KEY `PRJOECT_ID` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_TASK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_TASK` (
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `PROJECT_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '项目ID',
  `BUILD_ID` varchar(34) NOT NULL COMMENT '构建ID',
  `STAGE_ID` varchar(34) NOT NULL COMMENT '阶段ID',
  `CONTAINER_ID` varchar(34) NOT NULL COMMENT '任务阶段节点id  （C-32位UUID）=34位',
  `TASK_NAME` varchar(128) DEFAULT NULL,
  `TASK_ID` varchar(34) NOT NULL COMMENT '任务节点id （T-32位UUID）=34位',
  `TASK_PARAMS` text /*!99104 COMPRESSED */ COMMENT '任务参数',
  `TASK_TYPE` varchar(64) NOT NULL COMMENT '任务类型',
  `TASK_ATOM` varchar(128) DEFAULT NULL COMMENT '原子任务，为空表示是构建机任务',
  `START_TIME` timestamp(3) NULL DEFAULT NULL COMMENT '开始时间',
  `END_TIME` timestamp(3) NULL DEFAULT NULL COMMENT '结束时间',
  `STARTER` varchar(64) NOT NULL COMMENT '操作人',
  `APPROVER` varchar(64) DEFAULT NULL COMMENT '审批人',
  `STATUS` int(11) DEFAULT NULL COMMENT '状态',
  `EXECUTE_COUNT` int(11) DEFAULT '0' COMMENT '执行次数，一般一次，如果有重试，则计数+1',
  `TASK_SEQ` int(11) DEFAULT '1' COMMENT '任务在Container中的执行顺序号，从1开始递增',
  `SUB_BUILD_ID` varchar(34) DEFAULT NULL COMMENT '关联的子流水线构建id，一般没有关联则为空',
  `CONTAINER_TYPE` varchar(45) DEFAULT NULL COMMENT '任务的构建容器类型:VMBuildContainer/NormalContainer',
  `ADDITIONAL_OPTIONS` text /*!99104 COMPRESSED */ COMMENT '高级选项',
  `TOTAL_TIME` bigint(20) DEFAULT NULL COMMENT '执行耗费时间',
  `ERROR_TYPE` int(11) DEFAULT NULL COMMENT '错误类型的标识',
  `ERROR_CODE` int(11) DEFAULT NULL COMMENT '错误的标识码',
  `ERROR_MSG` text /*!99104 COMPRESSED */ COMMENT '错误描述',
  `CONTAINER_HASH_ID` varchar(64) DEFAULT NULL COMMENT '容器的HASH ID， 与JOB hash id等价',
  `CREATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `ATOM_CODE` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`BUILD_ID`,`TASK_ID`,`CREATE_TIME`),
  KEY `STAT_PROJECT_RUN` (`PROJECT_ID`,`STATUS`),
  KEY `PROJECT_PIPELINE` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_VAR
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_VAR` (
  `BUILD_ID` varchar(34) NOT NULL COMMENT '构建ID',
  `KEY` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '变量名',
  `VALUE` varchar(4000) DEFAULT NULL COMMENT '变量值限制4000字符',
  `PROJECT_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '项目ID',
  `PIPELINE_ID` varchar(64) DEFAULT NULL COMMENT '流水线ID',
  `VAR_TYPE` varchar(64) DEFAULT NULL COMMENT '变量类型',
  `CREATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`BUILD_ID`,`KEY`,`CREATE_TIME`),
  KEY `IDX_SEARCH_BUILD_ID` (`PROJECT_ID`,`PIPELINE_ID`,`KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `T_PIPELINE_CONTAINER_DISPATCH` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `CONTAINER_ID` varchar(34) NOT NULL,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `PIPELINE_VERSION` int(11) NOT NULL,
  `PROJECT_ID` varchar(64) NOT NULL,
  `UPDATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DISPATCH_BUILD_TYPE` varchar(40) NOT NULL,
  `DISPATCH_VALUE` varchar(254) NOT NULL,
  `DISPATCH_IMAGE_TYPE` varchar(32) DEFAULT NULL,
  `DISPATCH_CREDENTIAL_ID` varchar(34) DEFAULT NULL,
  `DISPATCH_WORKSPACE` varchar(254) DEFAULT NULL,
  `DISPATCH_AGENT_TYPE` varchar(32) DEFAULT NULL,
  `DISPATCH_SYSTEM_VERSION` varchar(255) DEFAULT NULL,
  `DISPATCH_XCODE_VERSION` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `PIPELINE_ID_PIPELINE_VERSION` (`PIPELINE_ID`,`PIPELINE_VERSION`),
  KEY `DISPATCH_BUILD_TYPE_DISPATCH_VALUE` (`DISPATCH_BUILD_TYPE`,`DISPATCH_VALUE`),
  KEY `DISPATCH_VALUE` (`DISPATCH_VALUE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_CONTAINER_MONITOR
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_CONTAINER_MONITOR` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `OS_TYPE` varchar(32) NOT NULL,
  `BUILD_TYPE` varchar(32) NOT NULL,
  `MAX_STARTUP_TIME` bigint(20) NOT NULL,
  `MAX_EXECUTE_TIME` bigint(20) NOT NULL,
  `USERS` varchar(1024) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `OS_TYPE` (`OS_TYPE`,`BUILD_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_FAILURE_BUILD
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_FAILURE_BUILD` (
  `BUILD_ID` varchar(64) NOT NULL,
  `PROJECT_ID` varchar(32) NOT NULL,
  `PIPELINE_ID` varchar(64) NOT NULL,
  `START_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `END_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `BUILD_NUM` int(20) NOT NULL DEFAULT '0',
  PRIMARY KEY (`BUILD_ID`),
  KEY `PIPELINE_ID` (`PIPELINE_ID`),
  KEY `END_TIME` (`END_TIME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_FAILURE_NOTIFY_USER
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_FAILURE_NOTIFY_USER` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `USER_ID` varchar(32) DEFAULT '',
  `NOTIFY_TYPES` varchar(32) DEFAULT '',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `USER_ID` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_FAVOR
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_FAVOR` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` varchar(64) NOT NULL,
  `PIPELINE_ID` varchar(64) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `PIPELINE_ID` (`PIPELINE_ID`,`CREATE_USER`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_GIT_CHECK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_GIT_CHECK` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PIPELINE_ID` varchar(64) NOT NULL,
  `BUILD_NUMBER` int(11) NOT NULL,
  `REPO_ID` varchar(64) DEFAULT NULL,
  `COMMIT_ID` varchar(64) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  `REPO_NAME` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `PIPELINE_ID_REPO_ID_COMMIT_ID` (`PIPELINE_ID`,`COMMIT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_GITHUB_CHECK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_GITHUB_CHECK` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PIPELINE_ID` varchar(64) NOT NULL,
  `BUILD_NUMBER` int(11) NOT NULL,
  `REPO_ID` varchar(64) DEFAULT NULL,
  `COMMIT_ID` varchar(64) NOT NULL,
  `CHECK_RUN_ID` int(11) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  `REPO_NAME` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `PIPELINE_ID_REPO_ID_COMMIT_ID` (`PIPELINE_ID`,`COMMIT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_GROUP
-- ----------------------------
CREATE TABLE IF NOT EXISTS `T_PIPELINE_GROUP` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` varchar(32) NOT NULL,
  `NAME` varchar(64) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL DEFAULT '',
  `UPDATE_USER` varchar(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `PROJECT_ID` (`PROJECT_ID`,`NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_INFO
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_INFO` (
  `PIPELINE_ID` varchar(34) NOT NULL DEFAULT '',
  `PROJECT_ID` varchar(64) NOT NULL,
  `PIPELINE_NAME` varchar(255) NOT NULL,
  `PIPELINE_DESC` varchar(255) DEFAULT NULL,
  `VERSION` int(11) DEFAULT '1',
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `CREATOR` varchar(64) NOT NULL,
  `UPDATE_TIME` timestamp NULL DEFAULT NULL,
  `LAST_MODIFY_USER` varchar(64) NOT NULL,
  `CHANNEL` varchar(32) DEFAULT NULL,
  `MANUAL_STARTUP` int(11) DEFAULT '1',
  `ELEMENT_SKIP` int(11) DEFAULT '0',
  `TASK_COUNT` int(11) DEFAULT '0',
  `DELETE` bit(1) DEFAULT b'0',
  PRIMARY KEY (`PIPELINE_ID`),
  UNIQUE KEY `T_PIPELINE_INFO_NAME_uindex` (`PROJECT_ID`,`PIPELINE_NAME`),
  KEY `PROJECT_ID` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_JOB_MUTEX_GROUP
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_JOB_MUTEX_GROUP` (
  `PROJECT_ID` varchar(64) NOT NULL,
  `JOB_MUTEX_GROUP_NAME` varchar(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`PROJECT_ID`,`JOB_MUTEX_GROUP_NAME`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_LABEL
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_LABEL` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `GROUP_ID` bigint(20) NOT NULL,
  `NAME` varchar(64) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL DEFAULT '',
  `UPDATE_USER` varchar(64) NOT NULL DEFAULT '',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `GROUP_ID` (`GROUP_ID`,`NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_LABEL_PIPELINE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_LABEL_PIPELINE` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `LABEL_ID` bigint(20) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `PIPELINE_ID` (`PIPELINE_ID`,`LABEL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_MODEL_TASK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_MODEL_TASK` (
  `PIPELINE_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `PROJECT_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `STAGE_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `CONTAINER_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `TASK_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `TASK_NAME` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `CLASS_TYPE` varchar(64) COLLATE utf8mb4_bin NOT NULL,
  `TASK_ATOM` varchar(128) COLLATE utf8mb4_bin DEFAULT NULL,
  `TASK_SEQ` int(11) DEFAULT '1',
  `TASK_PARAMS` mediumtext COLLATE utf8mb4_bin,
  `OS` varchar(45) COLLATE utf8mb4_bin DEFAULT NULL,
  `ADDITIONAL_OPTIONS` mediumtext CHARACTER SET utf8mb4,
  `ATOM_CODE` varchar(32) COLLATE utf8mb4_bin NOT NULL DEFAULT '',
  PRIMARY KEY (`PIPELINE_ID`,`PROJECT_ID`,`STAGE_ID`,`CONTAINER_ID`,`TASK_ID`),
  KEY `STAT_PROJECT_RUN` (`PROJECT_ID`),
  KEY `PROJECT_PIPELINE` (`PROJECT_ID`,`PIPELINE_ID`),
  KEY `ATOM_CODE` (`ATOM_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for T_PIPELINE_MUTEX_GROUP
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_MUTEX_GROUP` (
  `PROJECT_ID` varchar(64) NOT NULL,
  `GROUP_NAME` varchar(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  PRIMARY KEY (`PROJECT_ID`,`GROUP_NAME`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_REMOTE_AUTH
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_REMOTE_AUTH` (
  `PIPELINE_ID` varchar(34) NOT NULL,
  `PIPELINE_AUTH` varchar(32) NOT NULL,
  `PROJECT_ID` varchar(32) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL,
  PRIMARY KEY (`PIPELINE_ID`),
  UNIQUE KEY `PIPELINE_AUTH` (`PIPELINE_AUTH`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_RESOURCE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_RESOURCE` (
  `PIPELINE_ID` varchar(34) NOT NULL,
  `VERSION` int(11) NOT NULL DEFAULT '1',
  `MODEL` mediumtext,
  `CREATOR` varchar(64) DEFAULT NULL,
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`PIPELINE_ID`,`VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_SETTING
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_SETTING` (
  `PIPELINE_ID` varchar(34) NOT NULL,
  `DESC` varchar(1024) DEFAULT NULL,
  `RUN_TYPE` int(11) DEFAULT NULL,
  `NAME` varchar(255) DEFAULT NULL,
  `SUCCESS_RECEIVER` mediumtext,
  `FAIL_RECEIVER` mediumtext,
  `SUCCESS_GROUP` mediumtext,
  `FAIL_GROUP` mediumtext,
  `SUCCESS_TYPE` varchar(32) DEFAULT NULL,
  `FAIL_TYPE` varchar(32) DEFAULT NULL,
  `PROJECT_ID` varchar(64) DEFAULT NULL,
  `SUCCESS_WECHAT_GROUP_FLAG` bit(1) NOT NULL DEFAULT b'0',
  `SUCCESS_WECHAT_GROUP` varchar(1024) NOT NULL DEFAULT '',
  `FAIL_WECHAT_GROUP_FLAG` bit(1) NOT NULL DEFAULT b'0',
  `FAIL_WECHAT_GROUP` varchar(1024) NOT NULL DEFAULT '',
  `RUN_LOCK_TYPE` int(11) DEFAULT '1',
  `SUCCESS_DETAIL_FLAG` bit(1) DEFAULT b'0',
  `FAIL_DETAIL_FLAG` bit(1) DEFAULT b'0',
  `SUCCESS_CONTENT` longtext,
  `FAIL_CONTENT` longtext,
  `WAIT_QUEUE_TIME_SECOND` int(11) DEFAULT '7200',
  `MAX_QUEUE_SIZE` int(11) DEFAULT '10',
  `IS_TEMPLATE` bit(1) DEFAULT b'0',
  `SUCCESS_WECHAT_GROUP_MARKDOWN_FLAG` bit(1) NOT NULL DEFAULT b'0',
  `FAIL_WECHAT_GROUP_MARKDOWN_FLAG` bit(1) NOT NULL DEFAULT b'0',
  `MAX_PIPELINE_RES_NUM` int(11) DEFAULT '50',
  `MAX_CON_RUNNING_QUEUE_SIZE` int(11) DEFAULT '50',
  PRIMARY KEY (`PIPELINE_ID`),
  UNIQUE KEY `PROJECT_ID` (`PROJECT_ID`,`NAME`,`IS_TEMPLATE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_TEMPLATE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_TEMPLATE` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `TYPE` varchar(32) NOT NULL DEFAULT 'FREEDOM',
  `CATEGORY` varchar(128) DEFAULT NULL,
  `TEMPLATE_NAME` varchar(64) NOT NULL,
  `ICON` varchar(32) NOT NULL DEFAULT '',
  `LOGO_URL` varchar(512) DEFAULT NULL,
  `PROJECT_CODE` varchar(32) DEFAULT NULL,
  `SRC_TEMPLATE_ID` varchar(32) DEFAULT NULL,
  `AUTHOR` varchar(64) NOT NULL DEFAULT '',
  `ATOMNUM` int(11) NOT NULL,
  `PUBLIC_FLAG` bit(1) NOT NULL DEFAULT b'0',
  `TEMPLATE` mediumtext,
  `CREATOR` varchar(32) NOT NULL,
  `CREATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ID`),
  KEY `SRC_TEMPLATE_ID` (`SRC_TEMPLATE_ID`),
  KEY `PROJECT_CODE` (`PROJECT_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_TIMER
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_TIMER` (
  `PROJECT_ID` varchar(32) NOT NULL,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `CRONTAB` varchar(2048) NOT NULL,
  `CREATOR` varchar(64) NOT NULL,
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `CHANNEL` varchar(32) NOT NULL DEFAULT 'CODECC',
  PRIMARY KEY (`PROJECT_ID`,`PIPELINE_ID`),
  UNIQUE KEY `IDX_PIPELINE_ID` (`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_TRANSFER_HISTORY
-- ----------------------------
CREATE TABLE IF NOT EXISTS `T_PIPELINE_TRANSFER_HISTORY` (
  `PROJECT_ID` varchar(64) NOT NULL,
  `USER_ID` varchar(128) NOT NULL,
  `PIPELINE_ID` varchar(64) NOT NULL,
  `SOURCE_VERSION` int(11) NOT NULL,
  `TARGET_VERSION` int(11) DEFAULT '0',
  `LOG` varchar(128) DEFAULT NULL,
  `UPDATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_USER
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_USER` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL,
  `UPDATE_USER` varchar(64) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `PIPELINE_ID` (`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_VERSION
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_VERSION` (
  `PIPELINE_ID` varchar(32) NOT NULL,
  `VERSION` int(11) NOT NULL,
  PRIMARY KEY (`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_VIEW
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_VIEW` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` varchar(32) NOT NULL,
  `NAME` varchar(64) NOT NULL,
  `FILTER_BY_PIPEINE_NAME` varchar(128) DEFAULT '',
  `FILTER_BY_CREATOR` varchar(64) DEFAULT '',
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL,
  `IS_PROJECT` bit(1) DEFAULT b'0',
  `LOGIC` varchar(32) DEFAULT 'AND',
  `FILTERS` mediumtext,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `PROJECT_NAME` (`PROJECT_ID`,`NAME`,`CREATE_USER`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_VIEW_LABEL
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_VIEW_LABEL` (
  `VIEW_ID` bigint(20) NOT NULL,
  `LABEL_ID` bigint(20) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  PRIMARY KEY (`VIEW_ID`,`LABEL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_VIEW_PROJECT
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_VIEW_PROJECT` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `VIEW_ID` bigint(20) NOT NULL,
  `PROJECT_ID` varchar(32) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL,
  `CREATE_USER` varchar(64) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `PROJECT_ID` (`PROJECT_ID`,`CREATE_USER`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_VIEW_USER_LAST_VIEW
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_VIEW_USER_LAST_VIEW` (
  `USER_ID` varchar(32) NOT NULL,
  `PROJECT_ID` varchar(32) NOT NULL,
  `VIEW_ID` varchar(64) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`USER_ID`,`PROJECT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_VIEW_USER_SETTINGS
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_VIEW_USER_SETTINGS` (
  `USER_ID` varchar(255) NOT NULL,
  `PROJECT_ID` varchar(32) NOT NULL,
  `SETTINGS` mediumtext NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`USER_ID`,`PROJECT_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_WEBHOOK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_WEBHOOK` (
  `REPOSITORY_TYPE` varchar(64) NOT NULL,
  `PROJECT_ID` varchar(32) NOT NULL,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `REPO_HASH_ID` varchar(45) DEFAULT NULL,
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `REPO_NAME` varchar(128) DEFAULT NULL,
  `REPO_TYPE` varchar(32) DEFAULT NULL,
  `PROJECT_NAME` VARCHAR(128) DEFAULT NULL,
  `TASK_ID` VARCHAR(34) DEFAULT NULL,
  `DELETE` BIT(1) DEFAULT 0,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UNIQ_PIPELINE_ID_TASK_ID` (`PIPELINE_ID`, `TASK_ID`),
  KEY `IDX_PROJECT_NAME_REPOSITORY_TYPE` (`PROJECT_NAME`, `REPOSITORY_TYPE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_REPORT
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_REPORT` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` varchar(32) NOT NULL,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `BUILD_ID` varchar(34) NOT NULL,
  `ELEMENT_ID` varchar(34) NOT NULL,
  `INDEX_FILE` text NOT NULL,
  `NAME` text NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `UPDATE_TIME` datetime NOT NULL ON UPDATE CURRENT_TIMESTAMP,
  `TYPE` varchar(32) NOT NULL DEFAULT 'INTERNAL' COMMENT '类型，INTERNAL 平台内置报告，THIRDPARTY 第三方报告',
  PRIMARY KEY (`ID`),
  KEY `PROJECT_PIPELINE_BUILD_IDX` (`PROJECT_ID`,`PIPELINE_ID`,`BUILD_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_TEMPLATE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_TEMPLATE` (
  `VERSION` bigint(20) NOT NULL AUTO_INCREMENT,
  `ID` varchar(32) NOT NULL,
  `TEMPLATE_NAME` varchar(64) NOT NULL,
  `PROJECT_ID` varchar(34) NOT NULL,
  `VERSION_NAME` varchar(64) NOT NULL,
  `CREATOR` varchar(64) NOT NULL,
  `CREATED_TIME` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `TEMPLATE` mediumtext,
  `TYPE` varchar(32) NOT NULL DEFAULT 'CUSTOMIZE',
  `CATEGORY` varchar(128) DEFAULT NULL,
  `LOGO_URL` varchar(512) DEFAULT NULL,
  `SRC_TEMPLATE_ID` varchar(32) DEFAULT NULL,
  `STORE_FLAG` bit(1) DEFAULT b'0',
  `WEIGHT` int(11) DEFAULT '0',
  PRIMARY KEY (`VERSION`),
  KEY `PROJECT_ID` (`PROJECT_ID`),
  KEY `SRC_TEMPLATE_ID` (`SRC_TEMPLATE_ID`),
  KEY `TYPE` (`TYPE`),
  KEY `ID` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_TEMPLATE_PIPELINE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_TEMPLATE_PIPELINE` (
  `PIPELINE_ID` varchar(34) NOT NULL,
  `INSTANCE_TYPE` varchar(32) NOT NULL DEFAULT 'CONSTRAINT' COMMENT '实例化类型：FREEDOM 自由模式  CONSTRAINT 约束模式',
  `ROOT_TEMPLATE_ID` varchar(32) DEFAULT NULL COMMENT '源模板ID',
  `VERSION` bigint(20) NOT NULL,
  `VERSION_NAME` varchar(64) NOT NULL,
  `TEMPLATE_ID` varchar(32) NOT NULL,
  `CREATOR` varchar(64) NOT NULL,
  `UPDATOR` varchar(64) NOT NULL,
  `CREATED_TIME` datetime NOT NULL,
  `UPDATED_TIME` datetime NOT NULL,
  `BUILD_NO` text COMMENT '推荐版本号',
  `PARAM` mediumtext COMMENT '全局参数',
  PRIMARY KEY (`PIPELINE_ID`),
  KEY `TEMPLATE_ID` (`TEMPLATE_ID`),
  KEY `ROOT_TEMPLATE_ID` (`ROOT_TEMPLATE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_TEMPLATE_TRANSFER_HISTORY
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_TEMPLATE_TRANSFER_HISTORY` (
  `PROJECT_ID` varchar(64) NOT NULL,
  `USER_ID` varchar(128) NOT NULL,
  `TEMPLATE_ID` varchar(64) NOT NULL,
  `SOURCE_VERSION` bigint(11) NOT NULL,
  `TARGET_VERSION` bigint(11) DEFAULT '0',
  `LOG` varchar(128) DEFAULT NULL,
  `UPDATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`PROJECT_ID`,`TEMPLATE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
-- ----------------------------
-- Table structure for T_PROJECT_PIPELINE_CALLBACK
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PROJECT_PIPELINE_CALLBACK` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` varchar(64) NOT NULL,
  `EVENTS` varchar(255) DEFAULT NULL,
  `CALLBACK_URL` varchar(255) NOT NULL,
  `CREATOR` varchar(64) NOT NULL,
  `UPDATOR` varchar(64) NOT NULL,
  `CREATED_TIME` datetime NOT NULL,
  `UPDATED_TIME` datetime NOT NULL,
  `SECRET_TOKEN` text DEFAULT NULL COMMENT 'Send to your with http header: X-DEVOPS-WEBHOOK-TOKEN',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `IDX_PROJECT_CALLBACK_EVENTS` (`PROJECT_ID`, `CALLBACK_URL`, `EVENTS`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for T_PIPELINE_STAGE_TAG
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_STAGE_TAG` (
  `ID` varchar(32) NOT NULL DEFAULT '' COMMENT '主键',
  `STAGE_TAG_NAME` varchar(45) NOT NULL DEFAULT '' COMMENT '阶段标签名称',
  `WEIGHT` int(11) NOT NULL DEFAULT '0' COMMENT '阶段标签权值',
  `CREATOR` varchar(50) NOT NULL DEFAULT 'system' COMMENT '创建人',
  `MODIFIER` varchar(50) NOT NULL DEFAULT 'system' COMMENT '最近修改人',
  `CREATE_TIME` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_TIME` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_SUBSCRIPTION
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_SUBSCRIPTION` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PIPELINE_ID` varchar(64) NOT NULL,
  `USERNAME` varchar(32) NOT NULL,
  `SUBSCRIPTION_TYPE` varchar(32) NOT NULL,
  `TYPE` int(11) NOT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `USERNAME-PIPELINE_ID` (`USERNAME`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_HIS_DATA_CLEAR
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_HIS_DATA_CLEAR` (
  `BUILD_ID` varchar(34) NOT NULL,
  `PIPELINE_ID` varchar(34) NOT NULL,
  `PROJECT_ID` varchar(64) NOT NULL,
  `DEL_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`BUILD_ID`),
  KEY `INX_PROJECT_PIPELINE` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `T_PIPELINE_PAUSE_VALUE` (
  `BUILD_ID` varchar(34) NOT NULL COMMENT '构建ID',
  `TASK_ID` varchar(34) NOT NULL COMMENT '任务ID',
  `DEFAULT_VALUE` text COMMENT '默认变量',
  `NEW_VALUE` text COMMENT '暂停后用户提供的变量',
  `CREATE_TIME` timestamp NULL DEFAULT NULL COMMENT '添加时间',
  PRIMARY KEY (`BUILD_ID`,`TASK_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `T_PROJECT_PIPELINE_CALLBACK_HISTORY` (
  `ID` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `PROJECT_ID` VARCHAR(64) NOT NULL,
  `EVENTS` VARCHAR(255) DEFAULT NULL,
  `CALLBACK_URL` VARCHAR(255) NOT NULL,
  `STATUS` VARCHAR(20) NOT NULL,
  `ERROR_MSG` TEXT DEFAULT NULL,
  `REQUEST_HEADER` TEXT DEFAULT NULL,
  `REQUEST_BODY` TEXT NOT NULL,
  `RESPONSE_CODE` INT(10) DEFAULT NULL,
  `RESPONSE_BODY` TEXT DEFAULT NULL,
  `START_TIME` DATETIME NOT NULL,
  `END_TIME` DATETIME NOT NULL,
  `CREATED_TIME` DATETIME NOT NULL,
  PRIMARY KEY `pk_t_project_pipeline_callback_history` (`ID`, `CREATED_TIME`),
  KEY `idx_project_id_callback_url_events`(`PROJECT_ID`, `CALLBACK_URL`, `EVENTS`)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `T_TEMPLATE_INSTANCE_BASE` (
  `ID` varchar(32) NOT NULL DEFAULT '' COMMENT '主键ID',
  `TEMPLATE_VERSION` varchar(32) NOT NULL DEFAULT '' COMMENT '模板版本',
  `USE_TEMPLATE_SETTINGS_FLAG` bit(1)  NOT NULL COMMENT '是否使用模板配置',
  `PROJECT_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '项目ID',
  `TOTAL_ITEM_NUM` int(11) NOT NULL DEFAULT '0' COMMENT '总实例化数量',
  `SUCCESS_ITEM_NUM` int(11) NOT NULL DEFAULT '0' COMMENT '实例化成功数量',
  `FAIL_ITEM_NUM` int(11) NOT NULL DEFAULT '0' COMMENT '实例化失败数量',
  `STATUS` varchar(32) NOT NULL DEFAULT '' COMMENT '状态',
  `CREATOR` varchar(50) NOT NULL DEFAULT 'system' COMMENT '创建者',
  `MODIFIER` varchar(50) NOT NULL DEFAULT 'system' COMMENT '修改者',
  `UPDATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `CREATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  KEY `INX_TTIB_PROJECT_ID` (`PROJECT_ID`),
  KEY `INX_TTIB_STATUS` (`STATUS`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板实列化基本信息表';

CREATE TABLE IF NOT EXISTS `T_TEMPLATE_INSTANCE_ITEM` (
  `ID` varchar(32) NOT NULL DEFAULT '' COMMENT '主键ID',
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `PIPELINE_NAME` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin  NOT NULL COMMENT '流水线名称',
  `BUILD_NO_INFO` varchar(512) COMMENT '构建号信息',
  `STATUS` varchar(32) NOT NULL DEFAULT '' COMMENT '状态',
  `BASE_ID` varchar(32) NOT NULL DEFAULT '' COMMENT '实列化基本信息ID',
  `PARAM` mediumtext COMMENT '参数',
  `CREATOR` varchar(50) NOT NULL DEFAULT 'system' COMMENT '创建者',
  `MODIFIER` varchar(50) NOT NULL DEFAULT 'system' COMMENT '修改者',
  `UPDATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '修改时间',
  `CREATE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UNI_INX_TTI_PIPELINE_ID` (`PIPELINE_ID`),
  KEY `INX_TTI_BASE_ID` (BASE_ID),
  KEY `INX_TTI_STATUS` (`STATUS`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板实列化项信息表';

CREATE TABLE IF NOT EXISTS `T_PIPELINE_WEBHOOK_QUEUE` (
  `ID` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `PIPELINE_ID` VARCHAR(64) NOT NULL,
  `SOURCE_PROJECT_ID` BIGINT(20) NOT NULL,
  `SOURCE_REPO_NAME` VARCHAR(255) NOT NULL,
  `SOURCE_BRANCH` VARCHAR(255) NOT NULL,
  `TARGET_PROJECT_ID` BIGINT(20),
  `TARGET_REPO_NAME` VARCHAR(255),
  `TARGET_BRANCH` VARCHAR(255),
  `BUILD_ID` VARCHAR(34) NOT NULL,
  `CREATE_TIME` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UNIQ_BUILD_ID`(`BUILD_ID`),
  KEY `IDX_PIPELINE_ID_PROJECT_ID_BRANCH`(`PIPELINE_ID`, `SOURCE_PROJECT_ID`,`SOURCE_BRANCH`)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
