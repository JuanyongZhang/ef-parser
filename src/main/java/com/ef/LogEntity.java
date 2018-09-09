package com.ef;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * 2017-01-01 00:00:11.763|192.168.234.82|"GET / HTTP/1.1"|200|"swcd (unknown
 * version) CFNetwork/808.2.16 Darwin/15.6.0" 2017-01-01
 * 00:01:08.028|192.168.27.46|"GET / HTTP/1.1"|200|"Mozilla/5.0 (Windows NT
 * 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.91
 * Safari/537.36"
 * 
 * @author Juanyong
 *
 */
@Entity
public class LogEntity {
	final static DateTimeFormatter date_pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;
	private LocalDateTime timestamp;
	private String ip;
	private String request;
	private String responseCode;
	private String clientInfo;

	public LogEntity() {
		super();
	}

	public LogEntity(String timestamp, String ip, String request,
			String responseCode, String clientInfo) throws ParseException {
		this.timestamp = LocalDateTime.parse(timestamp, date_pattern);
		this.ip = ip;
		this.request = request;
		this.responseCode = responseCode;
		this.clientInfo = clientInfo;
	}

	public Long getId() {
		return id;
	}

	public LocalDateTime getTimestamp() {
		return timestamp;
	}

	public String getIp() {
		return ip;
	}

	public String getRequest() {
		return request;
	}

	public String getResponseCode() {
		return responseCode;
	}

	public String getClientInfo() {
		return clientInfo;
	}

}
