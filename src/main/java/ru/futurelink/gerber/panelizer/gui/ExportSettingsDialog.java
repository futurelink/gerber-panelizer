package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.Qt;
import io.qt.widgets.*;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.batch.BatchSettings;

public class ExportSettingsDialog extends QDialog {
    @Getter private final BatchSettings settings;

    private final QTableWidget table;

    public ExportSettingsDialog(QWidget parent, BatchSettings settings) {
        super(parent);
        this.settings = settings;

        setWindowTitle("Gerber export settings");
        setMinimumSize(500, 300);
        setLayout(new QVBoxLayout(this));

        table = new QTableWidget(this);
        table.setColumnCount(2);
        table.horizontalHeader().setStretchLastSection(true);
        table.verticalHeader().hide();
        table.setHorizontalHeaderItem(0, new QTableWidgetItem("Layer type"));
        table.setHorizontalHeaderItem(1, new QTableWidgetItem("File name pattern"));
        layout().addWidget(table);

        var saveBtn = new QPushButton("Save");
        saveBtn.clicked.connect(this, "accept()");

        var cancelBtn = new QPushButton("Cancel");
        cancelBtn.clicked.connect(this, "reject()");

        var buttonBox = new QDialogButtonBox(Qt.Orientation.Horizontal);
        buttonBox.addButton(saveBtn, QDialogButtonBox.ButtonRole.AcceptRole);
        buttonBox.addButton(cancelBtn, QDialogButtonBox.ButtonRole.RejectRole);
        layout().addWidget(buttonBox);

        fillTable();
    }

    private void fillTable() {
        table.setRowCount(settings.getFilePatterns().size()+1);
        var row = 0;
        for (var i : settings.getFilePatterns().keySet()) {
            table.setItem(row, 0, new QTableWidgetItem(Layer.layerTypeName(i)));
            table.setItem(row, 1, new QTableWidgetItem(settings.getFilePatterns().get(i)));
            row++;
        }
    }
}
