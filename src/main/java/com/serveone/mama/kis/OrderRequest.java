package com.serveone.mama.kis;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.UpperSnakeCaseStrategy.class)
public record OrderRequest(
        String cano,
        String acntPrdtCd,
        String pdno,
        String ordDvsn,
        String ordQty,
        String ordUnpr
) {}
