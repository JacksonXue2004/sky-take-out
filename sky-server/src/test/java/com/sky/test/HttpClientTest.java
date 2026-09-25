package com.sky.test;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

//@SpringBootTest
public class HttpClientTest {

    @Test
    public void testGET() throws Exception{

        CloseableHttpClient httpClient = HttpClients.createDefault();


        HttpGet httpGet = new HttpGet("http://localhost:8080/user/shop/status");


        CloseableHttpResponse response = httpClient.execute(httpGet);


        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("Operation" + statusCode);

        HttpEntity entity = response.getEntity();
        String body = EntityUtils.toString(entity);
        System.out.println("Operation" + body);


        response.close();
        httpClient.close();
    }


    @Test
    public void testPOST() throws Exception{

        CloseableHttpClient httpClient = HttpClients.createDefault();


        HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("username","admin");
        jsonObject.put("password","123456");

        StringEntity entity = new StringEntity(jsonObject.toString());

        entity.setContentEncoding("utf-8");

        entity.setContentType("application/json");
        httpPost.setEntity(entity);


        CloseableHttpResponse response = httpClient.execute(httpPost);


        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("Operation" + statusCode);

        HttpEntity entity1 = response.getEntity();
        String body = EntityUtils.toString(entity1);
        System.out.println("Operation" + body);


        response.close();
        httpClient.close();
    }
}
