package com.donatodev.bcm_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.textract.TextractClient;

/**
 * Configures AWS SDK v2 clients using the DefaultCredentialsProvider chain.
 * Credentials are resolved automatically from (in order):
 *   1. Environment variables: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
 *   2. ~/.aws/credentials (set via: aws configure)
 *   3. IAM role attached to the running instance (EC2/ECS)
 */
@Configuration
public class AwsConfig {

    @Value("${aws.region:eu-central-1}")
    private String awsRegion;

    @Value("${aws.textract.region:eu-central-1}")
    private String textractRegion;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    public TextractClient textractClient() {
        return TextractClient.builder()
                .region(Region.of(textractRegion))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
