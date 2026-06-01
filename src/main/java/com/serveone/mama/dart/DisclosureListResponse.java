package com.serveone.mama.dart;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisclosureListResponse(
        String status,
        String message,
        Integer pageNo,
        Integer pageCount,
        Integer totalCount,
        Integer totalPage,
        List<DisclosureItem> list
) {
    public boolean isSuccess() {
        return "000".equals(status);
    }
}
