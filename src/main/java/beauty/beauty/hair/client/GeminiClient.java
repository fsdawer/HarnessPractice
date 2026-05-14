package beauty.beauty.hair.client;

import beauty.beauty.global.exception.CustomException;
import beauty.beauty.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiClient {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent";

    private static final String PROMPT = """
            첫 번째 이미지는 사용자의 얼굴 사진이고, 두 번째 이미지는 사용자가 원하는 헤어스타일 레퍼런스 사진입니다.
            반드시 아래 JSON 형식으로만 응답하세요.

            {
              "faceShape": "타원형|둥근형|각진형|하트형|직사각형|인식불가 중 하나",
              "faceFeatures": ["특징1", "특징2"],
              "referenceStyle": "레퍼런스 사진의 헤어스타일명",
              "compatibility": {
                "score": 85,
                "verdict": "잘 어울립니다|보통입니다|잘 어울리지 않습니다 중 하나",
                "reason": "이 얼굴형과 레퍼런스 헤어스타일의 궁합 분석"
              },
              "recommendations": [
                {
                  "style": "구체적인 스타일명",
                  "category": "커트|일반펌|열펌|염색|클리닉 중 하나",
                  "reason": "이 얼굴형에 어울리는 이유",
                  "suitability": 90
                }
              ],
              "stylingTips": "전반적인 헤어 스타일링 조언"
            }

            compatibility.score는 1-100 사이 숫자.
            recommendations는 3개, suitability는 1-100 사이 숫자, 내림차순 정렬.
            얼굴이 없으면 faceShape을 "인식불가"로 설정하고 나머지 필드는 빈 값으로 응답.
            """;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String analyze(String faceBase64, String faceMime, String styleBase64, String styleMime) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(
                                    Map.of("inline_data", Map.of("mime_type", faceMime, "data", faceBase64)),
                                    Map.of("inline_data", Map.of("mime_type", styleMime, "data", styleBase64)),
                                    Map.of("text", PROMPT)
                            )
                    )),
                    "generationConfig", Map.of("response_mime_type", "application/json")
            );

            String requestJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("[Gemini] API 오류 — status={}, body={}", response.statusCode(), response.body());
                throw new CustomException(ErrorCode.HAIR_ANALYSIS_FAILED);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();
            // 마크다운 코드블록 제거 (```json ... ```)
            if (text.startsWith("```")) {
                text = text.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }
            return text;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Gemini] 요청 실패", e);
            throw new CustomException(ErrorCode.HAIR_ANALYSIS_FAILED);
        }
    }
}
