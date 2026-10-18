package edu.courseassist.core.session;

import edu.courseassist.contract.CredentialUpdate.Slot;

/** Only platform credentials implement this in production; never serialized into business H2. */
public interface CredentialVault {
    /** Caller owns and clears the returned copy. Empty means absent. */
    char[] read(Slot slot);
    void write(Slot slot,char[] value);
}
