package com.serveone.mama.kis;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.UpperSnakeCaseStrategy.class)
public record CancelRequest(
        String cano,
        String acntPrdtCd,
        String krxFwdgOrdOrgno,
        String orgnOdno,
        String ordDvsn,
        String rvseCnclDvsnCd,
        String ordQty,
        String ordUnpr,
        String qtyAllOrdYn
) {}
