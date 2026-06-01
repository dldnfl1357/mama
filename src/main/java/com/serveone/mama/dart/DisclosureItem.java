package com.serveone.mama.dart;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DisclosureItem(
        String corpCode,
        String corpName,
        String stockCode,
        String corpCls,
        String reportNm,
        String rceptNo,
        String flrNm,
        String rceptDt,
        String rm
) {}
