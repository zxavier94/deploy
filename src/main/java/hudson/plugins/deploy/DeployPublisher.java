package hudson.plugins.deploy;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.plugins.deploy.ssh.ContainerLoc;
import hudson.plugins.deploy.ssh.SshCredentials;
import hudson.plugins.deploy.tomcat.TomcatAdapter;
import hudson.plugins.deploy.tomcat.TomcatUtil;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.VariableResolver;
import java.io.File;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import jenkins.util.io.FileBoolean;
import net.sf.json.JSONObject;
import org.dom4j.DocumentException;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deploys WAR to a container.
 *
 * @author Kohsuke Kawaguchi
 */
public class DeployPublisher extends Notifier implements SimpleBuildStep, Serializable {

    private static final String containerLocation = "containerLoc";

    private List<ContainerAdapter> adapters;
    private String contextPath = "";

    private String         war;
    private boolean        onFailure = true;
    private JSONObject     onShutDown;
    private SshCredentials sshCredentials;
    private String         sshCredentialsId;

    /**
     * @deprecated
     *      Use {@link #getAdapters()}
     */
    public final ContainerAdapter adapter = null;

    public DeployPublisher(List<ContainerAdapter> adapters, String war) {
        this.adapters = adapters;
        this.war = war;
    }

    @DataBoundConstructor
    public DeployPublisher(List<ContainerAdapter> adapters, String war,JSONObject onShutDown,String sshCredentialsId) {
        this.adapters = adapters;
        this.war = war;
        this.onShutDown = onShutDown;
        this.sshCredentials = initSshCredentials();
        this.sshCredentialsId = sshCredentialsId;
    }

    @Deprecated
    public DeployPublisher(List<ContainerAdapter> adapters, String war, String contextPath, boolean onFailure) {
   		this.adapters = adapters;
        this.war = war;
    }

    public String getWar () {
        return war;
    }

    public boolean isOnFailure () {
        return onFailure;
    }

    @DataBoundSetter
    public void setOnFailure (boolean onFailure) {
        this.onFailure = onFailure;
    }

    public JSONObject getOnShutDown() {
        return onShutDown;
    }

    @DataBoundSetter
    public void setOnShutDown(JSONObject onShutDown) {
        this.onShutDown = onShutDown;
    }

    public boolean isOnShutDown() {
        if(onShutDown != null && onShutDown.get(containerLocation) != null) {
            return true;
        }
        return false;
    }

    public String getAbsolutePath() {
        if(onShutDown != null && onShutDown.get(containerLocation) != null) {
            JSONObject o = (JSONObject) onShutDown.get(containerLocation);
            if(o != null && o.containsKey("absolutePath")) {
                return o.getString("absolutePath") == null ? "" : o.getString("absolutePath");
            }
        }
        return "";
    }

    public String getContainerLocation() {
        if(onShutDown != null && onShutDown.get(containerLocation) != null) {
            JSONObject o = (JSONObject) onShutDown.get(containerLocation);
            if(o != null && o.containsKey("value")) {
                String value = o.getString("value");
                return value == null ? ContainerLoc.loc.toString() : value;
            }
        }
        return ContainerLoc.loc.toString();
    }
    public String getContextPath () {
        return contextPath;
    }

    public SshCredentials getSshCredentials() {
        return sshCredentials;
    }

    public SshCredentials initSshCredentials() {
        String location = getContainerLocation();
        if(ContainerLoc.remote.toString().equals(location)) {
            JSONObject jsonObject = this.onShutDown.getJSONObject(containerLocation);
            String sshHostname = jsonObject.getString("sshHostname");
            String username = jsonObject.getString("username");
            String encryptedPassphrase = jsonObject.getString("encryptedPassphrase");
            return new SshCredentials(sshHostname,username,encryptedPassphrase);
        }
        return null;
    }

