package com.jiangwei.flv.factories;

import com.alibaba.fastjson.util.IOUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.coyote.CloseNowException;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.springframework.scheduling.annotation.Async;

import javax.servlet.AsyncContext;
import javax.servlet.ServletOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;

/**
 * javacv转包装<br/>
 * 无须转码，更低的资源消耗，更低的延迟<br/>
 * 确保流来源视频H264格式,音频AAC格式
 * 
 * @author gc.x
 */
@Slf4j
public class ConverterFactories implements Converter, Runnable {
	public volatile boolean runing = true;
	/**
	 * 读流器
	 */
	private FFmpegFrameGrabber grabber;
	/**
	 * 转码器
	 */
	private FFmpegFrameRecorder recorder;
	/**
	 * 转FLV格式的头信息<br/>
	 * 如果有第二个客户端播放首先要返回头信息
	 */
	private byte[] headers;
	/**
	 * 保存转换好的流
	 */
	private ByteArrayOutputStream stream;
	/**
	 * 流地址，h264,aac
	 */
	private String url;
	/**
	 * 流输出
	 */
	private List<AsyncContext> outEntitys;

	/**
	 * key用于表示这个转换器
	 */
	private String key;

	/**
	 * 转换队列
	 */
	private Map<String, Converter> factories;

	public ConverterFactories(String url, String key, Map<String, Converter> factories, List<AsyncContext> outEntitys) {
		this.url = url;
		this.key = key;
		this.factories = factories;
		this.outEntitys = outEntitys;
	}

	@Override
	public void run() {
		boolean isCloseGrabberAndResponse = true;
		try {
			grabber = new FFmpegFrameGrabber(url);
			if ("rtsp".equals(url.substring(0, 4))) {
				grabber.setOption("rtsp_transport", "tcp");
				grabber.setOption("stimeout", "5000000");
			}
			grabber.start();
			if (avcodec.AV_CODEC_ID_H264 == grabber.getVideoCodec()
					&& (grabber.getAudioChannels() == 0 || avcodec.AV_CODEC_ID_AAC == grabber.getAudioCodec())) {
				log.info("this url:{} converterFactories start", url);
				// 来源视频H264格式,音频AAC格式
				// 无须转码，更低的资源消耗，更低的延迟
				stream = new ByteArrayOutputStream();
				recorder = new FFmpegFrameRecorder(stream, grabber.getImageWidth(), grabber.getImageHeight(),
						grabber.getAudioChannels());
				recorder.setInterleaved(true);
				recorder.setVideoOption("preset", "ultrafast");
				recorder.setVideoOption("tune", "zerolatency");
				recorder.setVideoOption("crf", "25");
				recorder.setFrameRate(grabber.getFrameRate());
				recorder.setSampleRate(grabber.getSampleRate());
				if (grabber.getAudioChannels() > 0) {
					recorder.setAudioChannels(grabber.getAudioChannels());
					recorder.setAudioBitrate(grabber.getAudioBitrate());
					recorder.setAudioCodec(grabber.getAudioCodec());
				}
				recorder.setFormat("flv");
				recorder.setVideoBitrate(grabber.getVideoBitrate());
				recorder.setVideoCodec(grabber.getVideoCodec());
				recorder.start(grabber.getFormatContext());
				if (headers == null) {
					headers = stream.toByteArray();
					stream.reset();
					writeResponse(headers);
				}
				int nullNumber = 0;
                long startTime = System.currentTimeMillis();
                long videoTS = 0;
                long lastDTS = 0;
				while (runing) {
					AVPacket packet = grabber.grabPacket();
					if (packet != null) {
                        if (packet.pts() == AV_NOPTS_VALUE) {
                            if (packet.dts() != AV_NOPTS_VALUE) {
                                packet.pts(packet.dts());
                                lastDTS = packet.dts();
                            } else {
                                packet.pts(lastDTS + 1);
                                packet.dts(packet.pts());
                                lastDTS = packet.pts();
                            }
                        } else {
                            if (packet.dts() != AV_NOPTS_VALUE) {
                                if (packet.dts() < lastDTS) {
                                    packet.dts(lastDTS + 1);
                                }
                            } else {
                                packet.dts(packet.pts());
                            }
                            lastDTS = packet.dts();
                        }
                        if (packet.pts() < packet.dts()) {
                            packet.pts(packet.dts());
                        }
                        videoTS = 1000 * (System.currentTimeMillis() - startTime);
                        if (videoTS < 0 || packet.dts() < 0 || packet.pts() < 0) {
                            continue;
                        }
                        if (videoTS > recorder.getTimestamp()) {
                            recorder.setTimestamp(videoTS);
                        }
						try {
							recorder.recordPacket(packet);
						} catch (Exception e) {
                            log.error(e.getMessage());
						}
						if (stream.size() > 0) {
							byte[] b = stream.toByteArray();
							stream.reset();
							writeResponse(b);
							if (outEntitys.isEmpty()) {
								log.info("没有输出退出");
								break;
							}
						}
						avcodec.av_packet_unref(packet);
					} else {
						nullNumber++;
						if (nullNumber > 200) {
							break;
						}
					}
					Thread.sleep(5);
				}
			} else {
				isCloseGrabberAndResponse = false;
				// 需要转码为视频H264格式,音频AAC格式
				ConverterTranFactories c = new ConverterTranFactories(url, key, factories, outEntitys, grabber);
				factories.put(key, c);
				c.start();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		} finally {
			closeConverter(isCloseGrabberAndResponse);
			completeResponse(isCloseGrabberAndResponse);
			log.info("this url:{} converterFactories exit", url);
		}
	}

	/**
	 * 输出FLV视频流
	 */
	public void writeResponse(byte[] b) {
		Iterator<AsyncContext> it = outEntitys.iterator();
		while (it.hasNext()) {
			AsyncContext o = it.next();
			try {
				ServletOutputStream outputStream = o.getResponse().getOutputStream();
				outputStream.write(b);
			} catch (ClientAbortException | CloseNowException e) {
				log.info("移除一个输出");
				it.remove();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 退出转换
	 */
	public void closeConverter(boolean isCloseGrabberAndResponse) {
		if (isCloseGrabberAndResponse) {
			IOUtils.close(grabber);
			factories.remove(this.key);
		}
		IOUtils.close(recorder);
		IOUtils.close(stream);
	}

	/**
	 * 关闭异步响应
	 */
	public void completeResponse(boolean isCloseGrabberAndResponse) {
		if (isCloseGrabberAndResponse) {
            for (AsyncContext o : outEntitys) {
                o.complete();
            }
		}
	}

	@Override
	public String getKey() {
		return this.key;
	}

	@Override
	public String getUrl() {
		return this.url;
	}

	@Override
	public void addOutputStreamEntity(String key, AsyncContext entity) throws IOException {
		if (headers == null) {
			outEntitys.add(entity);
		} else {
			entity.getResponse().getOutputStream().write(headers);
			entity.getResponse().getOutputStream().flush();
			outEntitys.add(entity);
		}
	}

	@Override
	public void exit() {
		this.runing = false;
//		try {
//			this.join();
//		} catch (Exception e) {
//			log.error(e.getMessage(), e);
//		}
	}

}
