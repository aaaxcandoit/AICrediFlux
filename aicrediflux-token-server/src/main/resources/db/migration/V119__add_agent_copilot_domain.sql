CREATE TABLE mr_agent_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_no VARCHAR(64) NOT NULL,
    user_id INT NOT NULL,
    title VARCHAR(120) NOT NULL,
    model VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_session_no(session_no),
    KEY idx_agent_session_user_time(user_id, update_time),
    KEY idx_agent_session_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_agent_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_no VARCHAR(64) NOT NULL,
    session_no VARCHAR(64) NOT NULL,
    user_id INT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    tool_call_id VARCHAR(128) NULL,
    metadata JSON NULL,
    usage_json JSON NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_message_no(message_no),
    KEY idx_agent_message_session_id(session_no, id),
    KEY idx_agent_message_user_time(user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_agent_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    run_no VARCHAR(64) NOT NULL,
    session_no VARCHAR(64) NOT NULL,
    user_id INT NOT NULL,
    model VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'AGENT',
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    error_message VARCHAR(1000) NULL,
    total_prompt_tokens INT NOT NULL DEFAULT 0,
    total_completion_tokens INT NOT NULL DEFAULT 0,
    total_quota BIGINT NOT NULL DEFAULT 0,
    stop_requested TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_run_no(run_no),
    KEY idx_agent_run_session_status(session_no, status),
    KEY idx_agent_run_user_time(user_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_agent_tool_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tool_log_no VARCHAR(64) NOT NULL,
    run_no VARCHAR(64) NOT NULL,
    session_no VARCHAR(64) NOT NULL,
    user_id INT NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    tool_call_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    input_json JSON NULL,
    output_json JSON NULL,
    error_message VARCHAR(1000) NULL,
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool_log_no(tool_log_no),
    KEY idx_agent_tool_run(run_no, id),
    KEY idx_agent_tool_session(session_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
