import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * مولّد أيقونة البرنامج: قبعة تخرّج فوق كتاب مفتوح.
 *
 * <p>يُشغَّل يدوياً عند الحاجة لتعديل الأيقونة، وليس جزءاً من بناء Maven:</p>
 *
 * <pre>
 *   java packaging\IconBuilder.java
 * </pre>
 *
 * <p>ينتج مخرجين: ملف {@code packaging/app.ico} لأيقونة الملف التنفيذي، وصور
 * PNG داخل موارد المشروع لأيقونة النافذة أثناء التشغيل ({@code util.AppIcons}).</p>
 *
 * <p>كل مقاس يُرسم مستقلاً بإحداثيات متجهة ثم يُقاس، بدل تصغير صورة واحدة —
 * الفرق واضح عند 16 و 24 بكسل حيث يعطي التصغير حوافّ ضبابية.</p>
 */
public class IconBuilder {

    static final Color BRAND      = new Color(0x1D4ED8);
    static final Color BRAND_DARK = new Color(0x1E3A8A);
    static final Color GOLD       = new Color(0xF59E0B);
    static final Color GOLD_LIGHT = new Color(0xFBBF24);
    static final Color PAPER      = new Color(0xFFFFFF);
    static final Color LINE       = new Color(0xCBD5E1);

    static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};
    static final int PNG_FROM = 128;   // هذا المقاس فأعلى يُخزَّن داخل ico كـ PNG

    static final String ICO_PATH = "packaging/app.ico";
    static final String PNG_DIR  = "src/main/resources/img";

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".").getCanonicalFile();

        // ---- ملف الأيقونة للملف التنفيذي ----
        File ico = new File(root, ICO_PATH);
        ico.getParentFile().mkdirs();

        List<byte[]> payloads = new ArrayList<>();
        for (int size : SIZES) {
            BufferedImage image = render(size);
            payloads.add(size >= PNG_FROM ? toPng(image) : toDib(image));
        }
        writeIco(ico, SIZES, payloads);
        System.out.println("wrote " + ico + " (" + ico.length() + " bytes)");

        // ---- صور PNG لأيقونة النافذة داخل البرنامج ----
        File pngDir = new File(root, PNG_DIR);
        pngDir.mkdirs();
        for (int size : SIZES) {
            File png = new File(pngDir, "icon-" + size + ".png");
            ImageIO.write(render(size), "png", png);
            System.out.println("wrote " + png.getName());
        }
    }

    // ------------------------------------------------------------- الرسم

    static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 256.0, size / 256.0);

        boolean tiny = size <= 24;

        // ظل خفيف — يُحذف في المقاسات الصغيرة حيث يتحول إلى ضبابية
        if (!tiny) {
            g.setColor(new Color(0, 0, 0, 26));
            g.fill(new RoundRectangle2D.Double(44, 40, 168, 168, 20, 20));
        }

        // خلفية الشارة
        RoundRectangle2D badge = new RoundRectangle2D.Double(28, 24, 200, 200, tiny ? 22 : 32, tiny ? 22 : 32);
        g.setColor(BRAND);
        g.fill(badge);

        // الكتاب المفتوح — صفحتان تلتقيان عند المنتصف
        g.setColor(PAPER);
        Path2D left = new Path2D.Double();
        left.moveTo(60, 168);
        left.curveTo(90, 152, 118, 152, 128, 168);
        left.lineTo(128, 210);
        left.curveTo(118, 196, 90, 196, 60, 210);
        left.closePath();
        g.fill(left);

        Path2D right = new Path2D.Double();
        right.moveTo(196, 168);
        right.curveTo(166, 152, 138, 152, 128, 168);
        right.lineTo(128, 210);
        right.curveTo(138, 196, 166, 196, 196, 210);
        right.closePath();
        g.fill(right);

        if (!tiny) {
            g.setColor(LINE);
            g.setStroke(new BasicStroke(4));
            g.draw(new Line2D.Double(128, 168, 128, 210));
        }

        // قبعة التخرّج فوق الكتاب
        Polygon cap = new Polygon(
                new int[]{128, 208, 128, 48},
                new int[]{78, 106, 134, 106}, 4);
        g.setColor(BRAND_DARK);
        g.fill(cap);

        // قاعدة القبعة (الجزء المكعّب أسفل اللوح)
        g.fill(new RoundRectangle2D.Double(102, 106, 52, tiny ? 20 : 26, 8, 8));

        // الشُّرّابة الذهبية
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(tiny ? 8 : 6));
        g.draw(new Line2D.Double(208, 106, 208, tiny ? 146 : 152));
        g.setColor(GOLD_LIGHT);
        g.fill(new Ellipse2D.Double(200, (tiny ? 146 : 152) - 8, 16, 16));

        g.dispose();
        return image;
    }

    // ------------------------------------------------------- كتابة الـ ico

    static byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * صورة DIB بعمق 32 بت: ترويسة، ثم بيانات BGRA من الأسفل للأعلى، ثم قناع AND
     * صفري — الشفافية تأتي من قناة ألفا لكن البنية تتطلب وجود القناع.
     */
    static byte[] toDib(BufferedImage image) throws Exception {
        int w = image.getWidth(), h = image.getHeight();
        int maskStride = ((w + 31) / 32) * 4;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);

        writeInt(data, 40);            // biSize
        writeInt(data, w);             // biWidth
        writeInt(data, h * 2);         // biHeight = XOR + AND
        writeShort(data, 1);           // biPlanes
        writeShort(data, 32);          // biBitCount
        writeInt(data, 0);             // biCompression = BI_RGB
        writeInt(data, w * h * 4 + maskStride * h);
        writeInt(data, 0);             // biXPelsPerMeter
        writeInt(data, 0);             // biYPelsPerMeter
        writeInt(data, 0);             // biClrUsed
        writeInt(data, 0);             // biClrImportant

        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                data.writeByte(argb & 0xFF);           // B
                data.writeByte((argb >> 8) & 0xFF);    // G
                data.writeByte((argb >> 16) & 0xFF);   // R
                data.writeByte((argb >> 24) & 0xFF);   // A
            }
        }
        data.write(new byte[maskStride * h]);
        data.flush();
        return out.toByteArray();
    }

    static void writeIco(File target, int[] sizes, List<byte[]> payloads) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(target))) {
            writeShort(out, 0);              // محجوز
            writeShort(out, 1);              // النوع = أيقونة
            writeShort(out, sizes.length);

            int offset = 6 + 16 * sizes.length;
            for (int i = 0; i < sizes.length; i++) {
                int size = sizes[i];
                out.writeByte(size >= 256 ? 0 : size);   // 0 تعني 256
                out.writeByte(size >= 256 ? 0 : size);
                out.writeByte(0);            // لوحة ألوان غير مستخدمة
                out.writeByte(0);            // محجوز
                writeShort(out, 1);          // planes
                writeShort(out, 32);         // bit count
                writeInt(out, payloads.get(i).length);
                writeInt(out, offset);
                offset += payloads.get(i).length;
            }
            for (byte[] payload : payloads) {
                out.write(payload);
            }
        }
    }

    static void writeShort(DataOutputStream out, int value) throws Exception {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
    }

    static void writeInt(DataOutputStream out, int value) throws Exception {
        out.writeByte(value & 0xFF);
        out.writeByte((value >> 8) & 0xFF);
        out.writeByte((value >> 16) & 0xFF);
        out.writeByte((value >> 24) & 0xFF);
    }
}
