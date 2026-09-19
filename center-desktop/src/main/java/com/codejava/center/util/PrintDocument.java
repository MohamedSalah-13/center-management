package com.codejava.center.util;

import javafx.scene.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * مستند للطباعة موصوفاً بقطعه لا بصفحاته.
 *
 * <p>كانت كل مطبوعة تُبنى كـ {@code VBox} واحد يُرسَل إلى {@code printPage}، وهو يطبع ما يسع
 * الصفحة الأولى ويُسقط الباقي بلا أي إشارة: تقرير متأخرات فيه مئتا طالب كان يخرج منه أربعون.
 * بوصف المستند كقائمة قطع يستطيع {@link Printing} أن يوزّعها على صفحات بحيث لا يُقطع سطر
 * في منتصفه.</p>
 *
 * <p>القطعة وحدة لا تتجزأ عند التقسيم - سطر، أو جدول صغير، أو فاصل. فإن أردت ألّا ينفصل
 * عنوانٌ عن أول سطر بعده، اجعلهما قطعة واحدة.</p>
 */
public final class PrintDocument {

    private final DocumentKind kind;
    private final List<Node> blocks = new ArrayList<>();
    private Supplier<Node> headerFactory;

    private PrintDocument(DocumentKind kind) {
        this.kind = kind;
    }

    /** تقرير على ورق مقطوع: يُقسَّم على صفحات وتُرقَّم */
    public static PrintDocument report() {
        return new PrintDocument(DocumentKind.REPORT);
    }

    /** إيصال على رول متصل: صفحة واحدة مهما طالت */
    public static PrintDocument receipt() {
        return new PrintDocument(DocumentKind.RECEIPT);
    }

    /**
     * ترويسة تُعاد في أعلى كل صفحة.
     *
     * <p>الوسيط {@link Supplier} لا {@link Node}: العقدة الواحدة لا تُضاف إلى أكثر من أب في
     * JavaFX، فلو مرَّرنا عقدة جاهزة لظهرت الترويسة في الصفحة الأخيرة وحدها بعد أن تنتزعها
     * كل صفحة من سابقتها.</p>
     */
    public PrintDocument header(Supplier<Node> factory) {
        this.headerFactory = factory;
        return this;
    }

    public PrintDocument add(Node... nodes) {
        Collections.addAll(blocks, nodes);
        return this;
    }

    public PrintDocument add(Collection<? extends Node> nodes) {
        blocks.addAll(nodes);
        return this;
    }

    DocumentKind kind() {
        return kind;
    }

    List<Node> blocks() {
        return blocks;
    }

    Supplier<Node> headerFactory() {
        return headerFactory;
    }
}
