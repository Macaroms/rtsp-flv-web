package com.jiangwei.flv.controller;

import com.jiangwei.flv.service.IFLVService;
import io.swagger.annotations.Api;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

/**
 * @author jiangwei97@aliyun.com
 * @since 2023/12/11 10:42
 */
@Api(tags = "flv")
@RestController
@RequestMapping("/flv")
public class FLVController {

    @Resource
    private IFLVService service;

    @GetMapping(value = "/{channel}")
    public void open(@PathVariable(value = "channel") String channel,
                      HttpServletResponse response,
                      HttpServletRequest request) {
        String url = service.getUrl(channel);
		if(StringUtils.isNotBlank(url)){
            service.open(url, response, request);
		}
    }

    @GetMapping(value = "/test")
    public String test() {
        return "test: " + LocalDateTime.now();
    }
}
