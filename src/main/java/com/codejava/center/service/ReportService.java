package com.codejava.center.service;

import com.codejava.center.domain.Teacher;
import javafx.print.PrinterJob;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ReportService {

    public void printTeacherStatement(Teacher teacher, Window ownerWindow) {
        PrinterJob job = PrinterJob.createPrinterJob();

        if (job != null && job.showPrintDialog(ownerWindow)) {
            // بناء تصميم الإيصال/التقرير للطباعة
            VBox printableNode = new VBox(15);
            printableNode.setStyle("-fx-padding: 30; -fx-background-color: white;");

            Label header = new Label("كشف حساب معلم - السنتر التعليمي");
            header.setFont(Font.font("System", FontWeight.BOLD, 24));

            Label dateLabel = new Label("تاريخ الإصدار: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

            Label teacherInfo = new Label(
                    String.format("الاسم: %s\nالمادة: %s\nنوع العمولة: %s\nقيمة العمولة: %s",
                            teacher.getName(), teacher.getSubject(), teacher.getCommissionType(), teacher.getCommissionValue())
            );
            teacherInfo.setFont(Font.font("System", 16));

            // يمكن لاحقاً حقن SessionRepository هنا لجلب إجمالي الحصص وإيراداتها
            Label summary = new Label("\n-- تفاصيل الحصص المالية ستدرج هنا لاحقاً --");

            printableNode.getChildren().addAll(header, dateLabel, new javafx.scene.control.Separator(), teacherInfo, summary);

            // تنفيذ الطباعة
            boolean success = job.printPage(printableNode);
            if (success) {
                job.endJob();
            }
        }
    }
}