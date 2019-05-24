package hudson.plugins.deploy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * @Author: margo
 * @Date: 2019/5/22 11:27
 * @Description:
 */
public class ExecCmd {

    public static void cmd(PrintStream logger,String cmd) throws InterruptedException, IOException {
        Runtime rt = Runtime.getRuntime();
        Process p = rt.exec(cmd);
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(p.getInputStream()))) {
            String line = null;
            while ((line = br.readLine()) != null) {
                logger.println(line + "\n");
            }
            p.waitFor();
        } catch (RuntimeException e) {
            throw e;
        }
    }
}
