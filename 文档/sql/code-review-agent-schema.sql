-- 智能代码审查与重构助手 - MySQL建表脚本
-- 版本: v1.0
-- 字符集建议: utf8mb4

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS code_review_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE code_review_agent;

-- 1. 用户与认证
CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id VARCHAR(64) NOT NULL COMMENT '业务用户ID',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态:1启用,0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_user_id (user_id),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    token VARCHAR(512) NOT NULL COMMENT '访问令牌',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_sessions_session (session_id),
    KEY idx_user_sessions_user_id (user_id),
    KEY idx_user_sessions_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话表';

CREATE TABLE IF NOT EXISTS user_projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
    project_id VARCHAR(64) NOT NULL COMMENT '项目ID',
    project_name VARCHAR(128) NOT NULL COMMENT '项目名称',
    repo_url VARCHAR(512) DEFAULT NULL COMMENT '仓库地址',
    default_branch VARCHAR(64) DEFAULT 'main' COMMENT '默认分支',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_projects_user_project (user_id, project_id),
    KEY idx_user_projects_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户项目关联表';

-- 2. 审查任务主表
CREATE TABLE IF NOT EXISTS review_tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id VARCHAR(64) NOT NULL COMMENT '审查ID',
    batch_id VARCHAR(64) DEFAULT NULL COMMENT '批量任务ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    project_id VARCHAR(64) DEFAULT NULL COMMENT '项目ID',
    review_type VARCHAR(32) NOT NULL COMMENT 'GIT_DIFF/PASTE_CODE',
    language VARCHAR(32) DEFAULT 'java' COMMENT '编程语言',
    template_id VARCHAR(64) DEFAULT NULL COMMENT '模板ID',
    status VARCHAR(32) NOT NULL COMMENT 'PROCESSING/COMPLETED/CANCELLED/FAILED',
    score INT DEFAULT NULL COMMENT '审查得分',
    total_issues INT NOT NULL DEFAULT 0 COMMENT '问题总数',
    summary TEXT COMMENT '摘要',
    code_content MEDIUMTEXT COMMENT '输入代码/DIFF',
    refactored_code MEDIUMTEXT COMMENT '重构后代码',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_review_tasks_review_id (review_id),
    KEY idx_review_tasks_session_id (session_id),
    KEY idx_review_tasks_project_id (project_id),
    KEY idx_review_tasks_status (status),
    KEY idx_review_tasks_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查任务表';

