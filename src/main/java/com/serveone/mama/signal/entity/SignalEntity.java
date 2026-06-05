package com.serveone.mama.signal.entity;

import com.serveone.mama.signal.Action;
import com.serveone.mama.signal.Signal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "signal")
public class SignalEntity {

    @Id
    @Column(name = "rcept_no", length = 32, nullable = false)
    private String rceptNo;

    @Column(name = "ticker", length = 16, nullable = false)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 8, nullable = false)
    private Action action;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "reasoning")
    private String reasoning;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "order_no", length = 32)
    private String orderNo;

    @Column(name = "executed_qty")
    private Integer executedQty;

    protected SignalEntity() {}

    private SignalEntity(String rceptNo, String ticker, Action action, double confidence,
                         String reasoning, String errorMessage, Instant generatedAt) {
        this.rceptNo = rceptNo;
        this.ticker = ticker;
        this.action = action;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.errorMessage = errorMessage;
        this.generatedAt = generatedAt;
    }

    public static SignalEntity success(String rceptNo, Signal signal, Instant now) {
        return new SignalEntity(rceptNo, signal.ticker(), signal.action(),
                signal.confidence(), signal.reasoning(), null, now);
    }

    public static SignalEntity failed(String rceptNo, String ticker, String errorMessage, Instant now) {
        return new SignalEntity(rceptNo, ticker, Action.HOLD, 0.0, null, errorMessage, now);
    }

    public void markExecuted(String orderNo, int executedQty, Instant now) {
        this.orderNo = orderNo;
        this.executedQty = executedQty;
        this.executedAt = now;
    }

    public void markFailed(String errorMessage, Instant now) {
        this.errorMessage = errorMessage;
        this.executedAt = now;
    }

    public void markSuperseded(String winnerRceptNo, Instant now) {
        this.errorMessage = "superseded by " + winnerRceptNo;
        this.executedAt = now;
    }

    public String rceptNo() { return rceptNo; }
    public String ticker() { return ticker; }
    public Action action() { return action; }
    public double confidence() { return confidence; }
    public String reasoning() { return reasoning; }
    public String errorMessage() { return errorMessage; }
    public Instant generatedAt() { return generatedAt; }
    public Instant executedAt() { return executedAt; }
    public String orderNo() { return orderNo; }
    public Integer executedQty() { return executedQty; }
}
