CREATE TABLE IF NOT EXISTS mr_rag_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(64) NOT NULL,
    space VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_name VARCHAR(255) NULL,
    content_hash VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    created_by INT NOT NULL,
    indexed_at TIMESTAMP NULL,
    error_message VARCHAR(500) NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mr_rag_document_doc_no (doc_no),
    KEY idx_mr_rag_document_space_status (space, status),
    KEY idx_mr_rag_document_hash (space, title, content_hash)
);

CREATE TABLE IF NOT EXISTS mr_rag_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    chunk_no INT NOT NULL,
    locator VARCHAR(255) NULL,
    content_hash VARCHAR(64) NOT NULL,
    text MEDIUMTEXT NOT NULL,
    metadata_json JSON NULL,
    vector_id VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mr_rag_chunk_vector_id (vector_id),
    KEY idx_mr_rag_chunk_doc_version (doc_no, version),
    KEY idx_mr_rag_chunk_hash (content_hash)
);

CREATE TABLE IF NOT EXISTS mr_rag_index_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_no VARCHAR(64) NOT NULL,
    doc_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by INT NOT NULL,
    total_chunks INT NOT NULL DEFAULT 0,
    success_chunks INT NOT NULL DEFAULT 0,
    failed_reason VARCHAR(500) NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mr_rag_index_job_job_no (job_no),
    KEY idx_mr_rag_index_job_doc_no (doc_no),
    KEY idx_mr_rag_index_job_status (status)
);
