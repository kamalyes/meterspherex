package io.metersphere.autoconfigure;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.utils.LoggerUtil;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;


public class RestTemplateConfig implements WebMvcConfigurer {
    private final static int RETRY_COUNT = 3;
    private final static long RETRY_INTERVAL_TIME = 1000L;
    private final static int MAX_TOTAL = 1000;
    private final static int MAX_PER_ROUTE = 500;
    private final static int CONN_REQUEST_TIMEOUT = 5000;
    private final static int CONNECT_TIMEOUT = 8000;
    private final static int SOCKET_TIMEOUT = 20 * 1000;

    @Bean
    public RestTemplate restTemplate() {
        return setTemplate();
    }

    @Bean
    public RestTemplate restTemplateWithTimeOut() {
        return setTemplate();
    }

    @Bean
    public HttpClient httpClient() {
        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
        //????????????????????????????????????
        connectionManager.setMaxTotal(MAX_TOTAL);
        //????????????maxTotal?????????
        connectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);

        RequestConfig requestConfig = RequestConfig.custom()
                //?????????????????????(response)?????????????????????????????????read timeout
                .setSocketTimeout(SOCKET_TIMEOUT)
                //??????????????????(????????????)?????????????????????????????????connect timeout
                .setConnectTimeout(CONNECT_TIMEOUT)
                //????????????????????????????????????????????????????????????????????????????????????Timeout waiting for connection from pool
                .setConnectionRequestTimeout(CONN_REQUEST_TIMEOUT)
                .build();
        // ????????????
        HttpClientBuilder client = getHttpClientBuilder();
        client.setDefaultRequestConfig(requestConfig);
        // ???????????????
        client.setConnectionManager(connectionManager);
        return client.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private RestTemplate setTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory(httpClient()));
        return restTemplate;
    }

    private HttpClientBuilder getHttpClientBuilder() {
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        // ??????io????????????????????????
        httpClientBuilder.setRetryHandler((IOException exception, int curRetryCount, HttpContext context) -> {
            // curRetryCount ???????????????????????????1??????
            if (curRetryCount > RETRY_COUNT) return false;
            try {
                //????????????
                Thread.sleep(curRetryCount * RETRY_INTERVAL_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
                LogUtil.error(e);
            }
            if (exception instanceof ConnectTimeoutException ||
                    exception instanceof NoHttpResponseException ||
                    exception instanceof ConnectException ||
                    exception instanceof SocketException) {
                LoggerUtil.info("????????????: " + curRetryCount);
                return true;
            }
            return false;
        });
        return httpClientBuilder;
    }

}
