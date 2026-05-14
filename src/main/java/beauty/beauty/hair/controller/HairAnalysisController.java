package beauty.beauty.hair.controller;

import beauty.beauty.hair.dto.HairAnalysisResponse;
import beauty.beauty.hair.service.HairAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/hair-analysis")
@RequiredArgsConstructor
public class HairAnalysisController {

    private final HairAnalysisService hairAnalysisService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HairAnalysisResponse> analyze(
            @RequestParam("face") MultipartFile face,
            @RequestParam("style") MultipartFile style) {
        return ResponseEntity.ok(hairAnalysisService.analyze(face, style));
    }
}
