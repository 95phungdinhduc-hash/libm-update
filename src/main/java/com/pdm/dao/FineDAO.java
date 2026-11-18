package com.pdm.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.pdm.connect.dbconnect;
import com.pdm.model.Fine;

public class FineDAO {
    public List<Fine> get_fines() {
        return search_fines("");
    }

    public List<Fine> search_fines(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        boolean hasSearch = !trimmed.isEmpty();

        StringBuilder listfine_q = new StringBuilder("""
                SELECT ldf.Loan_ID, ldf.Detail_ID, ldf.Member_ID, m.Full_Name AS Member_Name,
                       b.Title AS Book_Title, ldf.Copy_ID, ldf.Due_Date, ldf.Return_Date,
                       ldf.Days_Late, ldf.Fine_Amount, ldf.Payment_Status
                FROM Loan_Detail_Fine ldf
                JOIN Member m ON ldf.Member_ID = m.Member_ID
                JOIN Book b ON ldf.Book_ID = b.Book_ID
                WHERE ldf.Fine_Amount > 0
                """);
        if (hasSearch) {
            listfine_q.append("""
                     AND (ldf.Loan_ID LIKE ? OR ldf.Member_ID LIKE ? OR m.Full_Name LIKE ?
                          OR b.Title LIKE ? OR ldf.Copy_ID LIKE ? OR ldf.Payment_Status LIKE ?)
                    """);
        }
        listfine_q.append(" ORDER BY CASE ldf.Payment_Status WHEN 'Unpaid' THEN 0 WHEN 'Paid' THEN 1 WHEN 'Waived' THEN 2 ELSE 3 END, ldf.Due_Date ASC ");

        List<Fine> list = new ArrayList<>();
        try (
                Connection conn = dbconnect.getConnection();
                PreparedStatement ps = conn.prepareStatement(listfine_q.toString())) {
            if (hasSearch) {
                String like = "%" + trimmed + "%";
                for (int i = 1; i <= 6; i++) {
                    ps.setString(i, like);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Fine fine = new Fine(
                            rs.getString("Loan_ID"),
                            rs.getString("Detail_ID"),
                            rs.getString("Member_ID"),
                            rs.getString("Member_Name"),
                            rs.getString("Book_Title"),
                            rs.getString("Copy_ID"),
                            rs.getDate("Due_Date"),
                            rs.getDate("Return_Date"),
                            rs.getInt("Days_Late"),
                            rs.getBigDecimal("Fine_Amount"),
                            rs.getString("Payment_Status"));
                    list.add(fine);
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return list;
    }
}
