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
import com.pdm.dao.LoanDAO.ActionResult;
import com.pdm.model.Reservation;

public class ReservationDAO {
    public List<Reservation> getReser() {
        return searchReser("");
    }

    public List<Reservation> searchReser(String keyword) {
        List<Reservation> list = new ArrayList<>();
        String trimmed = keyword == null ? "" : keyword.trim();
        boolean hasSearch = !trimmed.isEmpty();

        StringBuilder sql = new StringBuilder("""
                SELECT r.Reservation_ID, r.Member_ID, m.Full_Name AS Member_Name,
                       r.Book_ID, b.Title AS Book_Title, r.Reservation_Date, r.Status,
                       (SELECT COUNT(*) FROM Book_Copy bc WHERE bc.Book_ID = r.Book_ID AND bc.Status = 'Available') AS Available_Copies
                FROM Reservation r
                JOIN Member m ON r.Member_ID = m.Member_ID
                JOIN Book b ON r.Book_ID = b.Book_ID
                """);
        if (hasSearch) {
            sql.append("""
                     WHERE r.Reservation_ID LIKE ? OR r.Member_ID LIKE ? OR m.Full_Name LIKE ?
                        OR r.Book_ID LIKE ? OR b.ISBN LIKE ? OR b.Title LIKE ? OR r.Status LIKE ?
                    """);
        }
        sql.append(" ORDER BY CASE r.Status WHEN 'Confirmed' THEN 0 WHEN 'Pending' THEN 1 ELSE 2 END, r.Reservation_Date ASC ");

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
                    list.add(new Reservation(
                            rs.getString("Reservation_ID"),
                            rs.getString("Member_ID"),
                            rs.getString("Member_Name"),
                            rs.getString("Book_ID"),
                            rs.getString("Book_Title"),
                            rs.getDate("Reservation_Date"),
                            rs.getString("Status"),
                            rs.getInt("Available_Copies")));
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return list;
    }

