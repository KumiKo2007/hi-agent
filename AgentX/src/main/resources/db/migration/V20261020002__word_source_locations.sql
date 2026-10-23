-- 原文定位只能在重新处理时取得；历史记录保留空值，调用方必须披露不足。
ALTER TABLE document_unit ADD COLUMN IF NOT EXISTS source_locator_type varchar(40);
ALTER TABLE document_unit ADD COLUMN IF NOT EXISTS source_locator text;
ALTER TABLE document_unit ADD COLUMN IF NOT EXISTS coverage_warnings text;
ALTER TABLE rag_version_documents ADD COLUMN IF NOT EXISTS source_locator_type varchar(40);
ALTER TABLE rag_version_documents ADD COLUMN IF NOT EXISTS source_locator text;
ALTER TABLE rag_version_documents ADD COLUMN IF NOT EXISTS coverage_warnings text;
