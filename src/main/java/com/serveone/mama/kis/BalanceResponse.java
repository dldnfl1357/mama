package com.serveone.mama.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BalanceResponse(
        @JsonProperty("rt_cd") String rtCd,
        @JsonProperty("msg_cd") String msgCd,
        @JsonProperty("msg1") String msg,
        @JsonProperty("output1") List<Holding> holdings,
        @JsonProperty("output2") List<Summary> summaries
) {
    public boolean isSuccess() {
        return "0".equals(rtCd);
    }

    public long deposit() {
        if (summaries == null || summaries.isEmpty() || summaries.get(0).dncaTotAmt() == null) {
            return 0L;
        }
        return Long.parseLong(summaries.get(0).dncaTotAmt());
    }

    public Map<String, Integer> holdingsByTicker() {
        Map<String, Integer> out = new HashMap<>();
        if (holdings == null) return out;
        for (Holding h : holdings) {
            if (h.pdno() == null || h.hldgQty() == null) continue;
            int qty = Integer.parseInt(h.hldgQty());
            out.merge(h.pdno(), qty, Integer::sum);
        }
        return out;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Holding(
            @JsonProperty("pdno") String pdno,
            @JsonProperty("hldg_qty") String hldgQty
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            @JsonProperty("dnca_tot_amt") String dncaTotAmt
    ) {}
}