    public ActionResult addReservation(String memberIdInput, String bookOrIsbnInput, LocalDate reservationDate) {
        if (memberIdInput == null || memberIdInput.isBlank() || bookOrIsbnInput == null || bookOrIsbnInput.isBlank()) {
            return new ActionResult(false, "Member ID and Book ID / ISBN are required.");
        }

        String memberId = memberIdInput.trim();
        String bookKey = bookOrIsbnInput.trim();
        LocalDate effectiveDate = reservationDate != null ? reservationDate : LocalDate.now();

        String checkMember = "SELECT Member_ID, Account_Status, Membership_Expiry_Date FROM Member WHERE Member_ID = ?";
        String findBook = "SELECT Book_ID, Title FROM Book WHERE Book_ID = ? OR ISBN = ? LIMIT 1";
        String countAvailable = "SELECT COUNT(*) AS avail FROM Book_Copy WHERE Book_ID = ? AND Status = 'Available'";
        String checkDuplicate = """
                SELECT COUNT(*) AS dup
                FROM Reservation
                WHERE Member_ID = ? AND Book_ID = ? AND Status IN ('Pending', 'Confirmed')
                """;
        String insertSql = "INSERT INTO Reservation (Reservation_ID, Member_ID, Book_ID, Reservation_Date, Status) VALUES (?, ?, ?, ?, 'Pending')";

        try (Connection conn = dbconnect.getConnection()) {
            String resolvedMemberId;
            try (PreparedStatement psM = conn.prepareStatement(checkMember)) {
                psM.setString(1, memberId);
                try (ResultSet rs = psM.executeQuery()) {
                    if (!rs.next()) {
                        return new ActionResult(false, "Member '" + memberId + "' does not exist.");
                    }
                    resolvedMemberId = rs.getString("Member_ID");
                    String status = rs.getString("Account_Status");
                    Date expiry = rs.getDate("Membership_Expiry_Date");
                    if (!"Active".equals(status)) {
                        return new ActionResult(false, "Member account is " + status + " and cannot place reservations.");
                    }
                    if (expiry != null && expiry.toLocalDate().isBefore(effectiveDate)) {
                        return new ActionResult(false, "Member card is expired.");
                    }
                }
            }

            String resolvedBookId;
            String bookTitle;
            try (PreparedStatement psB = conn.prepareStatement(findBook)) {
                psB.setString(1, bookKey);
                psB.setString(2, bookKey);
                try (ResultSet rs = psB.executeQuery()) {
                    if (!rs.next()) {
                        return new ActionResult(false, "Book with ID or ISBN '" + bookKey + "' was not found.");
                    }
                    resolvedBookId = rs.getString("Book_ID");
                    bookTitle = rs.getString("Title");
                }
            }

            try (PreparedStatement psAvail = conn.prepareStatement(countAvailable)) {
                psAvail.setString(1, resolvedBookId);
                try (ResultSet rs = psAvail.executeQuery()) {
                    if (rs.next() && rs.getInt("avail") > 0) {
                        return new ActionResult(false,
                                "Cannot reserve '" + bookTitle + "': there are currently "
                                        + rs.getInt("avail") + " available copy(ies). Please borrow directly.");
                    }
                }
            }

            try (PreparedStatement psDup = conn.prepareStatement(checkDuplicate)) {
                psDup.setString(1, resolvedMemberId);
                psDup.setString(2, resolvedBookId);
                try (ResultSet rs = psDup.executeQuery()) {
                    if (rs.next() && rs.getInt("dup") > 0) {
                        return new ActionResult(false, "Member already has an active reservation for this title.");
                    }
                }
            }

            String resId = generateNextReservationId(conn);
            try (PreparedStatement psIns = conn.prepareStatement(insertSql)) {
                psIns.setString(1, resId);
                psIns.setString(2, resolvedMemberId);
                psIns.setString(3, resolvedBookId);
                psIns.setDate(4, Date.valueOf(effectiveDate));
                psIns.executeUpdate();
                return new ActionResult(true, "Reservation " + resId + " created for '" + bookTitle + "'.", resId);
            }
        } catch (SQLException e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    public ActionResult fulfillReservation(String reservationId, String memberId, String bookId, String copyId,
            String staffId) {
        if (reservationId == null || copyId == null || copyId.isBlank() || staffId == null || staffId.isBlank()) {
            return new ActionResult(false, "Please select a copy and handling staff member.");
        }

        String checkRes = "SELECT Status FROM Reservation WHERE Reservation_ID = ?";
        String insertHeader = "INSERT INTO Loan_Transaction (Loan_ID, Member_ID, Staff_ID, Borrow_Date, Due_Date) VALUES (?, ?, ?, ?, ?)";
        String insertDetail = "INSERT INTO Loan_Detail (Loan_ID, Detail_ID, Book_ID, Copy_ID, Return_Date, Renewal_Count, Payment_Status) VALUES (?, 'D01', ?, ?, NULL, 0, 'None')";
        String updateCopy = "UPDATE Book_Copy SET Status = 'Borrowed' WHERE Book_ID = ? AND Copy_ID = ? AND Status IN ('Available', 'Reserved')";
        String updateRes = "UPDATE Reservation SET Status = 'Completed' WHERE Reservation_ID = ?";

        try (Connection conn = dbconnect.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psCheck = conn.prepareStatement(checkRes)) {
                    psCheck.setString(1, reservationId);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new ActionResult(false, "Reservation not found.");
                        }
                        String status = rs.getString("Status");
                        if (!"Pending".equals(status) && !"Confirmed".equals(status)) {
                            conn.rollback();
                            return new ActionResult(false, "Only Pending or Confirmed reservations can be fulfilled.");
                        }
                    }
                }

                LocalDate today = LocalDate.now();
                int period = LoanDAO.getMemberLoanPeriod(conn, memberId);
                LocalDate dueDate = today.plusDays(period);
                String loanId = LoanDAO.generateNextLoanId(conn);

                try (PreparedStatement psH = conn.prepareStatement(insertHeader);
                        PreparedStatement psD = conn.prepareStatement(insertDetail);
                        PreparedStatement psC = conn.prepareStatement(updateCopy);
                        PreparedStatement psR = conn.prepareStatement(updateRes)) {
                    psH.setString(1, loanId);
                    psH.setString(2, memberId);
                    psH.setString(3, staffId);
                    psH.setDate(4, Date.valueOf(today));
                    psH.setDate(5, Date.valueOf(dueDate));
                    psH.executeUpdate();

                    psD.setString(1, loanId);
                    psD.setString(2, bookId);
                    psD.setString(3, copyId);
                    psD.executeUpdate();

                    psC.setString(1, bookId);
                    psC.setString(2, copyId);
                    if (psC.executeUpdate() == 0) {
                        conn.rollback();
                        return new ActionResult(false, "Selected copy is not Available or Reserved.");
                    }

                    psR.setString(1, reservationId);
                    psR.executeUpdate();
                }

                conn.commit();
                return new ActionResult(true,
                        "Reservation " + reservationId + " fulfilled -> Loan " + loanId + " (Due: " + dueDate + ").",
                        loanId);
            } catch (SQLException e) {
                conn.rollback();
                return new ActionResult(false, e.getMessage());
            }
        } catch (SQLException e) {
            return new ActionResult(false, e.getMessage());
        }
    }

    public boolean cancelReser(String id) {
        String sql = "UPDATE Reservation SET Status = 'Cancelled' WHERE Reservation_ID = ? AND Status IN ('Pending', 'Confirmed')";
        try (Connection conn = dbconnect.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private String generateNextReservationId(Connection conn) throws SQLException {
        String sql = """
                SELECT Reservation_ID
                FROM Reservation
                WHERE Reservation_ID LIKE 'RES%'
                ORDER BY CAST(SUBSTRING(Reservation_ID, 4) AS UNSIGNED) DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String lastId = rs.getString("Reservation_ID");
                int num = Integer.parseInt(lastId.substring(3));
                return String.format("RES%02d", num + 1);
            }
        }
        return "RES01";
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
