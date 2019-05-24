package hudson.plugins.deploy.ssh;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

/**
 * @Author: margo
 * @Date: 2019/5/22 16:58
 * @Description:
 */
public class TestSsh {
    public static void main(String[] arg){

        try{
            JSch jsch=new JSch();
            Session session=jsch.getSession("pttomcat", "192.168.1.66", 22);

            session.setPassword("pttomcat");

            UserInfo ui = new SshUtil.MyUserInfo(){
                public void showMessage(String message){
                    System.out.println("message = " + message);
                }
                public boolean promptYesNo(String message){
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
            boolean b = session.isConnected();
            System.out.println("b = " + b);
            session.connect(30000);   // making a connection with timeout.
            b = session.isConnected();
            System.out.println("b = " + b);
            Channel channel=session.openChannel("shell");

            // Enable agent-forwarding.
            //((ChannelShell)channel).setAgentForwarding(true);

            channel.setInputStream(System.in);
      /*
      // a hack for MS-DOS prompt on Windows.
      channel.setInputStream(new FilterInputStream(System.in){
          public int read(byte[] b, int off, int len)throws IOException{
            return in.read(b, off, (len>1024?1024:len));
          }
        });
       */
            channel.setOutputStream(System.out);

      /*
      // Choose the pty-type "vt102".
      ((ChannelShell)channel).setPtyType("vt102");
      */

      /*
      // Set environment variable "LANG" as "ja_JP.eucJP".
      ((ChannelShell)channel).setEnv("LANG", "ja_JP.eucJP");
      */

            //channel.connect();
            channel.connect(3*1000);
        }
        catch(Exception e){
            System.out.println(e);
        }
    }


}
