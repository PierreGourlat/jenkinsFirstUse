package com.mmh.WatchDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class FileManagement implements Runnable {
	private static Logger LOGGER = LoggerFactory.getLogger(FileManagement.class);
	private static final Dimension DIMENSION = Dimension.builder().name("FILE METRICS").value("FILES").build();

	private Path child;
	private List<String> lines;
	private Session session;

	public FileManagement(Session session, Path child, List<String> lines) {
		this.session = session;
		this.child = child;
		this.lines = lines;
	}

	public void run() {
		int valid_file_counter = 0;
		int failed_file_counter = 0;
		CloudWatchClient cw = CloudWatchClient.builder().build();
		LOGGER.info("Upload to S3 is starting.");
		try {
			for (String line : lines) {
				if (!line.equals("FIN,FIN,FIN")) {
					String[] elements = line.split(",");
					String bigFileName = elements[1];
					Path absolutePath = child.getParent();
					String remoteChecksum = elements[0].toUpperCase();
					try {
						Path bigFilePath = absolutePath.resolve(bigFileName);
						HashCode hash = com.google.common.io.Files.asByteSource(bigFilePath.toFile())
								.hash(Hashing.md5());
						String localChecksum = hash.toString().toUpperCase();
						if (localChecksum.equals(remoteChecksum)) {
							session.getS3Client().putObject(
									PutObjectRequest.builder()
									.bucket(session.getS3Name())
									.key(elements[2] + bigFileName)
									.build(),
									bigFilePath);
							LOGGER.info(bigFileName + " pushed to S3 successfully.");
							valid_file_counter++;
						} else {
							LOGGER.error(bigFileName + " : checksum mismatch !");
							failed_file_counter++;
						}
						Files.delete(bigFilePath);
						LOGGER.info(bigFileName + " has been deleted.");
					} catch (Exception e) {
						failed_file_counter++;
						LOGGER.error("Error message", e);
					}
				}
			}
			Files.delete(child);
			LOGGER.info(child.toString() + " has been deleted.");
			session.stopFileHandling(child);
			MetricDatum datum = MetricDatum.builder()
					.metricName("UPLOADED")
					.unit(StandardUnit.NONE)
					.value((double) valid_file_counter)
					.dimensions(DIMENSION)
					.build();
			MetricDatum datum2 = MetricDatum.builder()
					.metricName("FAILED")
					.unit(StandardUnit.NONE)
					.value((double) failed_file_counter)
					.dimensions(DIMENSION)
					.build();
			PutMetricDataRequest request = PutMetricDataRequest.builder()
					.namespace("EDGE/NODE")
					.metricData(datum, datum2)
					.build();
			PutMetricDataResponse response = cw.putMetricData(request);
		} catch (Exception e) {
			LOGGER.error("Error message", e);
		}

	}
}
