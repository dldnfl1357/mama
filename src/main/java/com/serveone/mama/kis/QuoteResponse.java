package com.serveone.mama.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuoteResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg,
        @JsonProperty("output") Output output
) {
    public boolean isSuccess() {
        return "0".equals(rtCd);
    }

    public long currentPrice() {
        if (output == null || output.stckPrpr() == null) {
            throw new KisException("KIS quote response missing stck_prpr");
        }
        return Long.parseLong(output.stckPrpr());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("stck_prpr") String stckPrpr
    ) {}
}
