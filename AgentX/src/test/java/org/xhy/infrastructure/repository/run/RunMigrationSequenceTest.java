package org.xhy.infrastructure.repository.run;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunMigrationSequenceTest {
    @Test
    void appliesRunMigrationsInVersionOrderIncludingApprovalAndTargetChanges() {
        List<String> migrations = RunPostgresRabbitIntegrationTest.RUN_MIGRATION_RESOURCES;

        assertEquals(List.of("V20261015001__create_agent_runs.sql", "V20261015002__create_tool_action_policies.sql",
                "V20261015004__run_delivery_lease.sql", "V20261016001__pin_run_agent_version.sql",
                "V20261016008__pin_run_knowledge_version.sql", "V20261017002__persist_run_approvals.sql",
                "V20261017003__add_creator_approval_policies.sql",
                "V20261017004__persist_approved_external_resource.sql", "V20261017005__audit_run_reconciliation.sql",
                "V20261017006__add_reconciliation_evidence_references.sql",
                "V20261017007__pin_run_target_repository.sql", "V20261017008__add_run_timeline_events.sql"),
                migrations);
    }

    @Test
    void keepsReconciliationSchemaInForwardMigrations() throws IOException {
        String approvals = read("V20261017002__persist_run_approvals.sql");
        String reconciliation = read("V20261017005__audit_run_reconciliation.sql");
        String evidenceReferences = read("V20261017006__add_reconciliation_evidence_references.sql");

        assertFalse(approvals.contains("reconciliation_state"));
        assertFalse(approvals.contains("agent_run_reconciliations"));
        assertTrue(reconciliation.contains("reconciliation_state"));
        assertTrue(reconciliation.contains("CREATE TABLE IF NOT EXISTS agent_run_reconciliations"));
        assertTrue(evidenceReferences.contains("reconciliation_evidence_reference"));
        assertTrue(evidenceReferences.contains("evidence_reference"));
    }

    @Test
    void includesTimelineTableInCleanRunMigrationSequence() throws IOException {
        String timeline = read("V20261017008__add_run_timeline_events.sql");

        assertTrue(timeline.contains("CREATE TABLE IF NOT EXISTS agent_run_timeline_events"));
        assertTrue(timeline.contains("idx_agent_run_timeline_events_run_order"));
        assertTrue(RunPostgresRabbitIntegrationTest.RUN_MIGRATION_RESOURCES
                .contains("V20261017008__add_run_timeline_events.sql"));
    }

    private static String read(String migration) throws IOException {
        try (var input = new ClassPathResource("db/migration/" + migration).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
