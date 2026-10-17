ALTER TABLE file_detail ADD COLUMN IF NOT EXISTS processing_error VARCHAR(500);
ALTER TABLE rag_versions ADD COLUMN IF NOT EXISTS search_ready BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN file_detail.processing_error IS '可展示给使用者的文件处理失败原因';
COMMENT ON COLUMN rag_versions.search_ready IS '通过全部文件就绪检查创建的不可变可检索版本';
