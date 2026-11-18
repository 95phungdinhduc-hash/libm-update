package com.pdm.model;

import java.sql.Date;

public class Loan {
    private String loanId;
    private String detailId;
    private String memberId;
    private String memberName;
    private String staffId;
    private String staffName;
    private String bookId;
    private String isbn;
    private String title;
    private String copyId;
    private Date borrowDate;
    private Date dueDate;
    private Date returnDate;
    private int renewalCount;
    private String status;
    private String paymentStatus;

    public Loan() {
    }

    public Loan(String loanId, String detailId, String memberId, String memberName,
            String staffId, String staffName, String bookId, String isbn, String title,
            String copyId, Date borrowDate, Date dueDate, Date returnDate,
            int renewalCount, String status, String paymentStatus) {
        this.loanId = loanId;
        this.detailId = detailId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.staffId = staffId;
        this.staffName = staffName;
        this.bookId = bookId;
        this.isbn = isbn;
        this.title = title;
        this.copyId = copyId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.renewalCount = renewalCount;
        this.status = status;
        this.paymentStatus = paymentStatus;
    }

    public String getLoanId() {
        return loanId;
    }

    public String getDetailId() {
        return detailId;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getStaffId() {
        return staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public String getBookId() {
        return bookId;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getCopyId() {
        return copyId;
    }

    public Date getBorrowDate() {
        return borrowDate;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public Date getReturnDate() {
        return returnDate;
    }

    public int getRenewalCount() {
        return renewalCount;
    }

    public String getStatus() {
        return status;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    // Aliases for JavaFX PropertyValueFactory compatibility
    public String getLoan_ID() {
        return loanId;
    }

    public String getDetail_ID() {
        return detailId;
    }

    public String getMember_ID() {
        return memberId;
    }

    public String getMember_Name() {
        return memberName;
    }

    public String getCopy_ID() {
        return copyId;
    }

    public Date getDate_Out() {
        return borrowDate;
    }

    public Date getDateDue() {
        return dueDate;
    }

    public Date getDate_Return() {
        return returnDate;
    }

    public String getPayment_Status() {
        return paymentStatus;
    }
}

