package com.mmh.WatchDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;

public class Session {
	private static final String PROPERTY_FILE = "/home/ec2-user/config.properties";
	private static final Region REGION = Region.EU_WEST_3;


    private static Logger LOGGER = LoggerFactory.getLogger(Session.class);
	
	private int threadNumber;
	private String s3Name;
	
	private ConcurrentHashMap<Path, String> treatedMap = new ConcurrentHashMap<>();
	private S3Client s3;
	CloudWatchClient cw;
	
	public Session() {
		init();
	}

	protected void init() {
		loadProperties();
		
		s3 = S3Client.builder().region(REGION).build();
        cw = CloudWatchClient.builder().build();
	}

	private void loadProperties() {
		try (InputStream input = new FileInputStream(PROPERTY_FILE)) {
			Properties prop = new Properties();
			prop.load(input);
			threadNumber = Integer.parseInt(prop.getProperty("thread_number"));
			this.s3Name = prop.getProperty("s3_name");
		} catch (IOException e) {
			LOGGER.error("Could not read property file", e);
		}
	}

	public int getThreadNumber() {
		return threadNumber;
	}

	public String getS3Name() {
		return s3Name;
	}
	
	public void startFileHandling(Path path) {
		treatedMap.put(path, "treated");
	}
	
	public void stopFileHandling(Path path) {
		treatedMap.remove(path);
	}
	
	public boolean isFileHandled(Path path) {
		return treatedMap.containsKey(path);
	}
	
	public S3Client getS3Client() {
		return s3;
	}
	
	public CloudWatchClient getCloudWatchClient() {
		return cw;
	}
}
