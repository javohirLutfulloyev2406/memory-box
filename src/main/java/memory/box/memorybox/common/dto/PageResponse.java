package memory.box.memorybox.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** A plain, stable JSON shape for paginated responses, decoupled from Spring Data's {@code Page}. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
