ALTER TABLE tools
    ADD COLUMN transport_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY_SSE',
    ADD CONSTRAINT tools_transport_type_check CHECK (transport_type IN ('STREAMABLE_HTTP', 'LEGACY_SSE'));

ALTER TABLE tool_versions
    ADD COLUMN transport_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY_SSE',
    ADD CONSTRAINT tool_versions_transport_type_check CHECK (transport_type IN ('STREAMABLE_HTTP', 'LEGACY_SSE'));

ALTER TABLE user_tools
    ADD COLUMN transport_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY_SSE',
    ADD CONSTRAINT user_tools_transport_type_check CHECK (transport_type IN ('STREAMABLE_HTTP', 'LEGACY_SSE'));
