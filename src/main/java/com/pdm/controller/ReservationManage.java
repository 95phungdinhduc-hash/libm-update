package com.pdm.controller;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

import com.pdm.dao.ReservationDAO;
import com.pdm.dao.ReservationDAO.ActionResult;
import com.pdm.model.Reservation;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

public class ReservationManage extends basecontrol implements Initializable {

    @FXML private TableView<Reservation> reser_table;
    @FXML private TableColumn<Reservation, String> col_id;
    @FXML private TableColumn<Reservation, String> col_title;
    @FXML private TableColumn<Reservation, String> col_memid;
    @FXML private TableColumn<Reservation, String> col_memname;
    @FXML private TableColumn<Reservation, String> col_status;
    @FXML private TableColumn<Reservation, java.sql.Date> col_date;

    @FXML private Button cancel_btn, fulfillReservation_btn, add_btn;
    @FXML private TextField search_field, memberIdField, bookField, staffField;
    @FXML private DatePicker reservationDatePicker;
    @FXML private ChoiceBox<String> copyChoice;

    private final ReservationDAO reservationDAO = new ReservationDAO();
    private final ObservableList<Reservation> reserList = FXCollections.observableArrayList();

    private Reservation selected;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        col_id.setCellValueFactory(new PropertyValueFactory<>("id"));
        col_title.setCellValueFactory(new PropertyValueFactory<>("title"));
        col_memid.setCellValueFactory(new PropertyValueFactory<>("memid"));
        col_memname.setCellValueFactory(new PropertyValueFactory<>("memname"));
        col_status.setCellValueFactory(new PropertyValueFactory<>("status"));
        col_date.setCellValueFactory(new PropertyValueFactory<>("date"));

        reser_table.setItems(reserList);
        loadReservations("");

        reser_table.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            selected = newV;
            boolean has = newV != null;
            cancel_btn.setDisable(!has);
            fulfillReservation_btn.setDisable(!has);
            if (has) copyChoice.getItems().clear();
        });

        search_field.setOnAction(e -> doSearch());
        cancel_btn.setDisable(true);
        fulfillReservation_btn.setDisable(true);
    }

    @FXML private void doSearch() { loadReservations(search_field.getText()); }

    private void loadReservations(String keyword) {
        reserList.setAll(reservationDAO.searchReser(keyword));
        boolean has = !reserList.isEmpty() && selected != null;
        cancel_btn.setDisable(!has);
        fulfillReservation_btn.setDisable(!has);
    }

    @FXML private void cancel() {
        Reservation r = reser_table.getSelectionModel().getSelectedItem();
        if (r == null) { showError("Please select a reservation to cancel."); return; }
        if (!"Pending".equals(r.getStatus()) && !"Confirmed".equals(r.getStatus())) {
            showError("Only Pending or Confirmed reservations can be canceled."); return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Reservation");
        confirm.setHeaderText("Cancel reservation " + r.getId() + " for '" + r.getTitle() + "'?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                boolean ok = reservationDAO.cancelReser(r.getId());
                if (ok) { showInfo("Reservation canceled successfully."); loadReservations(search_field.getText()); }
                else { showError("Failed to cancel reservation."); }
            }
        });
    }

    @FXML private void addReservation() {
        String memberId = memberIdField.getText() == null ? "" : memberIdField.getText().trim();
        String bookKey = bookField.getText() == null ? "" : bookField.getText().trim();
        LocalDate date = reservationDatePicker.getValue();
        ActionResult res = reservationDAO.addReservation(memberId, bookKey, date);
        if (res.isSuccess()) {
            showInfo(res.getMessage());
            memberIdField.clear(); bookField.clear(); reservationDatePicker.setValue(null);
            loadReservations(search_field.getText());
        } else { showError(res.getMessage()); }
    }

    @FXML private void fulfillReservation() {
        Reservation r = reser_table.getSelectionModel().getSelectedItem();
        if (r == null) { showError("Please select a reservation to fulfill."); return; }
        String copyId = copyChoice.getValue();
        String staffId = staffField.getText() == null ? "" : staffField.getText().trim();
        if (copyId == null || copyId.isBlank()) { showError("Please select a copy to fulfill."); return; }
        if (staffId.isBlank()) { showError("Please enter the handling staff ID."); return; }
        ActionResult res = reservationDAO.fulfillReservation(r.getId(), r.getMemid(), r.getBookid(), copyId, staffId);
        if (res.isSuccess()) {
            showInfo(res.getMessage());
            copyChoice.getItems().clear(); staffField.clear();
            loadReservations(search_field.getText());
        } else { showError(res.getMessage()); }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Reservations");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Reservations");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
