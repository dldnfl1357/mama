package com.serveone.mama.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serveone.mama.dart.entity.DisclosureEntity;
import com.serveone.mama.llm.OpenAiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SignalGenerator {

    private static final int MAX_TOKENS = 512;

    private static final String SYSTEM_PROMPT = """
            당신은 한국 주식 매매 신호를 만드는 분석가입니다.
            입력으로 받은 DART 공시 한 건을 보고 단기 스윙(1~5일) 관점의 매매 의견을 정합니다.
            응답은 반드시 다음 JSON 스키마의 객체 하나로만 출력합니다.
            {
              "ticker": "<6자리 종목코드>",
              "action": "BUY" | "SELL" | "HOLD",
              "confidence": 0.0~1.0 사이 숫자,
              "reasoning": "<한국어 1~2문장 근거>"
            }
            확신이 낮거나 매매에 부적합한 공시는 action="HOLD"로 보고합니다.""";

    private final OpenAiClient openAi;
    private final ObjectMapper mapper;

    public SignalGenerator(OpenAiClient openAi, ObjectMapper mapper) {
        this.openAi = openAi;
        this.mapper = mapper;
    }

    public Signal generate(DisclosureEntity entity) {
        if (entity.stockCode() == null || entity.stockCode().isBlank()) {
            throw new IllegalArgumentException("disclosure has no stock code: rceptNo=" + entity.rceptNo());
        }
        String text = openAi.completeJson(SYSTEM_PROMPT, buildUserPrompt(entity), MAX_TOKENS);
        return parse(text, entity.stockCode());
    }

    private String buildUserPrompt(DisclosureEntity entity) {
        return """
                회사: %s (%s)
                공시명: %s
                접수일: %s
                신고자: %s
                """.formatted(
                entity.corpName(),
                entity.stockCode(),
                entity.reportNm(),
                entity.rceptDt(),
                entity.flrNm()
        );
    }

    private Signal parse(String text, String fallbackTicker) {
        String json = extractJsonObject(text);
        if (json == null) {
            log.warn("no JSON object found in LLM output, falling back to HOLD: {}", abbreviate(text));
            return new Signal(fallbackTicker, Action.HOLD, 0.0, "파싱 실패: JSON 객체 미발견");
        }
        try {
            JsonNode node = mapper.readTree(json);
            String ticker = node.path("ticker").asText(fallbackTicker);
            Action action = parseAction(node.path("action").asText("HOLD"));
            double confidence = clampConfidence(node.path("confidence").asDouble(0.0));
            String reasoning = node.path("reasoning").asText("").trim();
            return new Signal(ticker, action, confidence, reasoning);
        } catch (Exception e) {
            log.warn("failed to parse LLM JSON, falling back to HOLD: {} ({})", e.getMessage(), abbreviate(text));
            return new Signal(fallbackTicker, Action.HOLD, 0.0, "파싱 실패: " + e.getMessage());
        }
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }

    private static Action parseAction(String raw) {
        try {
            return Action.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Action.HOLD;
        }
    }

    private static double clampConfidence(double v) {
        if (Double.isNaN(v)) return 0.0;
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
