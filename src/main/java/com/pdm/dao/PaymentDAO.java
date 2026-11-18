package com.pdm.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import com.pdm.connect.dbconnect;
import com.pdm.dao.LoanDAO.ActionResult;

public class PaymentDAO {

    public ActionResult processPayment(String loanId, String detailId, BigDecimal payAmount,
            LocalDate paymentDate, String paymentMethod) {
        if (loanId == null || detailId == null) {
            return new ActionResult(false, "Please select a fine record first.");
        }
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new ActionResult(false, "Pay amount must be greater than 0.");
        }
        if (paymentDate == null) {
            return new ActionResult(false, "Please select a payment date.");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return new ActionResult(false, "Please select a payment method.");
        }

        String checkSql = """
                SELECT Return_Date, Fine_Amount, Payment_Status
                FROM Loan_Detail_Fine
                WHERE Loan_ID = ? AND Detail_ID = ?
                """;
        String updateSql = "UPDATE Loan_Detail SET Payment_Status = 'Paid' WHERE Loan_ID = ? AND Detail_ID = ?";

        try (Connection conn = dbconnect.getConnection()) {
            try (PreparedStatement psCheck = conn.prepareStatement(checkSql)) {
                psCheck.setString(1, loanId);
                psCheck.setString(2, detailId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (!rs.next()) {
                        return new ActionResult(false, "Fine record not found.");
                    }
                    if (rs.getDate("Return_Date") == null) {
                        return new ActionResult(false,
                                "This book has not been returned yet. Please return the book in Loans Management before settling the final fine.");
                    }
                    BigDecimal fineAmount = rs.getBigDecimal("Fine_Amount");
                    String currentStatus = rs.getString("Payment_Status");
                    if ("Paid".equals(currentStatus) || "Waived".equals(currentStatus)) {
                        return new ActionResult(false, "This fine is already marked as " + currentStatus + ".");
                    }
                    if (fineAmount == null || fineAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        return new ActionResult(false, "There is no fine owed on this loan line.");
                    }
                    if (payAmount.compareTo(fineAmount) < 0) {
                        return new ActionResult(false,
                                "Pay amount (" + payAmount + " VND) is less than the fine amount (" + fineAmount + " VND).");
                    }
                }
            }

            try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                psUpd.setString(1, loanId);
                psUpd.setString(2, detailId);
                int updated = psUpd.executeUpdate();
                if (updated > 0) {
                    return new ActionResult(true,
                            "Payment of " + payAmount + " VND (" + paymentMethod + " on " + paymentDate
                                    + ") recorded. Fine marked as Paid.");
                }
                return new ActionResult(false, "Failed to update payment status.");
            }
        } catch (SQLException e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    public ActionResult updateFineStatus(String loanId, String detailId, String newStatus) {
        if (loanId == null || detailId == null || newStatus == null || newStatus.isBlank()) {
            return new ActionResult(false, "Please select a fine record and status.");
        }
        String sql = "UPDATE Loan_Detail SET Payment_Status = ? WHERE Loan_ID = ? AND Detail_ID = ?";
        try (Connection conn = dbconnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, loanId);
            ps.setString(3, detailId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                return new ActionResult(true, "Fine status updated to '" + newStatus + "'.");
            }
            return new ActionResult(false, "Fine record not found.");
        } catch (SQLException e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    public ActionResult waiveFine(String loanId, String detailId) {
        return updateFineStatus(loanId, detailId, "Waived");
    }

    public static class ActionResult {
        private final boolean success;
        private final String message;
        private final String generatedId;

        public ActionResult(boolean success, String message) {
            this(success, message, null);
        }

        public ActionResult(boolean success, String message, String generatedId) {
            this.success = success;
            this.message = message;
            this.generatedId = generatedId;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
}}
