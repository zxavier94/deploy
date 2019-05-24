package hudson.plugins.deploy.ssh;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.plugins.deploy.tomcat.TomcatUtil;
import java.io.File;
import java.io.PrintStream;
import java.util.UUID;
import jenkins.model.Jenkins;
import jenkins.plugins.publish_over.Credentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @Author: margo
 * @Date: 2019/5/21 14:40
 * @Description:
 */
public class SshCredentials extends SshKeyInfo implements Credentials<SshCredentials> {

    private static final long serialVersionUID = -3170152720976677723L;
    private static final int wait_time = 100;
    private String sshHostname;
    private String username;

    @DataBoundConstructor
    public SshCredentials(final String sshHostname, final String username, final String encryptedPassphrase) {
        super(encryptedPassphrase);
        this.sshHostname = sshHostname;
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public String getSshHostname() {
        return sshHostname;
    }

    @DataBoundSetter
    public void setSshHostname(String sshHostname) {
        this.sshHostname = sshHostname;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public Descriptor<SshCredentials> getDescriptor() {
        return Jenkins.getActiveInstance().getDescriptorByType(SshCredentialsDescriptor.class);
    }

    public void startupRemoteContainer(TaskListener listener,String containerAbsolutePath,String containerType)
        throws Exception {
        PrintStream logger = listener.getLogger();
        logger.println("Container absolute path:" + containerAbsolutePath);
        logger.println("hostname:" + sshHostname);
        logger.println("username:" + username);
        System.out.println("super.getEncryptedPassphrase() = " + super.getEncryptedPassphrase());
        String folder=System.getProperty("java.io.tmpdir");
        String fileUUID = UUID.randomUUID().toString();
        SftpUtil su = new SftpUtil();
        ChannelSftp sftp = su.getSftpConnect(sshHostname, 22, username, super.getPassphrase());
        if("Tomcat".equals(containerType)) {
            // 如果是tomcat容器
            String tempFile = folder + File.separator + fileUUID + ".xml";
            logger.println("缓存:" + tempFile);
            SftpUtil.download(containerAbsolutePath + File.separator + "conf"+ File.separator +"server.xml", tempFile, sftp);
            SftpUtil.exit(sftp);
            String port = TomcatUtil.getPort(tempFile);
            boolean b = TomcatUtil.testURL("http://" + sshHostname + ":" + port);
            if(!b) {
                // 不能请求远程tomcat页面,重新启动tomcat
                Session session = SshUtil.connect(username, super.getPassphrase(), sshHostname, 22);
                StringBuffer sb = new StringBuffer();
                SshUtil.execCmd(session,containerAbsolutePath + "/" + "bin" + "/" + "startup.sh",sb);
                int count = 0;
                boolean b1 = false;
                while(true) {
                    b1 = TomcatUtil.testURL("http://" + sshHostname + ":" + port);
                    if(b1 || count >= wait_time) {
                        if(count >= wait_time) {
                            throw new RuntimeException("timeout");
                        }
                        break;
                    } else {
                        Thread.sleep(1000);
                    }
                    count++;
                }
            }
        }

    }
}
