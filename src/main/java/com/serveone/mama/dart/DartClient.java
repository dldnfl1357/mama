package com.serveone.mama.dart;

import com.serveone.mama.config.MamaProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class DartClient {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient;
    private final MamaProperties.Dart props;

    public DartClient(RestClient.Builder builder, MamaProperties properties) {
        this.props = properties.dart();
        this.restClient = builder.baseUrl(this.props.baseUrl()).build();
    }

    public DisclosureListResponse fetchDisclosures(LocalDate from, LocalDate to, int pageNo, int pageCount) {
        log.info("fetching DART disclosures: {} ~ {} (page {}, size {})", from, to, pageNo, pageCount);
        return restClient.get()
                .uri(uri -> uri.path("/list.json")
                        .queryParam("crtfc_key", props.apiKey())
                        .queryParam("bgn_de", YYYYMMDD.format(from))
                        .queryParam("end_de", YYYYMMDD.format(to))
                        .queryParam("page_no", pageNo)
                        .queryParam("page_count", pageCount)
                        .build())
                .retrieve()
                .body(DisclosureListResponse.class);
    }
}
