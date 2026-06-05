package com.serveone.mama.dart;

import com.serveone.mama.dart.entity.DisclosureEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class DisclosureIngestService {

    private final DartClient dartClient;
    private final DisclosureRepository repository;
    private final Clock clock;

    public DisclosureIngestService(DartClient dartClient, DisclosureRepository repository, Clock clock) {
        this.dartClient = dartClient;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public IngestPage ingest(LocalDate from, LocalDate to, int pageNo, int pageCount) {
        DisclosureListResponse response = dartClient.fetchDisclosures(from, to, pageNo, pageCount);
        if (!response.isSuccess()) {
            throw new DartIngestException(
                    "DART list.json failed: status=" + response.status() + " message=" + response.message());
        }
        List<DisclosureItem> items = response.list() == null ? List.of() : response.list();
        Instant now = Instant.now(clock);
        List<DisclosureEntity> entities = items.stream()
                .map(item -> DisclosureEntity.of(item, now))
                .toList();
        repository.saveAll(entities);
        int totalPage = response.totalPage() != null && response.totalPage() > 0 ? response.totalPage() : 1;
        log.info("ingested {} disclosures ({} ~ {} page {}/{})",
                entities.size(), from, to, response.pageNo(), totalPage);
        return new IngestPage(entities, totalPage);
    }
}
