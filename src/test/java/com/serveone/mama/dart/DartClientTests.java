package com.serveone.mama.dart;

import com.serveone.mama.config.MamaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DartClientTests {

    private DartClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        MamaProperties props = new MamaProperties(
                new MamaProperties.Kis("k", "s", "0-0", true, "https://x", "https://y", null),
                new MamaProperties.Dart("test-key", "https://opendart.fss.or.kr/api"),
                new MamaProperties.OpenAi("a", "gpt-4o-mini"),
                new MamaProperties.Watchlist(List.of()),
                new MamaProperties.Executor(0.01, 0.6),
                new MamaProperties.Pipeline("0 0 16 * * MON-FRI", "0 5 9 * * MON-FRI", 0L)
        );
        client = new DartClient(builder, props);
    }

    @Test
    void fetchDisclosures_parsesResponseFromDart() {
        String body = """
                {
                  "status": "000",
                  "message": "정상",
                  "page_no": 1,
                  "page_count": 10,
                  "total_count": 1,
                  "total_page": 1,
                  "list": [
                    {
                      "corp_cls": "Y",
                      "corp_name": "삼성전자",
                      "corp_code": "00126380",
                      "stock_code": "005930",
                      "report_nm": "주요사항보고서",
                      "rcept_no": "20260601000001",
                      "flr_nm": "삼성전자",
                      "rcept_dt": "20260601",
                      "rm": ""
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo(startsWith("https://opendart.fss.or.kr/api/list.json")))
                .andExpect(method(GET))
                .andExpect(queryParam("crtfc_key", "test-key"))
                .andExpect(queryParam("bgn_de", "20260601"))
                .andExpect(queryParam("end_de", "20260601"))
                .andExpect(queryParam("page_no", "1"))
                .andExpect(queryParam("page_count", "10"))
                .andRespond(withSuccess(body, APPLICATION_JSON));

        DisclosureListResponse response = client.fetchDisclosures(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), 1, 10);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.list()).hasSize(1);
        DisclosureItem item = response.list().get(0);
        assertThat(item.corpName()).isEqualTo("삼성전자");
        assertThat(item.stockCode()).isEqualTo("005930");
        assertThat(item.reportNm()).isEqualTo("주요사항보고서");
        mockServer.verify();
    }

    @Test
    void isSuccess_returnsFalseForNonZeroStatus() {
        DisclosureListResponse error = new DisclosureListResponse(
                "010", "등록되지 않은 키입니다.", null, null, null, null, null);
        assertThat(error.isSuccess()).isFalse();
    }
}
