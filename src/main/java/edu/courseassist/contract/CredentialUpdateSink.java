package edu.courseassist.contract;

import java.util.concurrent.CompletionStage;

/** Core-owned credential broker. Implementation later uses IDEA PasswordSafe. */
@FunctionalInterface
public interface CredentialUpdateSink {
    /**
     * Copies the short-lived input before completion and returns a single-use opaque reference.
     * The caller must clear its own arrays and close update after this stage terminates.
     * Acceptance/consumption still requires the application to be OFF; staging changes no config.
     */
    CompletionStage<String> stage(CredentialUpdate update);
}
