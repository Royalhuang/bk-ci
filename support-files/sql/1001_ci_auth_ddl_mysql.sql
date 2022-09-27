USE devops_ci_auth;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for T_LOG_INDICES
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_AUTH_GROUP_INFO` (
  `ID` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主健ID',
  `GROUP_NAME` VARCHAR(32) NOT NULL DEFAULT '""' COMMENT '用户组名称',
  `GROUP_CODE` VARCHAR(32) NOT NULL COMMENT '用户组标识 默认用户组标识一致',
  `GROUP_TYPE` BIT(1) NOT NULL COMMENT '用户组类型 0默认分组',
  `PROJECT_CODE` VARCHAR(64) NOT NULL DEFAULT '""' COMMENT '用户组所属项目',
  `IS_DELETE` BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除 0 可用 1删除',
  `CREATE_USER` VARCHAR(64) NOT NULL DEFAULT '""' COMMENT '添加人',
  `UPDATE_USER` VARCHAR(64) DEFAULT NULL COMMENT '修改人',
  `CREATE_TIME` DATETIME(3) NOT NULL COMMENT '创建时间',
  `UPDATE_TIME` DATETIME(3) DEFAULT NULL COMMENT '修改时间',
  `DISPLAY_NAME` VARCHAR(32) DEFAULT NULL COMMENT '用户组别名',
  `RELATION_ID` VARCHAR(32) DEFAULT NULL COMMENT '关联系统ID',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `GROUP_NAME+PROJECT_CODE` (`GROUP_NAME`,`PROJECT_CODE`),
  KEY `PROJECT_CODE` (`PROJECT_CODE`)
) ENGINE=INNODB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='用户组信息表';

CREATE TABLE IF NOT EXISTS `T_AUTH_GROUP_PERSSION` (
  `ID` varchar(64) NOT NULL COMMENT '主健ID',
  `AUTH_ACTION` varchar(64) NOT NULL DEFAULT '""' COMMENT '权限动作',
  `GROUP_CODE` varchar(64) NOT NULL DEFAULT '""' COMMENT '用户组编号 默认7个内置组编号固定 自定义组编码随机',
  `CREATE_USER` varchar(64) NOT NULL DEFAULT '""' COMMENT '创建人',
  `UPDATE_USER` varchar(64) DEFAULT NULL COMMENT '修改人',
  `CREATE_TIME` datetime(3) NOT NULL COMMENT '创建时间',
  `UPDATE_TIME` datetime(3) DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='';

CREATE TABLE IF NOT EXISTS `T_AUTH_GROUP_USER` (
  `ID` varchar(64) NOT NULL COMMENT '主键ID',
  `USER_ID` varchar(64) NOT NULL DEFAULT '""' COMMENT '用户ID',
  `GROUP_ID` varchar(64) NOT NULL DEFAULT '""' COMMENT '用户组ID',
  `CREATE_USER` varchar(64) NOT NULL DEFAULT '""' COMMENT '添加用户',
  `CREATE_TIME` datetime(3) NOT NULL COMMENT '添加时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='';

CREATE TABLE IF NOT EXISTS `T_AUTH_STRATEGY` (
  `ID` int(11) NOT NULL AUTO_INCREMENT COMMENT '策略主键ID',
  `STRATEGY_NAME` varchar(32) NOT NULL COMMENT '策略名称',
  `STRATEGY_BODY` varchar(2000) NOT NULL COMMENT '策略内容',
  `IS_DELETE` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除 0未删除 1删除',
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_TIME` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `CREATE_USER` varchar(32) NOT NULL COMMENT '创建人',
  `UPDATE_USER` varchar(32) DEFAULT NULL COMMENT '修改人',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='权限策略表';

