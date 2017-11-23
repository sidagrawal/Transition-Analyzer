package application;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.opencv.core.Mat;

import utilities.Utilities;

class Hist {
	
	private float[][] hist;
	private int size;
	
	public Hist(int x) {
		size = x;

		hist = new float[size][size];
	}
	
	public float[][] getHist( ) {
		return hist;
	}
	
	public void add(int x, int y) {
		hist[x][y] += 1.0;
		
	}
	
	public void printHist() {
		for (int i=0;i<size;i++) {
			for (int j=0;j<size;j++) {
				System.out.print(hist[i][j]);
				System.out.print("  ");
			}
			System.out.println(" ");
		}
		System.out.println("end of the hist");
	}
	
	public void columnHist(Mat original, int frameHeight, int frameWidth, int column) {
		BufferedImage image = Utilities.matToBufferedImage(original);
//		System.out.println("height is "+ frameHeight);
//		System.out.println("column is "+ column);
	    	for (int i=0;i<frameHeight;i++) {
	    		
//	    		System.out.println("i is " +i);
//	    		int c = image.getRGB(i,column);
//	    		System.out.println(c);
		    	Color rgb = new Color(image.getRGB(column, i));
		    
		    	float[] rg = getRG(rgb);
		    	
		    	int[] position = getPosition(rg, size);
		    	
		    	this.add(position[0], position[1]);
		    	
//		    	System.out.println(i);
		    } 
	    		
//	    		this.printHist();
	    	
	}
	
	public int getSize() {
		return size;
	}
	
	
	private int[] getPosition(float[] rg, int bins) {
		int[] position = new int[2];
    	int x = (int) Math.ceil(rg[0] * bins) - 1;
    	int y = (int) Math.ceil(rg[1] * bins) - 1;
    	if (x == -1) {
    		x = 0;
    	}
    	if (y == -1) {
    		y = 0;
    	}
    	position[0] = x;
    	position[1] =y;
    	return position;
	}
	
	private float[] getRG(Color rgb) {
		float[] rg = new float[2];
		float r;
		float g;
		int red = rgb.getRed();
    	int green = rgb.getGreen();
    	int blue = rgb.getBlue();
    	int total = red + green + blue;
//    	System.out.println("total:" + total);
    	if (total == 0) {
    		r = 0;
    		g = 0;
    	} else {
	    	r = ((float) red)/total;
	    	g = ((float) green)/total;
    	}
    	rg[0] = r;
    	rg[1] = g;
    	return rg;
	}

}
