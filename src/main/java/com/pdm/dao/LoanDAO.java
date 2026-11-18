package com.pdm.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.pdm.connect.dbconnect;
import com.pdm.model.Loan;

public class LoanDAO {

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

        public String getGeneratedId() {
            return generatedId;
        }
    }

    public static class StaffOption {
        private final String staffId;
        private final String fullName;
        private final String position;

        public StaffOption(String staffId, String fullName, String position) {
            this.staffId = staffId;
            this.fullName = fullName;
            this.position = position;
        }

        public String getStaffId() {
            return staffId;
        }

        public String getFullName() {
            return fullName;
        }

        public String getPosition() {
            return position;
        }

        @Override
        public String toString() {
            return staffId + " - " + fullName;
        }
    }

    public static class MemberOption {
        private final String memberId;
        private final String fullName;
        private final String status;
        private final int borrowingLimit;
        private final int loanPeriod;

        public MemberOption(String memberId, String fullName, String status, int borrowingLimit, int loanPeriod) {
            this.memberId = memberId;
            this.fullName = fullName;
            this.status = status;
            this.borrowingLimit = borrowingLimit;
            this.loanPeriod = loanPeriod;
        }

        public String getMemberId() {
            return memberId;
        }

        public String getFullName() {
            return fullName;
        }

        public String getStatus() {
            return status;
        }

        public int getBorrowingLimit() {
            return borrowingLimit;
        }

        public int getLoanPeriod() {
            return loanPeriod;
        }

        @Override
        public String toString() {
            return memberId + " - " + fullName + " (" + loanPeriod + "d, max " + borrowingLimit + ")";
        }
    }

    public List<Loan> getLoans(boolean overdueOnly, String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        boolean hasSearch = !trimmed.isEmpty();

        StringBuilder sql = new StringBuilder("""
                SELECT lt.Loan_ID, ld.Detail_ID, lt.Member_ID, m.Full_Name AS Member_Name,
                       lt.Staff_ID, s.Full_Name AS Staff_Name,
                       b.Book_ID, b.ISBN, b.Title, ld.Copy_ID,
                       lt.Borrow_Date, lt.Due_Date, ld.Return_Date, ld.Renewal_Count,
                       CASE
                           WHEN ld.Return_Date IS NOT NULL THEN 'Returned'
                           WHEN lt.Due_Date < CURRENT_DATE THEN 'Overdue'
                           ELSE 'Borrowing'
                       END AS Status,
                       ld.Payment_Status
                FROM Loan_Transaction lt
                JOIN Loan_Detail ld ON lt.Loan_ID = ld.Loan_ID
                JOIN Member m ON lt.Member_ID = m.Member_ID
                JOIN Staff s ON lt.Staff_ID = s.Staff_ID
                JOIN Book b ON ld.Book_ID = b.Book_ID
                WHERE 1=1
                """);

        if (overdueOnly) {
            sql.append(" AND ld.Return_Date IS NULL AND lt.Due_Date < CURRENT_DATE ");
        }
        if (hasSearch) {
            sql.append("""
                     AND (lt.Loan_ID LIKE ? OR lt.Member_ID LIKE ? OR m.Full_Name LIKE ?
                          OR b.Book_ID LIKE ? OR b.ISBN LIKE ? OR b.Title LIKE ? OR ld.Copy_ID LIKE ?)
                    """);
        }
        if (overdueOnly) {
            sql.append(" ORDER BY lt.Due_Date ASC, lt.Loan_ID ASC ");
        } else {
            sql.append(" ORDER BY lt.Borrow_Date DESC, CAST(SUBSTRING(lt.Loan_ID, 2) AS UNSIGNED) DESC, ld.Detail_ID ASC ");
        }

        List<Loan> list = new ArrayList<>();
        try (Connection conn = dbconnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (hasSearch) {
                String like = "%" + trimmed + "%";
                for (int i = 1; i <= 7; i++) {
                    ps.setString(i, like);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Loan(
                            rs.getString("Loan_ID"),
                            rs.getString("Detail_ID"),
                            rs.getString("Member_ID"),
                            rs.getString("Member_Name"),
                            rs.getString("Staff_ID"),
                            rs.getString("Staff_Name"),
                            rs.getString("Book_ID"),
                            rs.getString("ISBN"),
                            rs.getString("Title"),
                            rs.getString("Copy_ID"),
                            rs.getDate("Borrow_Date"),
                            rs.getDate("Due_Date"),
                            rs.getDate("Return_Date"),
                            rs.getInt("Renewal_Count"),
                            rs.getString("Status"),
                            rs.getString("Payment_Status")));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return list;
    }

    public ActionResult createLoan(String memberId, String staffId, String bookId, String copyId,
            LocalDate borrowDate, LocalDate dueDate) {
        if (memberId == null || memberId.isBlank() || staffId == null || staffId.isBlank()
                || bookId == null || bookId.isBlank() || copyId == null || copyId.isBlank()) {
            return new ActionResult(false, "Member, Staff, Book Title, and Copy are all required.");
        }

        LocalDate effectiveBorrow = borrowDate != null ? borrowDate : LocalDate.now();
        LocalDate effectiveDue = dueDate;

        String insertHeader = "INSERT INTO Loan_Transaction (Loan_ID, Member_ID, Staff_ID, Borrow_Date, Due_Date) VALUES (?, ?, ?, ?, ?)";
        String insertDetail = "INSERT INTO Loan_Detail (Loan_ID, Detail_ID, Book_ID, Copy_ID, Return_Date, Renewal_Count, Payment_Status) VALUES (?, ?, ?, ?, NULL, 0, 'None')";
        String updateCopy = "UPDATE Book_Copy SET Status = 'Borrowed' WHERE Book_ID = ? AND Copy_ID = ? AND Status IN ('Available', 'Reserved')";
        String completeConfirmedRes = """
                UPDATE Reservation
                SET Status = 'Completed'
                WHERE Member_ID = ? AND Book_ID = ? AND Status = 'Confirmed'
                ORDER BY Reservation_Date ASC
                LIMIT 1
                """;

        try (Connection conn = dbconnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (effectiveDue == null) {
                    int period = getMemberLoanPeriod(conn, memberId);
                    effectiveDue = effectiveBorrow.plusDays(period);
                }
                String loanId = generateNextLoanId(conn);

                try (PreparedStatement psHeader = conn.prepareStatement(insertHeader);
                        PreparedStatement psDetail = conn.prepareStatement(insertDetail);
                        PreparedStatement psCopy = conn.prepareStatement(updateCopy);
                        PreparedStatement psRes = conn.prepareStatement(completeConfirmedRes)) {

                    psHeader.setString(1, loanId);
                    psHeader.setString(2, memberId);
                    psHeader.setString(3, staffId);
                    psHeader.setDate(4, Date.valueOf(effectiveBorrow));
                    psHeader.setDate(5, Date.valueOf(effectiveDue));
                    psHeader.executeUpdate();

                    psDetail.setString(1, loanId);
                    psDetail.setString(2, "D01");
                    psDetail.setString(3, bookId);
                    psDetail.setString(4, copyId);
                    psDetail.executeUpdate();

                    psCopy.setString(1, bookId);
                    psCopy.setString(2, copyId);
                    if (psCopy.executeUpdate() == 0) {
                        conn.rollback();
                        return new ActionResult(false, "Selected copy is no longer Available or Reserved.");
                    }

                    psRes.setString(1, memberId);
                    psRes.setString(2, bookId);
                    psRes.executeUpdate();

                    conn.commit();
                    return new ActionResult(true, "Loan " + loanId + " created successfully (Due: " + effectiveDue + ").", loanId);
                }
            } catch (SQLException e) {
                conn.rollback();
                return new ActionResult(false, e.getMessage());
            }
        } catch (SQLException e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    public ActionResult returnCopy(String loanId, String detailId, String bookId, String copyId, LocalDate returnDate) {
        LocalDate effectiveReturn = returnDate != null ? returnDate : LocalDate.now();
        String updateDetail = """
                UPDATE Loan_Detail ld
                JOIN Loan_Transaction lt ON ld.Loan_ID = lt.Loan_ID
                SET ld.Return_Date = ?,
                    ld.Payment_Status = CASE
                        WHEN ? > lt.Due_Date THEN
                            CASE WHEN ld.Payment_Status IN ('Paid', 'Waived') THEN ld.Payment_Status ELSE 'Unpaid' END
                        ELSE 'None'
                    END
                WHERE ld.Loan_ID = ? AND ld.Detail_ID = ? AND ld.Return_Date IS NULL
                """;
        String findPendingReservation = """
                SELECT Reservation_ID, Member_ID
                FROM Reservation
                WHERE Book_ID = ? AND Status = 'Pending'
                ORDER BY Reservation_Date ASC, Reservation_ID ASC
                LIMIT 1
                """;
        String confirmReservation = "UPDATE Reservation SET Status = 'Confirmed' WHERE Reservation_ID = ?";
        String updateCopyStatus = "UPDATE Book_Copy SET Status = ? WHERE Book_ID = ? AND Copy_ID = ?";

        try (Connection conn = dbconnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int updated;
                try (PreparedStatement psDetail = conn.prepareStatement(updateDetail)) {
                    Date sqlReturn = Date.valueOf(effectiveReturn);
                    psDetail.setDate(1, sqlReturn);
                    psDetail.setDate(2, sqlReturn);
                    psDetail.setString(3, loanId);
                    psDetail.setString(4, detailId);
                    updated = psDetail.executeUpdate();
                }

                if (updated == 0) {
                    conn.rollback();
                    return new ActionResult(false, "This loan item has already been returned or does not exist.");
                }

                String nextResId = null;
                String nextResMember = null;
                try (PreparedStatement psFind = conn.prepareStatement(findPendingReservation)) {
                    psFind.setString(1, bookId);
                    try (ResultSet rs = psFind.executeQuery()) {
                        if (rs.next()) {
                            nextResId = rs.getString("Reservation_ID");
                            nextResMember = rs.getString("Member_ID");
                        }
                    }
                }

                String newCopyStatus = nextResId != null ? "Reserved" : "Available";
                if (nextResId != null) {
                    try (PreparedStatement psConfirm = conn.prepareStatement(confirmReservation)) {
                        psConfirm.setString(1, nextResId);
                        psConfirm.executeUpdate();
                    }
                }

                try (PreparedStatement psCopy = conn.prepareStatement(updateCopyStatus)) {
                    psCopy.setString(1, newCopyStatus);
                    psCopy.setString(2, bookId);
                    psCopy.setString(3, copyId);
                    psCopy.executeUpdate();
                }

                conn.commit();
                if (nextResId != null) {
                    return new ActionResult(true,
                            "Book returned. Copy is held ('Reserved') for pending reservation "
                                    + nextResId + " (Member " + nextResMember + ").");
                }
                return new ActionResult(true, "Book returned successfully. Copy is now Available.");
            } catch (SQLException e) {
                conn.rollback();
                return new ActionResult(false, e.getMessage());
            }
        } catch (SQLException e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    public ActionResult renewLoan(String loanId, String detailId) {
        String infoSql = """
                SELECT ld.Return_Date, ld.Renewal_Count, ld.Book_ID, lt.Due_Date, mc.Loan_Period,
                       (SELECT COUNT(*) FROM Reservation r WHERE r.Book_ID = ld.Book_ID AND r.Status IN ('Pending', 'Confirmed')) AS open_res
                FROM Loan_Detail ld
                JOIN Loan_Transaction lt ON ld.Loan_ID = lt.Loan_ID
                JOIN Member m ON lt.Member_ID = m.Member_ID
                JOIN Member_Category mc ON m.Member_Type_ID = mc.Member_Type_ID
                WHERE ld.Loan_ID = ? AND ld.Detail_ID = ?
                """;
        String updateDetail = "UPDATE Loan_Detail SET Renewal_Count = Renewal_Count + 1 WHERE Loan_ID = ? AND Detail_ID = ?";
        String updateHeader = "UPDATE Loan_Transaction SET Due_Date = ? WHERE Loan_ID = ?";

        try (Connection conn = dbconnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDate newDue;
                try (PreparedStatement psInfo = conn.prepareStatement(infoSql)) {
                    psInfo.setString(1, loanId);
                    psInfo.setString(2, detailId);
                    try (ResultSet rs = psInfo.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new ActionResult(false, "Loan record not found.");
                        }
                        if (rs.getDate("Return_Date") != null) {
                            conn.rollback();
                            return new ActionResult(false, "Returned books cannot be renewed.");
                        }
                        if (rs.getInt("Renewal_Count") >= 5) {
                            conn.rollback();
                            return new ActionResult(false, "Maximum renewal limit (5) reached.");
                        }
                        if (rs.getInt("open_res") > 0) {
                            conn.rollback();
                            return new ActionResult(false, "Cannot renew: another member has an active reservation for this title.");
                        }
                        LocalDate currentDue = rs.getDate("Due_Date").toLocalDate();
                        int period = rs.getInt("Loan_Period");
                        newDue = currentDue.plusDays(period);
                    }
                }

                try (PreparedStatement psD = conn.prepareStatement(updateDetail);
                        PreparedStatement psH = conn.prepareStatement(updateHeader)) {
                    psD.setString(1, loanId);
                    psD.setString(2, detailId);
                    psD.executeUpdate();

                    psH.setDate(1, Date.valueOf(newDue));
                    psH.setString(2, loanId);
                    psH.executeUpdate();
                }

                conn.commit();
                return new ActionResult(true, "Loan renewed. New due date: " + newDue + ".");
            } catch (SQLException e) {
                conn.rollback();
                return new ActionResult(false, e.getMessage());
            }
        } catch (SQLException e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    public List<StaffOption> getStaffOptions() {
        String sql = "SELECT Staff_ID, Full_Name, Position FROM Staff ORDER BY CAST(SUBSTRING(Staff_ID, 2) AS UNSIGNED) ASC";
        List<StaffOption> list = new ArrayList<>();
        try (Connection conn = dbconnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new StaffOption(
                        rs.getString("Staff_ID"),
                        rs.getString("Full_Name"),
                        rs.getString("Position")));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return list;
    }

    public List<MemberOption> getActiveMemberOptions() {
        String sql = """
                SELECT m.Member_ID, m.Full_Name, m.Account_Status, mc.Borrowing_Limit, mc.Loan_Period
                FROM Member m
                JOIN Member_Category mc ON m.Member_Type_ID = mc.Member_Type_ID
                WHERE m.Account_Status = 'Active'
                ORDER BY CAST(SUBSTRING(m.Member_ID, 2) AS UNSIGNED) ASC
                """;
        List<MemberOption> list = new ArrayList<>();
        try (Connection conn = dbconnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new MemberOption(
                        rs.getString("Member_ID"),
                        rs.getString("Full_Name"),
                        rs.getString("Account_Status"),
                        rs.getInt("Borrowing_Limit"),
                        rs.getInt("Loan_Period")));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return list;
    }

    public static int getMemberLoanPeriod(Connection conn, String memberId) throws SQLException {
        String sql = """
                SELECT mc.Loan_Period
                FROM Member m
                JOIN Member_Category mc ON m.Member_Type_ID = mc.Member_Type_ID
                WHERE m.Member_ID = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Loan_Period");
                }
            }
        }
        throw new SQLException("Member '" + memberId + "' was not found.");
    }

    public static String generateNextLoanId(Connection conn) throws SQLException {
        String sql = """
                SELECT Loan_ID
                FROM Loan_Transaction
                WHERE Loan_ID LIKE 'L%'
                ORDER BY CAST(SUBSTRING(Loan_ID, 2) AS UNSIGNED) DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String lastId = rs.getString("Loan_ID");
                int num = Integer.parseInt(lastId.substring(1));
                return String.format("L%02d", num + 1);
            }
        }
        return "L01";
    }
}
