package com.ef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.data.repository.CrudRepository;
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

	public static void main(String[] args) {
		SpringApplication.run(Parser.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		log.info("EXECUTING : command line runner");
		for (int i = 0; i < args.length; ++i) {
			log.info("args[{}]: {}", i, args[i]);
		}
	}

	/**
	 * Ingest log file from configure location, check if log exists in database,
	 * while line number not match do the heavy lifting job
	 * 
	 * @param repository
	 * @return
	 */
	@Bean
	public CommandLineRunner ingestLogs(LogEntityRepository repository) {
		return (args) -> {
			log.info(String.format("EXECUTING : CommandLineRunner ingestLogs"));
			Supplier<Stream<String>> logSupplier = () -> {
				try {
					return Files.lines(logFilePath);// .skip(1000).limit(1000);
				} catch (IOException e) {
					log.error("can't find log file!", e);
				}
				return null;
			};

			if (null != logSupplier && logSupplier.get().count() != repository.count()) {
				repository.deleteAll();
				logSupplier.get().forEach(processor::persist);
			}
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
	private LogEntityRepository repo;
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
				log.debug(String.format("Saving log entity:%s", ToStringBuilder.reflectionToString(entity)));
				repo.save(entity);
			} catch (Exception e) {
				log.warn(String.format("Fail to ingest log entity with input:%s", line), e);
			}
		});
	}
}

interface LogEntityRepository extends CrudRepository<LogEntity, Long> {

}
