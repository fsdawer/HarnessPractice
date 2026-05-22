package beauty.beauty.stylist.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class SliceResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final boolean hasNext;
    private final long totalElements;

    public SliceResponse(List<T> content, int page, int size, long totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.hasNext = (long) (page + 1) * size < totalElements;
    }
}
