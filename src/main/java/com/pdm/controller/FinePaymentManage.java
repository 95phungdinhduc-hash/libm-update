package com.pdm.controller;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

import com.pdm.dao.FineDAO;
import com.pdm.dao.PaymentDAO;
import com.pdm.dao.PaymentDAO.ActionResult;
import com.pdm.model.Fine;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

public class FinePaymentManage extends basecontrol implements Initializable {

    @FXML private TableView<Fine> fine_table;
    @FXML private TableColumn<Fine, String> col_loanid, col_memname, col_title, col_copyid, col_duedate, col_returndate, col_status, col_fine_amount;
    @FXML private ChoiceBox<String> payment_select, methodChoice;
    @FXML private TextField search_field, payAmountField;
    @FXML private DatePicker payDatePicker;
    @FXML private Button btnProcess, btnUpdate, btnWaive;

    private final FineDAO fineDAO = new FineDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final ObservableList<Fine> fine_list = FXCollections.observableArrayList();
    private Fine selected;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        col_loanid.setCellValueFactory(new PropertyValueFactory<>("Loan_ID"));
        col_memname.setCellValueFactory(new PropertyValueFactory<>("Member_Name"));
        col_title.setCellValueFactory(new PropertyValueFactory<>("Book_Title"));
        col_copyid.setCellValueFactory(new PropertyValueFactory<>("Copy_ID"));
        col_duedate.setCellValueFactory(new PropertyValueFactory<>("Due_Date"));
        col_returndate.setCellValueFactory(new PropertyValueFactory<>("Return_Date"));
        col_status.setCellValueFactory(new PropertyValueFactory<>("Payment_Status"));
        col_fine_amount.setCellValueFactory(new PropertyValueFactory<>("Fine_Amount"));
        fine_table.setItems(fine_list);
        loadFines("");
        payment_select.getItems().addAll("Cash", "Bank transfer", "Card");
        payment_select.setValue("Cash");
        methodChoice.getItems().addAll("Cash", "Bank transfer", "Card");
        methodChoice.setValue("Cash");
        fine_table.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            selected = newV;
            if (newV != null) {
                payAmountField.setText(newV.getFine_Amount() != null ? newV.getFine_Amount().toString() : "0");
            } else { payAmountField.setText(""); }
        });
        search_field.setOnAction(e -> doSearch());
        btnProcess.setDisable(true);
        btnUpdate.setDisable(true);
        btnWaive.setDisable(true);
    }

    @FXML private void doSearch() { loadFines(search_field.getText()); }

    private void loadFines(String keyword) {
        fine_list.setAll(fineDAO.search_fines(keyword));
        selected = null;
        btnProcess.setDisable(true);
        btnUpdate.setDisable(true);
        btnWaive.setDisable(true);
        payAmountField.setText("");
    }

    @FXML private void processPayment() {
        if (selected == null) { showError("Please select a fine record first."); return; }
        BigDecimal amount;
        try {
            amount = new BigDecimal(payAmountField.getText() == null ? "0" : payAmountField.getText().trim());
        } catch (NumberFormatException e) { showError("Please enter a valid numeric pay amount."); return; }
        LocalDate payDate = payDatePicker.getValue();
        String method = methodChoice.getValue();
        ActionResult res = paymentDAO.processPayment(selected.getLoan_ID(), selected.getDetail_ID(), amount, payDate, method);
        if (res.isSuccess()) { showInfo(res.getMessage()); loadFines(search_field.getText()); }
        else { showError(res.getMessage()); }
    }

    @FXML private void updateStatus() {
        if (selected == null) { showError("Please select a fine record first."); return; }
        ActionResult res = paymentDAO.updateFineStatus(selected.getLoan_ID(), selected.getDetail_ID(), payment_select.getValue());
        if (res.isSuccess()) { showInfo(res.getMessage()); loadFines(search_field.getText()); }
        else { showError(res.getMessage()); }
    }

    @FXML private void waiveFine() {
        if (selected == null) { showError("Please select a fine record first."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Waive Fine");
        confirm.setHeaderText("Waive fine for Loan " + selected.getLoan_ID() + " Detail " + selected.getDetail_ID() + "?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                ActionResult res = paymentDAO.waiveFine(selected.getLoan_ID(), selected.getDetail_ID());
                if (res.isSuccess()) { showInfo(res.getMessage()); loadFines(search_field.getText()); }
                else { showError(res.getMessage()); }
            }
        });
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Fines");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Fines");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
