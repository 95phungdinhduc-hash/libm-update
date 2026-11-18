package com.pdm.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Payment {
    private final String loanId;
    private final String detailId;
    private final BigDecimal amountPaid;
    private final LocalDate paymentDate;
    private final String paymentMethod;
    private final String resultingStatus;

    public Payment(String loanId, String detailId, BigDecimal amountPaid, LocalDate paymentDate,
            String paymentMethod, String resultingStatus) {
        this.loanId = loanId;
        this.detailId = detailId;
        this.amountPaid = amountPaid;
        this.paymentDate = paymentDate;
        this.paymentMethod = paymentMethod;
        this.resultingStatus = resultingStatus;
    }

    public String getLoanId() {
        return loanId;
    }

    public String getDetailId() {
        return detailId;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getResultingStatus() {
        return resultingStatus;
    }
}

