package edu.cmu.imageprocessing;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class EdgeDetectionTrial {

	
	public static void main(String args[]) {
		int arg = 0;
		String outArg = args[arg++];
		String inArg = args[arg++];
		if (args.length != arg) {
			throw new IllegalArgumentException("Invalid number of arguments");
		}

		System.out.println("Image conversion to " + outArg + " from " + inArg);
		
		try {
			BufferedImage outImage = EdgeDetectionTrial.detectEdges(inArg);
			EdgeDetectionTrial.save(outImage, outArg);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private static void save(BufferedImage image, String fileName) {
		String ext = fileName.substring(fileName.lastIndexOf(".")+1, fileName.length());
    File file = new File(fileName);
    try {
        ImageIO.write(image, ext, file);  // ignore returned boolean
    } catch(IOException e) {
        System.out.println("Write error for " + file.getPath() +
                           ": " + e.getMessage());
    }
}

	private static BufferedImage toBufferedImage(Image src) {
    int w = src.getWidth(null);
    int h = src.getHeight(null);
    int type = BufferedImage.TYPE_INT_RGB;  // other options
    BufferedImage dest = new BufferedImage(w, h, type);
    Graphics2D g2 = dest.createGraphics();
    g2.drawImage(src, 0, 0, null);
    g2.dispose();
    return dest;
}
	private static BufferedImage detectEdges(String fileName) throws IOException {
		BufferedImage inIname = toBufferedImage(ImageIO.read(new File(fileName)));
	//create the detector
		CannyEdgeDetector detector = new CannyEdgeDetector();

		//adjust its parameters as desired
		detector.setLowThreshold(0.1f);
		detector.setHighThreshold(0.5f);

		//apply it to an image
		detector.setSourceImage(inIname);
		detector.process();
		return detector.getEdgesImage();
	}
}
