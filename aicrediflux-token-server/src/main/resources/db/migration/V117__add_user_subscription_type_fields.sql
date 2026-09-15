SET @subscription_type_sql := IF(
    EXISTS(
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_subscriptions'
          AND COLUMN_NAME = 'type'
    ),
    'SELECT 1',
    'ALTER TABLE `user_subscriptions` ADD COLUMN `type` VARCHAR(32) NOT NULL DEFAULT ''subscription'' AFTER `status`'
);
PREPARE subscription_type_stmt FROM @subscription_type_sql;
EXECUTE subscription_type_stmt;
DEALLOCATE PREPARE subscription_type_stmt;

SET @model_whitelist_sql := IF(
    EXISTS(
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'user_subscriptions'
          AND COLUMN_NAME = 'model_whitelist'
    ),
    'SELECT 1',
    'ALTER TABLE `user_subscriptions` ADD COLUMN `model_whitelist` TEXT DEFAULT NULL AFTER `type`'
);
PREPARE model_whitelist_stmt FROM @model_whitelist_sql;
EXECUTE model_whitelist_stmt;
DEALLOCATE PREPARE model_whitelist_stmt;
