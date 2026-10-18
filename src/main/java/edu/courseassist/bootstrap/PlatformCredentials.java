package edu.courseassist.bootstrap;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import edu.courseassist.contract.CredentialUpdate.Slot;
import edu.courseassist.core.session.CredentialVault;

/** PasswordSafe owns durable keys. Platform APIs require a temporary String; no key is a setting,
 * view, diagnostic or H2 value. All calls run on controller workers, never on EDT. */
public final class PlatformCredentials implements CredentialVault {
    private CredentialAttributes attributes(Slot slot){return new CredentialAttributes("Course Assistant F0 / "+slot.name());}
    @Override public char[] read(Slot slot){String value=PasswordSafe.getInstance().getPassword(attributes(slot));return value==null?new char[0]:value.toCharArray();}
    @Override public void write(Slot slot,char[] value){PasswordSafe.getInstance().setPassword(attributes(slot),value.length==0?null:new String(value));}
}
