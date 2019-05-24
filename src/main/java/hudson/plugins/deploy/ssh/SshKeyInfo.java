package hudson.plugins.deploy.ssh;

import hudson.util.Secret;
import java.io.Serializable;

/**
 * @Author: margo
 * @Date: 2019/5/21 14:48
 * @Description:
 */
public class SshKeyInfo implements Serializable {

    private static final long serialVersionUID = -4980997262329529950L;

    private String passphrase;
    private Secret secretPassphrase;

    public SshKeyInfo(final String encryptedPassphrase) {
        secretPassphrase = Secret.fromString(encryptedPassphrase);
    }

    protected final String getPassphrase() { return Secret.toString(secretPassphrase); }
    public final void setPassphrase(final String passphrase) { secretPassphrase = Secret.fromString(passphrase); }

    public final String getEncryptedPassphrase() {
        return (secretPassphrase == null) ? null : secretPassphrase.getEncryptedValue();
    }


    public Object readResolve() {
        if (secretPassphrase == null)
            secretPassphrase = Secret.fromString(passphrase);
        passphrase = null;
        return this;
    }
}
