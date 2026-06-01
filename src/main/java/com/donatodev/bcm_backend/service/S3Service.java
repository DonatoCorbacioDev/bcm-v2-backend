package com.donatodev.bcm_backend.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Service
public class S3Service {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    public String uploadDocument(Long orgId, Long contractId, String fileName,
                                 String contentType, byte[] content) {
        String s3Key = String.format("contracts/%d/%d/%s-%s",
                orgId != null ? orgId : 0L, contractId, UUID.randomUUID(), fileName);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));

        return s3Key;
    }

    public String generatePresignedUrl(String s3Key) {
        return s3Presigner.presignGetObject(r -> r
                        .signatureDuration(Duration.ofMinutes(15))
                        .getObjectRequest(gr -> gr.bucket(bucketName).key(s3Key)))
                .url()
                .toString();
    }

    public void deleteDocument(String s3Key) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build());
    }
}