-- 3. 问题明细与建议
CREATE TABLE IF NOT EXISTS review_issues (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    issue_id VARCHAR(64) NOT NULL COMMENT '业务问题ID',
    review_id VARCHAR(64) NOT NULL COMMENT '审查ID',
    severity VARCHAR(16) NOT NULL COMMENT 'CRITICAL/HIGH/MEDIUM/LOW',
    file_path VARCHAR(512) DEFAULT NULL COMMENT '文件路径',
    line_number INT DEFAULT 0 COMMENT '行号',
    message VARCHAR(1024) NOT NULL COMMENT '问题描述',
    rule_id VARCHAR(128) DEFAULT NULL COMMENT '规则ID',
    suggestion VARCHAR(1024) DEFAULT NULL COMMENT '修复建议',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_review_issues_issue_id (issue_id),
    KEY idx_review_issues_review_id (review_id),
    KEY idx_review_issues_severity (severity),
    CONSTRAINT fk_review_issues_review_id FOREIGN KEY (review_id)
        REFERENCES review_tasks(review_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查问题明细';

CREATE TABLE IF NOT EXISTS review_suggestions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id VARCHAR(64) NOT NULL COMMENT '审查ID',
    priority_no INT NOT NULL COMMENT '建议优先级',
    title VARCHAR(256) NOT NULL COMMENT '建议标题',
    description VARCHAR(1024) NOT NULL COMMENT '建议描述',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_suggestions_review_id (review_id),
    KEY idx_review_suggestions_priority (priority_no),
    CONSTRAINT fk_review_suggestions_review_id FOREIGN KEY (review_id)
        REFERENCES review_tasks(review_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查建议表';

CREATE TABLE IF NOT EXISTS similar_code_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id VARCHAR(64) NOT NULL COMMENT '相似组ID',
    review_id VARCHAR(64) NOT NULL COMMENT '审查ID',
    similarity DECIMAL(5,4) NOT NULL COMMENT '相似度',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_similar_code_groups_group_id (group_id),
    KEY idx_similar_code_groups_review_id (review_id),
    CONSTRAINT fk_similar_code_groups_review_id FOREIGN KEY (review_id)
        REFERENCES review_tasks(review_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相似代码分组';

CREATE TABLE IF NOT EXISTS similar_code_blocks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_id VARCHAR(64) NOT NULL COMMENT '相似组ID',
    block_content MEDIUMTEXT NOT NULL COMMENT '代码片段',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_similar_code_blocks_group_id (group_id),
    CONSTRAINT fk_similar_code_blocks_group_id FOREIGN KEY (group_id)
        REFERENCES similar_code_groups(group_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相似代码片段';

-- 4. 审查模板与规则
CREATE TABLE IF NOT EXISTS review_templates (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id VARCHAR(64) NOT NULL COMMENT '模板ID',
    name VARCHAR(128) NOT NULL COMMENT '模板名',
    description VARCHAR(512) DEFAULT NULL COMMENT '模板描述',
    language VARCHAR(32) DEFAULT 'all' COMMENT '适用语言',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    is_builtin TINYINT NOT NULL DEFAULT 1 COMMENT '是否内置',
    created_by VARCHAR(64) DEFAULT NULL COMMENT '创建者',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_review_templates_template_id (template_id),
    KEY idx_review_templates_language (language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查模板';

CREATE TABLE IF NOT EXISTS review_template_rules (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id VARCHAR(64) NOT NULL COMMENT '模板ID',
    rule_id VARCHAR(128) NOT NULL COMMENT '规则ID',
    rule_name VARCHAR(256) NOT NULL COMMENT '规则名',
    severity VARCHAR(16) NOT NULL COMMENT '严重级别',
    rule_params JSON DEFAULT NULL COMMENT '规则参数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_template_rules_template_id (template_id),
    KEY idx_review_template_rules_rule_id (rule_id),
    CONSTRAINT fk_review_template_rules_template_id FOREIGN KEY (template_id)
        REFERENCES review_templates(template_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板规则';

-- 5. 批量与增量审查
CREATE TABLE IF NOT EXISTS batch_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id VARCHAR(64) NOT NULL COMMENT '批量ID',
    user_id VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    template_id VARCHAR(64) DEFAULT NULL COMMENT '模板ID',
    parallel_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否并行',
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '批次状态',
    task_count INT NOT NULL DEFAULT 0 COMMENT '任务数量',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_reviews_batch_id (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量审查主表';

CREATE TABLE IF NOT EXISTS batch_review_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id VARCHAR(64) NOT NULL COMMENT '批量ID',
    task_id VARCHAR(64) NOT NULL COMMENT '子任务ID',
    review_id VARCHAR(64) DEFAULT NULL COMMENT '审查ID',
    language VARCHAR(32) DEFAULT 'java' COMMENT '语言',
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    code_content MEDIUMTEXT COMMENT '提交代码',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_batch_review_items_task_id (task_id),
    KEY idx_batch_review_items_batch_id (batch_id),
    KEY idx_batch_review_items_review_id (review_id),
    CONSTRAINT fk_batch_review_items_batch_id FOREIGN KEY (batch_id)
        REFERENCES batch_reviews(batch_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量审查子项';

-- 6. 聊天与历史检索
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    role VARCHAR(32) NOT NULL COMMENT 'user/assistant/system',
    content MEDIUMTEXT NOT NULL COMMENT '消息内容',
    prompt_tokens INT DEFAULT 0 COMMENT '输入token数',
    completion_tokens INT DEFAULT 0 COMMENT '输出token数',
    total_tokens INT DEFAULT 0 COMMENT '总token数',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_chat_messages_message_id (message_id),
    KEY idx_chat_messages_session_id (session_id),
    KEY idx_chat_messages_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息';

CREATE TABLE IF NOT EXISTS review_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id VARCHAR(64) NOT NULL COMMENT '历史记录ID',
    review_id VARCHAR(64) NOT NULL COMMENT '审查ID',
    session_id VARCHAR(64) NOT NULL COMMENT '会话ID',
    project_id VARCHAR(64) DEFAULT NULL COMMENT '项目ID',
    summary TEXT COMMENT '历史摘要',
    embedding_model VARCHAR(64) DEFAULT NULL COMMENT '向量模型',
    embedding_dim INT DEFAULT NULL COMMENT '向量维度',
    metadata JSON DEFAULT NULL COMMENT '扩展信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_review_history_record_id (record_id),
    KEY idx_review_history_review_id (review_id),
    KEY idx_review_history_session_id (session_id),
    KEY idx_review_history_project_id (project_id),
    CONSTRAINT fk_review_history_review_id FOREIGN KEY (review_id)
        REFERENCES review_tasks(review_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查历史';

SET FOREIGN_KEY_CHECKS = 1;
