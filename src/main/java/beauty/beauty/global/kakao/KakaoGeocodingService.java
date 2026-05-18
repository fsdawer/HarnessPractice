package beauty.beauty.global.kakao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class KakaoGeocodingService {

    @Value("${kakao.rest-api-key}")
    private String restApiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 좌표 → 구(region_2depth_name) 변환. 실패 시 null 반환
    public String reverseGeocode(double lat, double lng) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=" + lng + "&y=" + lat))
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode documents = objectMapper.readTree(response.body()).path("documents");

            if (documents.isArray() && !documents.isEmpty()) {
                return documents.get(0).path("region_2depth_name").asText(null);
            }
        } catch (Exception e) {
            log.warn("[ReverseGeocode] 좌표 변환 실패 — lat={}, lng={}, error={}", lat, lng, e.getMessage());
        }
        return null;
    }

    // 주소 → [latitude, longitude] 변환. 실패 시 null 반환
    public double[] geocode(String address) {
        try {
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://dapi.kakao.com/v2/local/search/address.json?query=" + encoded))
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode documents = root.path("documents");

            if (documents.isArray() && !documents.isEmpty()) {
                JsonNode first = documents.get(0);
                double lng = Double.parseDouble(first.path("x").asText());
                double lat = Double.parseDouble(first.path("y").asText());
                return new double[]{lat, lng};
            }
        } catch (Exception e) {
            log.warn("[Geocoding] 주소 변환 실패 — address={}, error={}", address, e.getMessage());
        }
        return null;
    }
}
