package ai.univs.gate.shared.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class CustomPageable {

    public static Pageable of(int page, int size) {
        if (page > 0) {
            return PageRequest.of(page - 1, size);
        }
        return PageRequest.of(page, size);
    }

    public static Pageable of(int page, int size, Sort.Direction direction, String propName) {
        if (page > 0) {
            return PageRequest.of(page - 1, size, direction, propName);
        }
        return PageRequest.of(page, size, direction, propName);
    }
}
