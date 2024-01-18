package genius00hwan.S3Practice.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileService {
    private final AmazonS3 amazonS3Client;

    //property파일에서 bucket명을 불러오게끔 설정하였다.
    //private String bucket = "버킷명";과 같이 직접 설정해도 무관하다.
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;


    //여러 개의 파일을 동시에 업로드. 생성된 파일의 URL의 List를 반환
    public List<URL> fileUpload(List<MultipartFile> multipartFiles) {
        List<URL> urlList = new ArrayList<>();

        multipartFiles.forEach(multipartFile -> {
            String fileName = makeFileName(multipartFile);
            //key 값으로 사용하기 위한 파일 이름 생성
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentType(multipartFile.getContentType()); //객체(파일)의 메타데이터 설정

            try (InputStream inputStream = multipartFile.getInputStream()) {
                amazonS3Client.putObject(new PutObjectRequest(bucket, fileName, inputStream, objectMetadata)
                        .withCannedAcl(CannedAccessControlList.PublicRead));

            } catch (IOException e) {
                /** 예외 처리 전략에 따라 수정될 수 있음! */
                e.printStackTrace();
                log.info("파일 업로드 실패");
            }

            //bucket으로 부터 key(file명)에 대한 url 정보
            urlList.add(amazonS3Client.getUrl(bucket, fileName));
        });

        return urlList;
    }

    // 모든 파일 명의 List 반환
    public List<String> getFileList() {
        List<String> fileList = new ArrayList<>();

        ListObjectsV2Result result = amazonS3Client.listObjectsV2(bucket);
        List<S3ObjectSummary> objects = result.getObjectSummaries();

        return objects.stream()
                .map(S3ObjectSummary::getKey)
                .collect(Collectors.toList());

    }

    public URL getFile(String fileName) {
        return amazonS3Client.getUrl(bucket, fileName);
    }

    //파일의 S3 내부 진짜 경로로 변경
    public String changeFileKeyPath(String fileName) {
        String fileKey = fileName.replace(
                String.format("https://%s.s3.%s.amazonaws.com/", bucket, amazonS3Client.getRegion()), "");
        return fileKey;
    }

    //파일 삭제
    public void deleteFile(String fileName) {
        try {
            amazonS3Client.deleteObject(new DeleteObjectRequest(bucket, fileName));
            log.info("기존 파일이 정상적으로 삭제되었습니다.");
        } catch (AmazonS3Exception e) {
            e.printStackTrace();
            log.info("기존 파일 삭제에 실패했습니다.");

        }
    }

    // bucketName과 key(파일명)만으로 객체를 이동(복사)시켰다.(기존 객체는 삭제되지 않는다.)
    // 삭제를 하려면 기존 객체 deleteFile 로직 추가하면 됨
    public void moveFile(String destBucket, String sourceFileName, String destFileName) {
        try {
            amazonS3Client.copyObject(bucket, sourceFileName, destBucket, destFileName);
        } catch (AmazonServiceException e) {
            e.printStackTrace();
            log.info("파일 이동에 실패하였습니다.");
        }
    }

    private String makeFileName(MultipartFile multipartFile) {
        String originalName = multipartFile.getOriginalFilename();
        final String ext = originalName.substring(originalName.lastIndexOf("."));
        final String fileName = UUID.randomUUID() + ext;
        return System.getProperty("user.dir") + fileName;
    }

}
