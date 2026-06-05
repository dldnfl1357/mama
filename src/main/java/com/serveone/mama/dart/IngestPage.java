package com.serveone.mama.dart;

import com.serveone.mama.dart.entity.DisclosureEntity;

import java.util.List;

public record IngestPage(List<DisclosureEntity> entities, int totalPage) {

    public IngestPage {
        entities = entities == null ? List.of() : List.copyOf(entities);
    }
}
