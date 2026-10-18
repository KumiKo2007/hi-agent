package org.xhy.application.run.service;

/** Converts connector output into a stable external-resource reference for the run record. */
@FunctionalInterface
public interface ApprovedActionResultNormalizer {
    String normalize(String serverName, String actionName, String targetRepository, String resultPayload);
}
