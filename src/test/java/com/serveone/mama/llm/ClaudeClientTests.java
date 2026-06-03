package com.serveone.mama.llm;

import com.serveone.mama.config.MamaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClaudeClientTests {

    private ClaudeClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        MamaProperties props = new MamaProperties(
                new MamaProperties.Kis("k", "s", "0-0", true, "https://x", "https://y"),
                new MamaProperties.Dart("dart-key", "https://opendart.fss.or.kr/api"),
                new MamaProperties.Anthropic("anthropic-key", "claude-haiku-4-5-20251001")
        );
        client = new ClaudeClient(builder, props);
    }

    @Test
    void complete_sendsMessagesRequestAndReturnsConcatenatedText() {
        String response = """
                {
                  "id": "msg_01",
                  "type": "message",
                  "role": "assistant",
                  "content": [
                    {"type": "text", "text": "안녕"},
                    {"type": "text", "text": "하세요"}
                  ],
                  "model": "claude-haiku-4-5-20251001",
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 12, "output_tokens": 34}
                }
                """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andExpect(method(POST))
                .andExpect(header("x-api-key", "anthropic-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("claude-haiku-4-5-20251001"))
                .andExpect(jsonPath("$.max_tokens").value(256))
                .andExpect(jsonPath("$.system").value("sys"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("hi"))
                .andRespond(withSuccess(response, APPLICATION_JSON));

        String text = client.complete("sys", "hi", 256);

        assertThat(text).isEqualTo("안녕\n하세요");
        mockServer.verify();
    }

    @Test
    void complete_throwsWhenContentIsEmpty() {
        String response = """
                {
                  "id": "msg_02",
                  "type": "message",
                  "role": "assistant",
                  "content": [],
                  "stop_reason": "end_turn"
                }
                """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess(response, APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("sys", "hi", 256))
                .isInstanceOf(ClaudeClientException.class)
                .hasMessageContaining("empty content");
    }

    @Test
    void complete_throwsWhenOnlyNonTextBlocks() {
        String response = """
                {
                  "id": "msg_03",
                  "type": "message",
                  "role": "assistant",
                  "content": [{"type": "tool_use"}],
                  "stop_reason": "tool_use"
                }
                """;

        mockServer.expect(requestTo("https://api.anthropic.com/v1/messages"))
                .andRespond(withSuccess(response, APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("sys", "hi", 256))
                .isInstanceOf(ClaudeClientException.class)
                .hasMessageContaining("no text blocks");
    }
}
