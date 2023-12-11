package com.jiangwei.flv.factories;

import javax.servlet.AsyncContext;
import java.io.IOException;

public interface Converter {

	/**
	 * 获取该转换的key
	 */
	String getKey();

	/**
	 * 获取该转换的url
	 */
	String getUrl();

	/**
	 * 添加一个流输出
	 */
	void addOutputStreamEntity(String key, AsyncContext entity) throws IOException;

	/**
	 * 退出转换
	 */
	void exit();

	/**
	 * 启动
	 */
	void start();

}
