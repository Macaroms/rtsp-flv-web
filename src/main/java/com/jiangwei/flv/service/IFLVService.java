package com.jiangwei.flv.service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface IFLVService {

	String getUrl(String channel);

	/**
	 * 打开一个流地址
	 */
	void open(String url, HttpServletResponse response, HttpServletRequest request);

}
