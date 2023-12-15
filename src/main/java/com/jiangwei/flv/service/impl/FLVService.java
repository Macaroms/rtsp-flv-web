package com.jiangwei.flv.service.impl;


import com.google.common.collect.Lists;
import com.jiangwei.flv.factories.Converter;
import com.jiangwei.flv.factories.ConverterFactories;
import com.jiangwei.flv.service.IFLVService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author jiangwei97@aliyun.com
 * @since 2023/12/11 9:42
 */
@Slf4j
@Service
public class FLVService implements IFLVService, DisposableBean {

    private static final ConcurrentHashMap<String, Converter> converters = new ConcurrentHashMap<>();

    private static ThreadPoolExecutor executor;

    static {
        executor = new ThreadPoolExecutor(
                8,
                64,
                60,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Override
    public String getUrl(String channel) {
        String host = "rtsp://127.0.0.1:8554/";
        if (StringUtils.isBlank(channel)) {
            return "";
        }
        return host + channel;
    }

    @Override
    public void open(String url, HttpServletResponse response, HttpServletRequest request) {
        String key = md5(url);
        AsyncContext async = request.startAsync();
        async.setTimeout(0);
        if (converters.containsKey(key)) {
            Converter c = converters.get(key);
            try {
                c.addOutputStreamEntity(key, async);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                throw new IllegalArgumentException(e.getMessage());
            }
        } else {
            List<AsyncContext> outs = Lists.newArrayList();
            outs.add(async);
            ConverterFactories c = new ConverterFactories(url, key, converters, outs);
            // c.start();
            try {
                executor.submit(c);
                converters.put(key, c);
            } catch (RejectedExecutionException e) {
                System.out.println("暂无可用线程");
            }
        }
        response.setContentType("video/x-flv");
        response.setHeader("Connection", "keep-alive");
        response.setStatus(HttpServletResponse.SC_OK);
        try {
            response.flushBuffer();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public String md5(String plainText) {
        StringBuilder buf = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            byte b[] = md.digest();
            int i;
            buf = new StringBuilder("");
            for (int offset = 0; offset < b.length; offset++) {
                i = b[offset];
                if (i < 0)
                    i += 256;
                if (i < 16)
                    buf.append("0");
                buf.append(Integer.toHexString(i));
            }
        } catch (NoSuchAlgorithmException e) {
            log.error(e.getMessage(), e);
        }
        return buf.toString();
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
