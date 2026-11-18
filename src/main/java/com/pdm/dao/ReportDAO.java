package com.pdm.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.pdm.connect.dbconnect;

public class ReportDAO {

    public static class DashboardStats {
        private final int totalBooks;
        private final int availableCopies;
        private final int activeMembers;
        private final int activeLoans;
        private final int overdueLoans;
        private final int pendingReservations;
        private final BigDecimal unpaidFines;

        public DashboardStats(int totalBooks, int availableCopies, int activeMembers,
                int activeLoans, int overdueLoans, int pendingReservations, BigDecimal unpaidFines) {
            this.totalBooks = totalBooks;
            this.availableCopies = availableCopies;
            this.activeMembers = activeMembers;
            this.activeLoans = activeLoans;
            this.overdueLoans = overdueLoans;
            this.pendingReservations = pendingReservations;
            this.unpaidFines = unpaidFines;
        }

        public int getTotalBooks() {
            return totalBooks;
        }

        public int getAvailableCopies() {
            return availableCopies;
        }

        public int getActiveMembers() {
            return activeMembers;
        }

        public int getActiveLoans() {
            return activeLoans;
        }

        public int getOverdueLoans() {
            return overdueLoans;
        }

        public int getPendingReservations() {
            return pendingReservations;
        }

        public BigDecimal getUnpaidFines() {
            return unpaidFines;
        }
    }

    public DashboardStats getDashboardStats() {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM Book) AS Total_Books,
                    (SELECT COUNT(*) FROM Book_Copy WHERE Status = 'Available') AS Available_Copies,
                    (SELECT COUNT(*) FROM Member WHERE Account_Status = 'Active') AS Active_Members,
                    (SELECT COUNT(*) FROM Loan_Detail WHERE Return_Date IS NULL) AS Active_Loans,
                    (SELECT COUNT(*) FROM Loan_Detail ld JOIN Loan_Transaction lt ON ld.Loan_ID = lt.Loan_ID
                     WHERE ld.Return_Date IS NULL AND lt.Due_Date < CURRENT_DATE) AS Overdue_Loans,
                    (SELECT COUNT(*) FROM Reservation WHERE Status IN ('Pending', 'Confirmed')) AS Pending_Reservations,
                    (SELECT COALESCE(SUM(Fine_Amount), 0) FROM Loan_Detail_Fine WHERE Payment_Status = 'Unpaid') AS Unpaid_Fines
                """;
        try (Connection conn = dbconnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                BigDecimal unpaid = rs.getBigDecimal("Unpaid_Fines");
                return new DashboardStats(
                        rs.getInt("Total_Books"),
                        rs.getInt("Available_Copies"),
                        rs.getInt("Active_Members"),
                        rs.getInt("Active_Loans"),
                        rs.getInt("Overdue_Loans"),
                        rs.getInt("Pending_Reservations"),
                        unpaid != null ? unpaid : BigDecimal.ZERO);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return new DashboardStats(0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
    }
}
