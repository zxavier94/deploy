package hudson.plugins.deploy.tomcat;

import java.io.File;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.DefaultElement;

/**
 * @Author: margo
 * @Date: 2019/5/22 14:39
 * @Description:
 */
public class TomcatUtil {

    /**
     * 获取tomcat端口号
     * @param tomcatHome tomcat目录
     * @return
     */
    public static String getTomcatPort(String tomcatHome) throws DocumentException {
        String tomcatCfg = tomcatHome + File.separator + "conf" + File.separator + "server.xml";
        return getPort(tomcatCfg);
    }

    public static String getPort(String filePath) throws DocumentException {
//        Document document = DocumentHelper.parseText(tomcatCfg);
        SAXReader reader = new SAXReader();
        Document document = reader.read(new File(filePath));
        Node node = document.selectSingleNode("/Server/Service/Connector[@protocol='HTTP/1.1']");
        DefaultElement element = (DefaultElement) node;
        return element.attributeValue("port");
    }

    /**
     * 测试发送GET请求
     */
    public static boolean testURL(String url) {
        // 1. 创建一个默认的client实例
        CloseableHttpClient client = HttpClients.createDefault();

        try {
            // 2. 创建一个httpget对象
            HttpGet httpGet = new HttpGet(url);
            System.out.println("executing GET request " + httpGet.getURI());
            // 3. 执行GET请求并获取响应对象
            CloseableHttpResponse resp = client.execute(httpGet);

            try {
                // 4. 获取响应体
                HttpEntity entity = resp.getEntity();

                // 5. 打印响应状态
                int statusCode = resp.getStatusLine().getStatusCode();

                return statusCode == 200;
                // 6. 打印响应长度和响应内容
//                if (null != entity) {
//                    System.out.println("Response content length = " + entity.getContentLength());
//                    System.out.println("Response content is:\n" + EntityUtils.toString(entity));
//                }

            } finally {
                // 7. 无论请求成功与否都要关闭resp
                resp.close();
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 8. 最终要关闭连接，释放资源
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }


    public static void main(String[] args) throws DocumentException {
        String s = getTomcatPort("D:\\apache-tomcat-7.0.82");
        System.out.println(s);
        boolean b = testURL("http://localhost:8888");
        System.out.println("b = " + b);
    }
}
