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
