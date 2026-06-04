package com.serveone.mama.signal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.serveone.mama.dart.DisclosureItem;
import com.serveone.mama.llm.OpenAiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignalGeneratorTests {

    private OpenAiClient openAi;
    private SignalGenerator generator;

    @BeforeEach
    void setUp() {
        openAi = mock(OpenAiClient.class);
        generator = new SignalGenerator(openAi, new ObjectMapper());
    }

    private static DisclosureItem item() {
        return new DisclosureItem(
                "00126380", "삼성전자", "005930", "Y",
                "주요사항보고서", "20260601000001", "삼성전자", "20260601", null);
    }

    @Test
    void generate_parsesCleanJsonResponse() {
        when(openAi.completeJson(anyString(), anyString(), anyInt())).thenReturn("""
                {"ticker":"005930","action":"BUY","confidence":0.72,"reasoning":"호재성 공시"}
                """);

        Signal signal = generator.generate(item());

        assertThat(signal.ticker()).isEqualTo("005930");
        assertThat(signal.action()).isEqualTo(Action.BUY);
        assertThat(signal.confidence()).isEqualTo(0.72);
        assertThat(signal.reasoning()).isEqualTo("호재성 공시");
    }

    @Test
    void generate_extractsJsonFromMarkdownFencedResponse() {
        when(openAi.completeJson(anyString(), anyString(), anyInt())).thenReturn("""
                답변:
                ```json
                {"ticker":"005930","action":"SELL","confidence":0.4,"reasoning":"악재"}
                ```
                """);

        Signal signal = generator.generate(item());

        assertThat(signal.action()).isEqualTo(Action.SELL);
        assertThat(signal.confidence()).isEqualTo(0.4);
    }

    @Test
    void generate_clampsConfidenceToZeroOneRange() {
        when(openAi.completeJson(anyString(), anyString(), anyInt())).thenReturn(
                "{\"ticker\":\"005930\",\"action\":\"BUY\",\"confidence\":2.5,\"reasoning\":\"x\"}");

        Signal signal = generator.generate(item());

        assertThat(signal.confidence()).isEqualTo(1.0);
    }

    @Test
    void generate_unknownActionFallsBackToHold() {
        when(openAi.completeJson(anyString(), anyString(), anyInt())).thenReturn(
                "{\"ticker\":\"005930\",\"action\":\"STRONG_BUY\",\"confidence\":0.9,\"reasoning\":\"x\"}");

        Signal signal = generator.generate(item());

        assertThat(signal.action()).isEqualTo(Action.HOLD);
    }

    @Test
    void generate_malformedOutputFallsBackToHold() {
        when(openAi.completeJson(anyString(), anyString(), anyInt())).thenReturn("이건 JSON 아님");

        Signal signal = generator.generate(item());

        assertThat(signal.action()).isEqualTo(Action.HOLD);
        assertThat(signal.confidence()).isZero();
        assertThat(signal.ticker()).isEqualTo("005930");
        assertThat(signal.reasoning()).contains("파싱 실패");
    }

    @Test
    void generate_userPromptIncludesDisclosureFields() {
        when(openAi.completeJson(anyString(), anyString(), anyInt())).thenReturn(
                "{\"ticker\":\"005930\",\"action\":\"HOLD\",\"confidence\":0.1,\"reasoning\":\"x\"}");

        generator.generate(item());

        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(openAi).completeJson(anyString(), userPrompt.capture(), anyInt());
        assertThat(userPrompt.getValue())
                .contains("삼성전자")
                .contains("005930")
                .contains("주요사항보고서")
                .contains("20260601");
    }

    @Test
    void generate_rejectsDisclosureWithoutStockCode() {
        DisclosureItem unlisted = new DisclosureItem(
                "00100000", "비상장사", null, "N",
                "감사보고서", "20260601000002", "이사회", "20260601", null);

        assertThatThrownBy(() -> generator.generate(unlisted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stock code");
    }
}
