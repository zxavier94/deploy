package hudson.plugins.deploy.ssh;


import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;
import hudson.model.TaskListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Properties;

public class SshUtil {

    public static boolean testConnection(String username, String password, String host) {
        boolean b = false;
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, 22);

            session.setPassword(password);

            UserInfo ui = new SshUtil.MyUserInfo() {
                public void showMessage(String message) {
                    System.out.println("message = " + message);
                }

                public boolean promptYesNo(String message) {
                    System.out.println("message = " + message);
                    return true;
                }

                // If password is not given before the invocation of Session#connect(),
                // implement also following methods,
                //   * UserInfo#getPassword(),
                //   * UserInfo#promptPassword(String message) and
                //   * UIKeyboardInteractive#promptKeyboardInteractive()

            };

            session.setUserInfo(ui);

            // It must not be recommended, but if you want to skip host-key check,
            // invoke following,
            // session.setConfig("StrictHostKeyChecking", "no");

            //session.connect();
            session.connect(30000);   // making a connection with timeout.
            b = session.isConnected();
            return b;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(session != null) {
                session.disconnect();
            }
        }
        return false;
    }

    /**
     * 连接到指定的IP
     *
     * @throws JSchException
     */
    public static Session connect(String user, String passwd, String host, int port) throws JSchException {
        JSch jsch = new JSch();// 创建JSch对象
        Session session = jsch.getSession(user, host, port);// 根据用户名、主机ip、端口号获取一个Session对象
        session.setPassword(passwd);// 设置密码

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);// 为Session对象设置properties
        session.setTimeout(1500);// 设置超时
        session.connect();// 通过Session建立连接
        return session;
    }

    /**
     * 执行相关的命令
     *
     * @throws JSchException
     */
    public static void execCmd(Session session , String command, StringBuffer sb) throws JSchException {
        BufferedReader reader = null;
        Channel channel = null;
        try {
            if (command != null) {
                channel = session.openChannel("exec");
                ((ChannelExec) channel).setCommand(command);
                // ((ChannelExec) channel).setErrStream(System.err);
                channel.connect();

                InputStream in = channel.getInputStream();
                reader = new BufferedReader(new InputStreamReader(in));
                String buf = null;
                while ((buf = reader.readLine()) != null) {
                    System.out.println("buf = " + buf);
                    sb.append(buf).append("\n");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSchException e) {
            e.printStackTrace();
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            channel.disconnect();
            if(session != null) {
                session.disconnect();
            }
        }
    }

    public static void main(String[] args) throws JSchException {
        Session session = connect("ted","ted","192.168.1.121",22);
        execCmd(session,"/home/ted/apache-tomcat-8.5.39/bin/startup.sh",new StringBuffer());
    }

    public static abstract class MyUserInfo implements UserInfo, UIKeyboardInteractive {

        public String getPassword() {
            return null;
        }

        public boolean promptYesNo(String str) {
            return false;
        }

        public String getPassphrase() {
            return null;
        }

        public boolean promptPassphrase(String message) {
            return false;
        }

        public boolean promptPassword(String message) {
            return false;
        }

        public void showMessage(String message) {
        }

        public String[] promptKeyboardInteractive(String destination, String name,
            String instruction, String[] prompt, boolean[] echo) {
            return null;
        }
    }
}
