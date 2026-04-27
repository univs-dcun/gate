package ai.univs.gate.shared.usecase.result;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public record CustomPageResult(
        int pageSize,
        int page,
        Long totalElements,
        int totalPages,
        Long totalCount
) {

    public static CustomPageResult from(Pageable pageable, Long totalElements, int totalPages, Long totalCount) {
        return new CustomPageResult(
                pageable.getPageSize(),
                pageable.getPageNumber() + 1,
                totalElements,
                totalPages,
                totalCount);
    }

    public static <T> CustomPageResult of(Page<T> pageContents, Long totalCount) {
        Pageable pageable = pageContents.getPageable();

        if (pageable.isUnpaged()) {
            return new CustomPageResult(10, 1, 0L, 0, totalCount);
        }

        return new CustomPageResult(
                pageable.getPageSize(),
                pageable.getPageNumber() + 1,
                pageContents.getTotalElements(),
                pageContents.getTotalPages(),
                totalCount);
    }
}