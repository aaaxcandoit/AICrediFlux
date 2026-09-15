CREATE TABLE mr_token_package (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    credit_amount BIGINT NOT NULL,
    original_price BIGINT NOT NULL,
    sale_price BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_package_status_sort(status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_flash_sale_campaign (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    package_id BIGINT UNSIGNED NOT NULL,
    activity_name VARCHAR(128) NOT NULL,
    flash_price BIGINT NOT NULL,
    credit_amount BIGINT NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    per_user_limit INT NOT NULL DEFAULT 1,
    start_time DATETIME(3) NOT NULL,
    end_time DATETIME(3) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), KEY idx_campaign_package(package_id), KEY idx_campaign_time_status(status,start_time,end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_flash_sale_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    user_id INT NOT NULL,
    package_id BIGINT UNSIGNED NOT NULL,
    campaign_id BIGINT UNSIGNED NULL,
    package_name VARCHAR(128) NOT NULL,
    credit_amount BIGINT NOT NULL,
    pay_amount BIGINT NOT NULL,
    order_source VARCHAR(20) NOT NULL,
    order_status VARCHAR(20) NOT NULL,
    pay_status VARCHAR(20) NOT NULL,
    credit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expire_time DATETIME(3) NOT NULL,
    pay_time DATETIME(3) NULL,
    close_time DATETIME(3) NULL,
    stock_restored TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_flash_order_no(order_no),
    UNIQUE KEY uk_flash_campaign_user(campaign_id,user_id), KEY idx_flash_order_user_time(user_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_flash_sale_request (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    campaign_id BIGINT UNSIGNED NOT NULL,
    user_id INT NOT NULL,
    process_status VARCHAR(20) NOT NULL,
    fail_reason VARCHAR(512) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_flash_request_no(request_no), UNIQUE KEY uk_flash_request_order(order_no),
    UNIQUE KEY uk_flash_request_campaign_user(campaign_id,user_id), KEY idx_flash_request_status(process_status,update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_credit_grant_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    grant_biz_no VARCHAR(96) NOT NULL,
    credit_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    fail_reason VARCHAR(512) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_credit_grant_biz_no(grant_biz_no), UNIQUE KEY uk_credit_grant_order_no(order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mr_flash_sale_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    aggregate_no VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    deliver_at DATETIME(3) NULL,
    publish_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_time DATETIME(3) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_flash_outbox_event(event_id), KEY idx_flash_outbox_publish(publish_status,next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO mr_token_package(name,description,credit_amount,original_price,sale_price,status,sort)
VALUES ('20 万 AI Credit 体验包','适合首次体验平台模型',200000,1990,990,1,10),
       ('100 万 AI Credit 标准包','适合日常开发与测试',1000000,5900,3900,1,20),
       ('500 万 AI Credit 秒杀包','限时活动演示套餐',5000000,19900,9900,1,30);
