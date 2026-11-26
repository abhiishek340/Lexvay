package com.lexvay.document;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class FileStoreConfig {

    @Bean
    FileStore fileStore(@Value("${lexvay.filestore.type:local}") String type,
                        @Value("${lexvay.filestore.path:./data/files}") String path,
                        @Value("${lexvay.filestore.s3-bucket:}") String bucket) {
        return switch (type) {
            case "local" -> new LocalFileStore(Path.of(path));
            case "s3" -> new S3FileStore(S3Client.create(), bucket);
            default -> throw new IllegalArgumentException("Unknown LEXVAY_FILESTORE value: " + type);
        };
    }
}
