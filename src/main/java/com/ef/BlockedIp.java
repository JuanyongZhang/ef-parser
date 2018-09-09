package com.ef;

import java.time.LocalDateTime;

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
public class BlockedIp {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;
	private String ip;
	private String comments;

	public BlockedIp() {
		super();
	}

	public BlockedIp(String ip, long count, LocalDateTime startTime, LocalDateTime endTime, long threshold) {
		super();
		this.ip = ip;
		this.comments = String.format("ip[%s] is blocked! Because it made [%s over %s(allowed)] requests during %s to %s", ip,
				count, threshold, startTime, endTime);
	}
	public Long getId() {
		return id;
	}

	public String getIp() {
		return ip;
	}

	public String getComments() {
		return comments;
	}
}
