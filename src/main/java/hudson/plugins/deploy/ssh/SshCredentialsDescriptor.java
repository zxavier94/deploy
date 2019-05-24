package hudson.plugins.deploy.ssh;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.IOException;
import javax.servlet.ServletException;
import org.kohsuke.stapler.QueryParameter;

/**
 * @Author: margo
 * @Date: 2019/5/21 14:59
 * @Description:
 */
@Extension
public class SshCredentialsDescriptor extends Descriptor<SshCredentials> {

    public SshCredentialsDescriptor() {
        super(SshCredentials.class);
    }

    @Override
    public String getDisplayName() {
        return "not seen";
    }

    public FormValidation doTestConnection(@QueryParameter("sshHostname") String sshHostname,@QueryParameter("username") String username,
        @QueryParameter("encryptedPassphrase") String encryptedPassphrase) throws IOException, ServletException {
        try {
            boolean b1 = SshUtil.testConnection(username, Secret.toString(Secret.decrypt(encryptedPassphrase)), sshHostname);
            boolean b2 = SshUtil.testConnection(username, encryptedPassphrase, sshHostname);
            if(b1 || b2) {
                return FormValidation.ok("Success");
            } else {
                return FormValidation.error("Failure");
            }
        } catch (Exception e) {
            return FormValidation.error("Client error : "+e.getMessage());
        }
    }
}
