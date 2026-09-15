SET @cached_tokens_sql := IF(
    EXISTS(
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'logs'
          AND COLUMN_NAME = 'cached_tokens'
    ),
    'SELECT 1',
    'ALTER TABLE `logs` ADD COLUMN `cached_tokens` INT NOT NULL DEFAULT 0 AFTER `completion_tokens`'
);
PREPARE cached_tokens_stmt FROM @cached_tokens_sql;
EXECUTE cached_tokens_stmt;
DEALLOCATE PREPARE cached_tokens_stmt;

SET @key_index_sql := IF(
    EXISTS(
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'logs'
          AND COLUMN_NAME = 'key_index'
    ),
    'SELECT 1',
    'ALTER TABLE `logs` ADD COLUMN `key_index` INT DEFAULT NULL AFTER `upstream_request_id`'
);
PREPARE key_index_stmt FROM @key_index_sql;
EXECUTE key_index_stmt;
DEALLOCATE PREPARE key_index_stmt;