CREATE TABLE IF NOT EXISTS `T_AUTH_MANAGER` (
  `ID` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `NAME` varchar(32) NOT NULL COMMENT '名称',
  `ORGANIZATION_ID` int(11) NOT NULL COMMENT '组织ID',
  `LEVEL` int(11) NOT NULL COMMENT '层级ID',
  `STRATEGYID` int(11) NOT NULL COMMENT '权限策略ID',
  `IS_DELETE` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除',
  `CREATE_USER` varchar(11) NOT NULL DEFAULT '""' COMMENT '创建用户',
  `UPDATE_USER` varchar(11) DEFAULT '""' COMMENT '修改用户',
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_TIME` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='管理员策略表';

CREATE TABLE IF NOT EXISTS `T_AUTH_MANAGER_USER` (
  `ID` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `USER_ID` varchar(64) NOT NULL COMMENT '用户ID',
  `MANAGER_ID` int(11) NOT NULL COMMENT '管理员权限ID',
  `START_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '权限生效起始时间',
  `END_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '权限生效结束时间',
  `CREATE_USER` varchar(64) NOT NULL COMMENT '创建用户',
  `UPDATE_USER` varchar(64) DEFAULT NULL COMMENT '修改用户',
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_TIME` timestamp NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `USER_ID+MANGER_ID` (`USER_ID`,`MANAGER_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='管理员用户表(只存有效期内的用户)';

CREATE TABLE IF NOT EXISTS `T_AUTH_MANAGER_USER_HISTORY` (
  `ID` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `USER_ID` varchar(64) NOT NULL COMMENT '用户ID',
  `MANAGER_ID` int(11) NOT NULL COMMENT '管理员权限ID',
  `START_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '权限生效起始时间',
  `END_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '权限生效结束时间',
  `CREATE_USER` varchar(64) NOT NULL COMMENT '创建用户',
  `UPDATE_USER` varchar(64) DEFAULT NULL COMMENT '修改用户',
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_TIME` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`ID`),
  KEY `MANGER_ID` (`MANAGER_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='管理员用户历史表';

CREATE TABLE IF NOT EXISTS `T_AUTH_MANAGER_WHITELIST` (
   `ID` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
   `MANAGER_ID` int(11) NOT NULL COMMENT '管理策略ID',
   `USER_ID` varchar(64) NOT NULL COMMENT '用户ID',
   PRIMARY KEY (`ID`),
   KEY `idx_manager` (`MANAGER_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='管理员自助申请表名单表';

CREATE TABLE IF NOT EXISTS `T_AUTH_IAM_CALLBACK` (
   `ID` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
   `GATEWAY` varchar(255) NOT NULL DEFAULT '""' COMMENT '目标服务网关',
   `PATH` varchar(1024) NOT NULL DEFAULT '""' COMMENT '目标接口路径',
   `DELETE_FLAG` bit(1) DEFAULT b'0' COMMENT '是否删除 true-是 false-否',
   `RESOURCE` varchar(32) NOT NULL DEFAULT '""' COMMENT '资源类型',
   `SYSTEM` varchar(32) NOT NULL DEFAULT '""' COMMENT '接入系统',
   PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM回调地址';

CREATE TABLE IF NOT EXISTS `T_AUTH_USER_BLACKLIST` (
   `ID` int(11) NOT NULL AUTO_INCREMENT,
   `USER_ID` varchar(32) NOT NULL COMMENT '用户ID',
   `REMARK` varchar(255) NOT NULL COMMENT '拉黑原因',
   `CREATE_TIME` datetime NOT NULL COMMENT '拉黑时间',
   `STATUS` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否生效 1生效 0不生效',
   PRIMARY KEY (`ID`),
   KEY `bk_userId` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `T_AUTH_USER_INFO`  (
   `ID` int NOT NULL AUTO_INCREMENT,
   `userId` varchar(255) NOT NULL COMMENT '用户ID',
   `email` varchar(255) NULL COMMENT '邮箱',
   `phone` varchar(32) NULL COMMENT '手机号',
   `create_time` datetime NOT NULL COMMENT '注册时间',
   `user_type` int NOT NULL COMMENT '用户类型 0.页面注册 1.GitHub 2.Gitlab',
   `last_login_time` datetime NULL COMMENT '最后登陆时间',
  `user_status` int NOT NULL COMMENT '用户状态,0--正常,1--冻结',
   PRIMARY KEY (`ID`),
   UNIQUE INDEX `bk_user`(`userId`, `user_type`)
)ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号信息表';

SET FOREIGN_KEY_CHECKS = 1;
