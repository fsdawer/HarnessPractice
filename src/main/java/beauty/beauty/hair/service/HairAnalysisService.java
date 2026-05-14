package beauty.beauty.hair.service;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import beauty.beauty.hair.client.GeminiClient;
import beauty.beauty.hair.dto.HairAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class HairAnalysisService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public HairAnalysisResponse analyze(MultipartFile face, MultipartFile style) {
        try {
            String faceBase64 = Base64.getEncoder().encodeToString(face.getBytes());
            String faceMime   = face.getContentType()  != null ? face.getContentType()  : "image/jpeg";
            String styleBase64 = Base64.getEncoder().encodeToString(style.getBytes());
            String styleMime   = style.getContentType() != null ? style.getContentType() : "image/jpeg";

            String json = geminiClient.analyze(faceBase64, faceMime, styleBase64, styleMime);
            HairAnalysisResponse result = objectMapper.readValue(json, HairAnalysisResponse.class);

            if ("인식불가".equals(result.getFaceShape())) {
                throw new CustomException(ErrorCode.FACE_NOT_DETECTED);
            }

            return result;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[HairAnalysis] 분석 실패", e);
            throw new CustomException(ErrorCode.HAIR_ANALYSIS_FAILED);
        }
    }
}
