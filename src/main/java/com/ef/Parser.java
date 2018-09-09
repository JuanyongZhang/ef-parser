package com.ef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@SpringBootApplication
public class Parser implements CommandLineRunner {
	private Logger log = LoggerFactory.getLogger(this.getClass());

	@Value("${application.log-file-path}")
	private Path logFilePath;
	@Autowired
	private LogProccessor processor;

	enum Duration {
		hourly, daily
	}

	public static void main(String[] args) {
		SpringApplication.run(Parser.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
//		--accesslog=/path/to/file --startDate=2017-01-01.13:00:00 --duration=hourly --threshold=100
		String accesslog = "./data/access.log";
		final Duration duration = Duration.valueOf("hourly");
		LocalDateTime startTime = LocalDateTime.parse("2017-01-01.13:00:00",
				DateTimeFormatter.ofPattern("yyyy-MM-dd.HH:mm:ss"));
		LocalDateTime endTime = null;
		switch (duration) {
		case hourly:
			endTime = startTime.plusHours(1);
			break;
		case daily:
			endTime = startTime.plusDays(1);
			break;
		}
		if (null == endTime) {
			throw new IllegalArgumentException(
					String.format("--durtion needs to be one of: %s", Arrays.toString(Duration.values())));
		}

		long threshold = 100;
		processor.logFrequentHitIPs(startTime, endTime, threshold);
		log.info("EXECUTING : command line runner");
		for (int i = 0; i < args.length; ++i) {
			log.info("args[{}]: {}", i, args[i]);
		}
	}

	/**
	 * Ingest log file from configure location, check if log exists in database,
	 * while line number not match do the heavy lifting job
	 * 
	 * @param logRepo, blockedIpRepo
	 * @return
	 */
	@Bean
	public CommandLineRunner ingestLogs(LogEntityRepository logRepo, BlockedIpRepository blockedIpRepo) {
		return (args) -> {
			log.info("EXECUTING : CommandLineRunner ingestLogs");
			Supplier<Stream<String>> logSupplier = () -> {
				try {
					return Files.lines(logFilePath);// .skip(1000).limit(1000);
				} catch (IOException e) {
					log.error("can't find log file!", e);
				}
				return null;
			};

			if (null != logSupplier && logSupplier.get().count() != logRepo.count()) {
				logRepo.deleteAll();
				logSupplier.get().forEach(processor::persist);
			}

			blockedIpRepo.deleteAll();
		};
	}

}

@org.springframework.context.annotation.Configuration
class Config implements AsyncConfigurer {
	private Logger log = LoggerFactory.getLogger(this.getClass());

	@Value("${application.async.core-pool-size:5}")
	private int corePoolSize;

	@Bean(name = "taskExecutor")
	public ThreadPoolTaskExecutor getAsyncExecutor() {
		log.info("Creating Async Task Executor");
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setThreadNamePrefix("log-mointor-executor-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		return executor;
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return new SimpleAsyncUncaughtExceptionHandler();
	}
}

@Service
class LogProccessor {
	private Logger log = LoggerFactory.getLogger(this.getClass());
	@Autowired
	private LogEntityRepository logRepo;
	@Autowired
	private BlockedIpRepository blockedIpRepo;
	@Autowired
	private ThreadPoolTaskExecutor taskExecutor;

	/**
	 * Multi threading the database bulk loading function and jdbc batch committing
	 * 
	 * @param line
	 */
	public void persist(String line) {
		taskExecutor.execute(() -> {
			String[] parts = StringUtils.split(line, "|");
			try {
				LogEntity entity = new LogEntity(parts[0], parts[1], parts[2], parts[3], parts[4]);
				log.debug("Saving log entity:{}", ToStringBuilder.reflectionToString(entity));
				logRepo.save(entity);
			} catch (Exception e) {
				log.warn("Fail to ingest log entity with input:{}", line);
			}
		});
	}

	public void logFrequentHitIPs(final LocalDateTime startTime, final LocalDateTime endTime, final long threshold) {
		List<Object[]> results = logRepo.findIpHitsOver(startTime, endTime, threshold);
		results.forEach(it -> {
			BlockedIp blockedIp = new BlockedIp((String) it[0], (Long) it[1], startTime, endTime, threshold);
			log.info("Adding blocked ip[{}]: {}", blockedIp.getIp(), blockedIp.getComments());
			blockedIpRepo.save(blockedIp);
		});
	}
}

interface LogEntityRepository extends CrudRepository<LogEntity, Long> {
	@Query("SELECT log.ip, COUNT(log) FROM LogEntity log "
			+ "WHERE log.timestamp >= :startTime AND log.timestamp <= :endTime "
			+ "GROUP BY log.ip HAVING COUNT(log) > :threshold")
	public List<Object[]> findIpHitsOver(@Param("startTime") LocalDateTime startTime,
			@Param("endTime") LocalDateTime endTime, @Param("threshold") long threshold);
}

interface BlockedIpRepository extends CrudRepository<BlockedIp, Long> {
}
