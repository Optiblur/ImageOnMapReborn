package pl.iomreborn.image;

import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Writes animated GIFs for the tests, with frame positions, delays and disposal methods. */
final class TestGifs {

    /** One GIF frame: a picture placed at (x, y), shown for {@code delayCs} hundredths of a second. */
    record Frame(BufferedImage image, int x, int y, int delayCs, String disposal) {
        static Frame full(BufferedImage image, int delayCs) {
            return new Frame(image, 0, 0, delayCs, "none");
        }
    }

    private TestGifs() {
    }

    static BufferedImage filled(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    static byte[] write(List<Frame> frames) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (Frame frame : frames) {
                ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(frame.image());
                IIOMetadata metadata = writer.getDefaultImageMetadata(type, null);
                String format = "javax_imageio_gif_image_1.0";
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode descriptor = child(root, "ImageDescriptor");
                descriptor.setAttribute("imageLeftPosition", Integer.toString(frame.x()));
                descriptor.setAttribute("imageTopPosition", Integer.toString(frame.y()));
                IIOMetadataNode control = child(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", frame.disposal());
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(frame.delayCs()));
                if (!control.hasAttribute("transparentColorFlag")) {
                    control.setAttribute("transparentColorFlag", "FALSE");
                    control.setAttribute("transparentColorIndex", "0");
                }
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame.image(), null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /**
     * A GIF written byte by byte, so its frames can claim any size, even far bigger than the GIF itself (which
     * image writers refuse). The frames start at the top-left corner and hold no pixels, only the codes that start
     * and end the picture data, so the file stays tiny whatever sizes the frames claim. Their pixels are
     * transparent.
     *
     * @param frameSizes width and height claimed by every frame
     */
    static byte[] withFrameSizes(int screenWidth, int screenHeight, int[]... frameSizes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("GIF89a".getBytes(StandardCharsets.US_ASCII));
        littleEndian(out, screenWidth);
        littleEndian(out, screenHeight);
        out.write(0x87); // global color table of 256 colors
        out.write(0); // background color
        out.write(0); // pixel aspect ratio
        for (int i = 0; i < 256; i++) {
            out.write(i);
            out.write(i);
            out.write(i);
        }
        for (int[] size : frameSizes) {
            // Shown 100 ms, color 0 is transparent.
            out.writeBytes(new byte[] {0x21, (byte) 0xF9, 0x04, 0x01, 0x0A, 0x00, 0x00, 0x00});
            out.write(0x2C);
            littleEndian(out, 0);
            littleEndian(out, 0);
            littleEndian(out, size[0]);
            littleEndian(out, size[1]);
            out.write(0);
            // Picture data with 8-bit codes: CLEAR (256) and END (257), 9 bits each.
            out.writeBytes(new byte[] {0x08, 0x03, 0x00, 0x03, 0x02, 0x00});
        }
        out.write(0x3B);
        return out.toByteArray();
    }

    private static void littleEndian(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equals(name)) {
                return (IIOMetadataNode) node;
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
