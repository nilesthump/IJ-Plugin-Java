package edu.courseassist.bootstrap.ide;

import edu.courseassist.contract.ApplicationContracts.*;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.session.CredentialVault;
import edu.courseassist.model.ConfiguredRuntimeFactory;
import java.net.URI;
import java.util.Optional;

/** Real plugin classloader, explicit non-secret user model identifiers, synthetic credentials.
 * Construct/close only: no model or embedding request is sent. */
final class ConfiguredEnableChecks {
    static void run() throws Exception {
        var generation = new ProviderProfile("g","deepseek",URI.create("https://api.deepseek.com"),"deepseek-flash",Optional.empty());
        var embedding = new ProviderProfile("e","course",URI.create("https://agi.tongji.edu.cn"),"Qwen3-Embedding",Optional.of(1024));
        CredentialVault vault = new CredentialVault() {
            public char[] read(Slot slot) { return "synthetic-construction-only".toCharArray(); }
            public void write(Slot slot,char[] value) { throw new AssertionError("No credential writes"); }
        };
        try (var runtime = new ConfiguredRuntimeFactory().create(new ProviderConfiguration(generation,embedding),vault)) {
            DependencyChecks.require(runtime.embeddings().space().dimensions()==1024,"Configured runtime constructs inside real IDE");
        }
    }
}
