package com.serveone.mama.dart.entity;

import com.serveone.mama.dart.DisclosureItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "disclosure")
public class DisclosureEntity {

    private static final DateTimeFormatter RCEPT_DT = DateTimeFormatter.BASIC_ISO_DATE;

    @Id
    @Column(name = "rcept_no", length = 32, nullable = false)
    private String rceptNo;

    @Column(name = "corp_code", length = 16, nullable = false)
    private String corpCode;

    @Column(name = "corp_name", nullable = false)
    private String corpName;

    @Column(name = "stock_code", length = 16)
    private String stockCode;

    @Column(name = "corp_cls", length = 4, nullable = false)
    private String corpCls;

    @Column(name = "report_nm", nullable = false)
    private String reportNm;

    @Column(name = "flr_nm", nullable = false)
    private String flrNm;

    @Column(name = "rcept_dt", nullable = false)
    private LocalDate rceptDt;

    @Column(name = "rm")
    private String rm;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected DisclosureEntity() {
    }

    private DisclosureEntity(
            String rceptNo,
            String corpCode,
            String corpName,
            String stockCode,
            String corpCls,
            String reportNm,
            String flrNm,
            LocalDate rceptDt,
            String rm,
            Instant fetchedAt) {
        this.rceptNo = rceptNo;
        this.corpCode = corpCode;
        this.corpName = corpName;
        this.stockCode = stockCode;
        this.corpCls = corpCls;
        this.reportNm = reportNm;
        this.flrNm = flrNm;
        this.rceptDt = rceptDt;
        this.rm = rm;
        this.fetchedAt = fetchedAt;
    }

    public static DisclosureEntity of(DisclosureItem item, Instant fetchedAt) {
        return new DisclosureEntity(
                item.rceptNo(),
                item.corpCode(),
                item.corpName(),
                nullIfBlank(item.stockCode()),
                item.corpCls(),
                item.reportNm(),
                item.flrNm(),
                LocalDate.parse(item.rceptDt(), RCEPT_DT),
                nullIfBlank(item.rm()),
                fetchedAt
        );
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    public String rceptNo() { return rceptNo; }
    public String corpCode() { return corpCode; }
    public String corpName() { return corpName; }
    public String stockCode() { return stockCode; }
    public String corpCls() { return corpCls; }
    public String reportNm() { return reportNm; }
    public String flrNm() { return flrNm; }
    public LocalDate rceptDt() { return rceptDt; }
    public String rm() { return rm; }
    public Instant fetchedAt() { return fetchedAt; }
}
