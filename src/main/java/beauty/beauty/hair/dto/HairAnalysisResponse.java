package beauty.beauty.hair.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HairAnalysisResponse {

    private String faceShape;
    private List<String> faceFeatures;
    private String referenceStyle;
    private Compatibility compatibility;
    private List<StyleRecommendation> recommendations;
    private String stylingTips;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Compatibility {
        private int score;
        private String verdict;
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StyleRecommendation {
        private String style;
        private String category;
        private String reason;
        private int suitability;
    }
}
