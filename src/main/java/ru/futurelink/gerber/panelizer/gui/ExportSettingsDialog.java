package ru.futurelink.gerber.panelizer.gui;

import io.qt.core.Qt;
import io.qt.gui.QBrush;
import io.qt.gui.QColor;
import io.qt.widgets.*;
import lombok.Getter;
import ru.futurelink.gerber.panelizer.Layer;
import ru.futurelink.gerber.panelizer.batch.BatchSettings;

public class ExportSettingsDialog extends QDialog {
    private final BatchSettings settings = BatchSettings.getInstance();
    private final QTableWidget table;

    public ExportSettingsDialog(QWidget parent) {
        super(parent);

        setWindowTitle("Gerber export settings");
        setMinimumSize(500, 400);
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

    @Override
    public void accept() {
        settings.saveSettings();
        super.accept();
    }

    @Override
    public void reject() {
        settings.loadSettings();
        super.accept();
    }

    private void fillTable() {
        table.setRowCount(settings.getFilePatterns().size());
        table.itemChanged.connect(item -> {
            if (item.column() == 1) {
                var type = Layer.layerNameType(table.item(item.row(), 0).text());
                settings.getFilePatterns().put(type, item.text());
            }
        });
        var row = 0;
        var bg = new QBrush(new QColor(220, 220, 220));
        for (var i : settings.getFilePatterns().keySet()) {
            var item = new QTableWidgetItem(Layer.layerTypeName(i));
            item.setFlags(Qt.ItemFlag.ItemIsEnabled);
            item.setBackground(bg);
            table.setItem(row, 0, item);
            table.setItem(row, 1, new QTableWidgetItem(settings.getFilePatterns().get(i)));
            row++;
        }
    }
}
