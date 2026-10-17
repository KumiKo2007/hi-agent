package org.xhy.application.run.service;

/** Validates and revalidates a run's selected target through its owning external integration. */
public interface RunTargetRepositoryAuthorization {
    /** Returns the canonical, authorized repository key to pin on the run. */
    String authorize(String userId, String requestedRepository);

    /** Rechecks the authorization immediately before an approved external action is dispatched. */
    void revalidate(String userId, String authorizedRepository);
}