    @DataBoundSetter
    public void setContextPath (String contextPath) {
        this.contextPath = Util.fixEmpty(contextPath);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        Result result = run.getResult();
        if (onFailure || result == null || Result.SUCCESS.equals(result)) {
            if (!workspace.exists()) {
                listener.getLogger().println("[DeployPublisher][ERROR] Workspace not found");
                throw new AbortException("Workspace not found");
            }
            EnvVars envVars = new EnvVars();
            if (run instanceof AbstractBuild) {
                final AbstractBuild build = (AbstractBuild) run;
                envVars = build.getEnvironment(listener);
            }
            
            final VariableResolver<String> resolver = new VariableResolver.ByMap<String>(envVars);
            final String warFiles = Util.replaceMacro(envVars.expand(this.war), resolver); 

            FilePath[] wars = workspace.list(warFiles);
            if (wars == null || wars.length == 0) {
                throw new InterruptedException("[DeployPublisher][WARN] No wars found. Deploy aborted. %n");
            }
            listener.getLogger().printf("[DeployPublisher][INFO] Attempting to deploy %d war file(s)%n", wars.length);
            for (FilePath warFile : wars) {
                for (ContainerAdapter adapter : adapters) {
                    if(isOnShutDown()) {
                        listener.getLogger().println("start container first...");
                        if(adapter instanceof TomcatAdapter) {
                            listener.getLogger().println("container is tomcat");
                            if(getContainerLocation().equals(ContainerLoc.loc.toString())) {
                                // 启动本地tomcat
                                String tempPath = getAbsolutePath();
                                if(tempPath != null && (tempPath.endsWith("/") || tempPath.endsWith("\\"))) {
                                    tempPath = tempPath.substring(0,tempPath.length() - 1);
                                }
                                String systemType = System.getProperty("os.name");
                                listener.getLogger().println("system os:" + systemType);
                                String execCmdSuffix = "";
                                if(systemType.toLowerCase().startsWith("win")) {
                                    execCmdSuffix = ".bat";
                                } else {
                                    execCmdSuffix = ".sh";
                                }
                                File startup = new File(tempPath + File.separator + "bin" + File.separator + "startup" + execCmdSuffix);
                                File shutdown = new File(tempPath + File.separator + "bin" + File.separator + "shutdown" + execCmdSuffix);
                                if(startup.exists() && shutdown.exists()) {
                                    listener.getLogger().println("startup path " + startup);
//                                    listener.getLogger().println("shutdown path " + shutdown);
//                                    listener.getLogger().println("prepare shutdown container...");
//                                    ExecCmd.cmd(listener.getLogger(),shutdown.getAbsolutePath());
                                    boolean flag = false;
                                    String port = "";
                                    try {
                                        port = TomcatUtil.getTomcatPort(tempPath);
                                        flag = true;
                                    } catch (DocumentException e) {
                                        e.printStackTrace();
                                        listener.error("get tomcat port failed," + e.getMessage());
                                        listener.error("will not start container first");
                                    }
                                    if(flag && !TomcatUtil.testURL("http://localhost:" + port)) {
                                        listener.getLogger().println("prepare startup container...");
                                        ExecCmd.cmd(listener.getLogger(),startup.getAbsolutePath());
                                    }
                                } else {
                                    listener.error("startup path " + startup + " exists:" + startup.exists());
//                                    listener.error("shutdown path " + shutdown + " exists:" + shutdown.exists());
                                    listener.error("will not start container first");
                                }

                            } else if(getContainerLocation().equals(ContainerLoc.remote.toString())) {
                                // 启动远程tomcat 需要ssh
                                try {
                                    this.sshCredentials.startupRemoteContainer(listener,getAbsolutePath(),"Tomcat");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    if("timeout".equals(e.getMessage())) {
                                        listener.error("timeout");
                                        run.setResult(Result.FAILURE);
                                        return;
                                    }
                                }
                            }
                        }
                    }

                    adapter.redeployFile(warFile, contextPath, run, launcher, listener);
                }
            }
        } else {
            listener.getLogger().println("[DeployPublisher][INFO] Build failed, project not deployed");
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }
    
    public Object readResolve() {
    	if(adapter != null) {
    		if(adapters == null) {
    			adapters = new ArrayList<ContainerAdapter>();
    		}
    		adapters.add(adapter);
    	}
    	return this;
    }

    /**
	 * Get the value of the adapterWrappers property
	 *
	 * @return The value of adapterWrappers
	 */
	public List<ContainerAdapter> getAdapters() {
		return adapters;
	}

    @Symbol("deploy")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public boolean defaultOnFailure (Object job) {
            return !(job instanceof AbstractProject);
        }

        public String getDisplayName() {
            return Messages.DeployPublisher_DisplayName();
        }

        /**
         * Sort the descriptors so that the order they are displayed is more predictable
         *
         * @return a alphabetically sorted list of AdapterDescriptors
         */
        public List<ContainerAdapterDescriptor> getAdaptersDescriptors() {
            List<ContainerAdapterDescriptor> r = new ArrayList<ContainerAdapterDescriptor>(ContainerAdapter.all());
            Collections.sort(r,new Comparator<ContainerAdapterDescriptor>() {
                public int compare(ContainerAdapterDescriptor o1, ContainerAdapterDescriptor o2) {
                    return o1.getDisplayName().compareTo(o2.getDisplayName());
                }
            });
            return r;
        }
    }

    private static final long serialVersionUID = 1L;

    @Restricted(NoExternalUse.class)
    @Extension
    public static final class Migrator extends ItemListener {

        @SuppressWarnings("deprecation")
        @Override
        public void onLoaded() {
            FileBoolean migrated = new FileBoolean(getClass(), "migratedCredentials");
            if (migrated.isOn()) {
                return;
            }
            List<StandardUsernamePasswordCredentials> generatedCredentials = new ArrayList<StandardUsernamePasswordCredentials>();
            for (AbstractProject<?,?> project : Jenkins.getActiveInstance().getAllItems(AbstractProject.class)) {
                try {
                    DeployPublisher d = project.getPublishersList().get(DeployPublisher.class);
                    if (d == null) {
                        continue;
                    }
                    boolean modified = false;
                    boolean successful = true;
                    for (ContainerAdapter a : d.getAdapters()) {
                        if (a instanceof PasswordProtectedAdapterCargo) {
                            PasswordProtectedAdapterCargo ppac = (PasswordProtectedAdapterCargo) a;
                            if (ppac.getCredentialsId() == null) {
                                successful &= ppac.migrateCredentials(generatedCredentials);
                                modified = true;
                            }
                        }
                    }
                    if (modified) {
                        if (successful) {
                            Logger.getLogger(DeployPublisher.class.getName()).log(Level.INFO, "Successfully migrated DeployPublisher in project: {0}", project.getName());
                            project.save();
                        } else {
                            // Avoid calling project.save() because PasswordProtectedAdapterCargo will null out the username/password fields upon saving
                            Logger.getLogger(DeployPublisher.class.getName()).log(Level.SEVERE, "Failed to create credentials and migrate DeployPublisher in project: {0}, please manually add credentials.", project.getName());
                        }
                    }
                } catch (IOException e) {
                    Logger.getLogger(DeployPublisher.class.getName()).log(Level.WARNING, "Migration unsuccessful", e);
                }
            }
            migrated.on();
        }
    }

}
