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

class OpenAiClientTests {

    private OpenAiClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        MamaProperties props = new MamaProperties(
                new MamaProperties.Kis("k", "s", "0-0", true, "https://x", "https://y"),
                new MamaProperties.Dart("dart-key", "https://opendart.fss.or.kr/api"),
                new MamaProperties.OpenAi("openai-key", "gpt-4o-mini")
        );
        client = new OpenAiClient(builder, props);
    }

    @Test
    void complete_sendsChatRequestAndReturnsAssistantContent() {
        String response = """
                {
                  "id": "chatcmpl-01",
                  "model": "gpt-4o-mini",
                  "choices": [
                    {
                      "index": 0,
                      "message": {"role": "assistant", "content": "안녕하세요"},
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {"prompt_tokens": 12, "completion_tokens": 34, "total_tokens": 46}
                }
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header("authorization", "Bearer openai-key"))
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.max_tokens").value(256))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value("sys"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.messages[1].content").value("hi"))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(response, APPLICATION_JSON));

        String text = client.complete("sys", "hi", 256);

        assertThat(text).isEqualTo("안녕하세요");
        mockServer.verify();
    }

    @Test
    void completeJson_setsResponseFormatJsonObject() {
        String response = """
                {
                  "id": "chatcmpl-02",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": "{\\"a\\":1}"}, "finish_reason": "stop"}
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(response, APPLICATION_JSON));

        String text = client.completeJson("sys", "hi", 256);

        assertThat(text).isEqualTo("{\"a\":1}");
        mockServer.verify();
    }

    @Test
    void complete_throwsWhenChoicesEmpty() {
        String response = """
                {"id":"chatcmpl-03","choices":[]}
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(response, APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("sys", "hi", 256))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessageContaining("empty choices");
    }

    @Test
    void complete_throwsWhenContentBlank() {
        String response = """
                {
                  "id": "chatcmpl-04",
                  "choices": [
                    {"index": 0, "message": {"role": "assistant", "content": ""}, "finish_reason": "length"}
                  ]
                }
                """;

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andRespond(withSuccess(response, APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("sys", "hi", 256))
                .isInstanceOf(OpenAiClientException.class)
                .hasMessageContaining("no content")
                .hasMessageContaining("length");
    }
}
