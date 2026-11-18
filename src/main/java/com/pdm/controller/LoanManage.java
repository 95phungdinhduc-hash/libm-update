package com.pdm.controller;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

import com.pdm.connect.dbconnect;
import com.pdm.dao.LoanDAO;
import com.pdm.dao.LoanDAO.ActionResult;
import com.pdm.model.Loan;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

public class LoanManage extends basecontrol implements Initializable {

    @FXML private TableView<Loan> loan_table;
    @FXML private TableColumn<Loan, String> col_loanid;
    @FXML private TableColumn<Loan, String> col_detailid;
    @FXML private TableColumn<Loan, String> col_memid;
    @FXML private TableColumn<Loan, String> col_memname;
    @FXML private TableColumn<Loan, String> col_staffid;
    @FXML private TableColumn<Loan, String> col_bookid;
    @FXML private TableColumn<Loan, String> col_isbn;
    @FXML private TableColumn<Loan, String> col_title;
    @FXML private TableColumn<Loan, String> col_copyid;
    @FXML private TableColumn<Loan, java.sql.Date> col_dateout;
    @FXML private TableColumn<Loan, java.sql.Date> col_duedate;
    @FXML private TableColumn<Loan, java.sql.Date> col_datereturn;
    @FXML private TableColumn<Loan, String> col_status;
    @FXML private TableColumn<Loan, String> col_paymentstatus;

    @FXML private CheckBox overdueCheckBox;
    @FXML private TextField search_field;

    @FXML private Label lblLoanId, lblMemberId, lblMemberName, lblStaffId, lblBookId, lblIsbn, lblTitle, lblCopyId, lblDateOut, lblDueDate, lblStatus, lblPaymentStatus;
    @FXML private Button btnReturn, btnRenew;

    @FXML private TextField memberField, staffField, bookField, copyField;
    @FXML private DatePicker borrowDatePicker, dueDatePicker;
    @FXML private Button btnCreateLoan;

    private final LoanDAO loanDAO = new LoanDAO();
    private final ObservableList<Loan> loanList = FXCollections.observableArrayList();

    private Loan selected;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        col_loanid.setCellValueFactory(new PropertyValueFactory<>("loan_ID"));
        col_detailid.setCellValueFactory(new PropertyValueFactory<>("detail_ID"));
        col_memid.setCellValueFactory(new PropertyValueFactory<>("member_ID"));
        col_memname.setCellValueFactory(new PropertyValueFactory<>("member_Name"));
        col_staffid.setCellValueFactory(new PropertyValueFactory<>("staffId"));
        col_bookid.setCellValueFactory(new PropertyValueFactory<>("book_ID"));
        col_isbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        col_title.setCellValueFactory(new PropertyValueFactory<>("title"));
        col_copyid.setCellValueFactory(new PropertyValueFactory<>("copy_ID"));
        col_dateout.setCellValueFactory(new PropertyValueFactory<>("date_Out"));
        col_duedate.setCellValueFactory(new PropertyValueFactory<>("dateDue"));
        col_datereturn.setCellValueFactory(new PropertyValueFactory<>("date_Return"));
        col_status.setCellValueFactory(new PropertyValueFactory<>("status"));
        col_paymentstatus.setCellValueFactory(new PropertyValueFactory<>("payment_Status"));

        loan_table.setItems(loanList);
        loadLoans(false, "");

        loan_table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            selected = newSel;
            populateDetails(newSel);
        });

        search_field.setOnAction(e -> doSearch());
        overdueCheckBox.setOnAction(e -> toggleOverdue());
        clearDetails();
    }

    @FXML private void doSearch() {
        loadLoans(overdueCheckBox.isSelected(), search_field.getText());
    }

    @FXML private void toggleOverdue() {
        loadLoans(overdueCheckBox.isSelected(), search_field.getText());
    }

    private void loadLoans(boolean overdueOnly, String keyword) {
        loanList.setAll(loanDAO.getLoans(overdueOnly, keyword));
        clearDetails();
    }

    private void populateDetails(Loan loan) {
        if (loan == null) { clearDetails(); return; }
        lblLoanId.setText(loan.getLoanId());
        lblMemberId.setText(loan.getMemberId());
        lblMemberName.setText(loan.getMemberName());
        lblStaffId.setText(loan.getStaffId());
        lblBookId.setText(loan.getBookId());
        lblIsbn.setText(loan.getIsbn());
        lblTitle.setText(loan.getTitle());
        lblCopyId.setText(loan.getCopyId());
        lblDateOut.setText(loan.getBorrowDate() != null ? loan.getBorrowDate().toString() : "");
        lblDueDate.setText(loan.getDueDate() != null ? loan.getDueDate().toString() : "");
        lblStatus.setText(loan.getStatus());
        lblPaymentStatus.setText(loan.getPaymentStatus());
    }

    private void clearDetails() {
        selected = null;
        Label[] labels = {lblLoanId, lblMemberId, lblMemberName, lblStaffId, lblBookId, lblIsbn, lblTitle, lblCopyId, lblDateOut, lblDueDate, lblStatus, lblPaymentStatus};
        for (Label l : labels) { if (l != null) l.setText(""); }
    }

    @FXML private void returnSelected() {
        if (selected == null) { showError("Please select a loan item to return."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Return Book");
        confirm.setHeaderText("Return copy " + selected.getCopyId() + " of '" + selected.getTitle() + "'?");
        confirm.setContentText("Loan: " + selected.getLoanId() + "  Detail: " + selected.getDetailId());
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                ActionResult res = loanDAO.returnCopy(selected.getLoanId(), selected.getDetailId(), selected.getBookId(), selected.getCopyId(), null);
                if (res.isSuccess()) {
                    showInfo(res.getMessage());
                    loadLoans(overdueCheckBox.isSelected(), search_field.getText());
                } else {
                    showError(res.getMessage());
                }
            }
        });
    }

    @FXML private void renewSelected() {
        if (selected == null) { showError("Please select a loan item to renew."); return; }
        if (!"Borrowing".equals(selected.getStatus())) {
            showError("Only currently borrowed items can be renewed.");
            return;
        }
        ActionResult res = loanDAO.renewLoan(selected.getLoanId(), selected.getDetailId());
        if (res.isSuccess()) { showInfo(res.getMessage()); loadLoans(false, ""); }
        else { showError(res.getMessage()); }
    }

    @FXML private void createLoan() {
        String memberId = memberField.getText() == null ? "" : memberField.getText().trim();
        String staffId = staffField.getText() == null ? "" : staffField.getText().trim();
        String bookId = bookField.getText() == null ? "" : bookField.getText().trim();
        String copyId = copyField.getText() == null ? "" : copyField.getText().trim();
        LocalDate borrowDate = borrowDatePicker.getValue();
        LocalDate dueDate = dueDatePicker.getValue();

        if (memberId.isEmpty() || staffId.isEmpty() || bookId.isEmpty() || copyId.isEmpty()) {
            showError("Member ID, Staff ID, Book ID/ISBN, and Copy ID are required.");
            return;
        }

        ActionResult res = loanDAO.createLoan(memberId, staffId, bookId, copyId, borrowDate, dueDate);
        if (res.isSuccess()) {
            showInfo(res.getMessage());
            memberField.clear(); staffField.clear(); bookField.clear(); copyField.clear();
            borrowDatePicker.setValue(null); dueDatePicker.setValue(null);
            loadLoans(false, "");
        } else {
            showError(res.getMessage());
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Loans");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Loans");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
