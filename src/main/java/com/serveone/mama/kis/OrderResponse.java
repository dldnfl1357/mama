package com.serveone.mama.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg,
        @JsonProperty("output") Output output
) {
    public boolean isSuccess() {
        return "0".equals(rtCd);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Output(
            @JsonProperty("KRX_FWDG_ORD_ORGNO") String orgNo,
            @JsonProperty("ODNO") String orderNo,
            @JsonProperty("ORD_TMD") String orderTime
    ) {}
}
