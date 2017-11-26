package application;

import java.awt.Color;
import application.Hist;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.sound.sampled.LineUnavailableException;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	
	@FXML
	private Slider slider = new Slider();  //progress bar
	@FXML
	private Button playbutton = new Button();
	@FXML
	private Text textbox = new Text();
	@FXML
	public TextField thresholdValue = new TextField();
	
	private ScheduledExecutorService timer1;
	private int index = 0;    //index for naming the STI images
	private String filepath;
	private VideoCapture capture;
	private Hist[] currentHists;   //array storing the histogram for the column in the current frame
	private Hist[] preHists;       //array storing the histogram for the column in the previous frame
	private float threshold;       //threshold for creating the binary STI image
	private int width;
	private int height;
	
	@FXML
	private void initialize() {
		// initialize everything
		width = 32;
		height = 32;
		threshold = (float) 0.7;   //default threshold
		thresholdValue.setText(Float.toString(threshold));
		timer1 = Executors.newSingleThreadScheduledExecutor();
	}
	
	private String getVideoFilename() {
		// This method should return the filename of the video to be played
		 FileChooser fileChooser = new FileChooser();
		 fileChooser.setTitle("Open Resource File");
		 fileChooser.getExtensionFilters().addAll(
				 new ExtensionFilter("Video Files", "*.mp4", "*.mpeg", "*.avi"),
				 new ExtensionFilter("Audio Files", "*.wav", "*.mp3", "*.aac"),
		         new ExtensionFilter("All Files", "*.*"));
		 File selectedFile = fileChooser.showOpenDialog(null);
		 if (selectedFile == null) {
			 return "empty";
		 }
		return selectedFile.getAbsolutePath();
	}
	
	private void shutdown(ScheduledExecutorService timer) throws InterruptedException {
		//shut down the thread
		if (timer != null && !timer.isShutdown()) {
			timer.shutdown();
			timer.awaitTermination(Math.round(1), TimeUnit.MILLISECONDS);
		}
		imageView.setVisible(false);
	}
	
	@FXML
	protected void openVideo(ActionEvent event) throws InterruptedException {
		// This method opens an Video and display it using the GUI
		filepath = getVideoFilename();
		if (filepath != "empty") {
			shutdown(timer1);
			capture = new VideoCapture(filepath); // open video file
			playbutton.setDisable(false);
			playbutton.setText("Analyze");
			textbox.setText("Press Analyze");
			textbox.setVisible(true);
		}
	}
	
	@FXML
	protected void createFrameGrabber() throws InterruptedException {
		if (capture != null && capture.isOpened()) { // the video must be open
			double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
			int frameHeight = 32;    //resize parameter
			int frameWidth = 32;     //resize parameter
			int frameCount = (int) totalFrameCount;
			double framePerSecond = 48;
			// 2D array storing center column pixels from each frame of the video
			int[][] STI_vertical = new int[frameCount][frameHeight];
			// 2D array storing center row pixels from each frame of the video
			int[][] STI_horizontal = new int[frameCount][frameWidth];
			// 2D array storing histogram difference computed with current histogram and previous histogram
			float[][] STI_histdiff = new float[frameCount][frameWidth];
			
			// create a runnable to fetch new frames periodically
			Runnable frameGrabber = new Runnable() {
				@Override
				public void run() {
					Mat frame2 = new Mat();
					if (capture.read(frame2)) { // decode successfully
						Mat frame = new Mat();
						Imgproc.resize(frame2, frame, new Size(width, height));   //resize the frame
						Image im = Utilities.mat2Image(frame);
						Utilities.onFXThread(imageView.imageProperty(), im);
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						int currentframe = (int) currentFrameNumber;
						// grab center column from each frame
						columnGrabber(frame, STI_vertical[currentframe - 1], frameHeight, frameWidth);
						// grab center row from each frame
						rowGrabber(frame, STI_horizontal[currentframe - 1], frameWidth, frameHeight);
						// if it is the first frame, set current frame and previous frame both to be the fist frame 
						if (currentframe == 1) {
							currentHists = frameHist(frame, frameHeight, frameWidth);
							preHists = currentHists;
						}
						// otherwise, just as normal
						preHists = currentHists;
						currentHists = frameHist(frame, frameHeight, frameWidth);
						// diffs stores histogram differences for one frame
						float[] diffs = computediff(currentHists, preHists, frameWidth);
						for (int i=0;i<frameWidth;i++) {
							STI_histdiff[currentframe-1][i] = diffs[i];
						}
						slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
					} else { // reach the end of the video
						// 2D arrays storing RGB values for each pixel to create STI images
						int[][] greyscale = createImage(STI_histdiff, frameCount, frameWidth);
						int[][] binary = createBinary(STI_histdiff, frameCount, frameWidth, threshold);
						timer1.shutdown();
						// update the GUI when the video analysis is finished
						Platform.runLater(new Runnable()
						{
						@Override
						public void run() {
						   index += 1;
						   playbutton.setText("Done");
						   imageView.setVisible(false);
						   textbox.setText("Please open another video");
						   textbox.setVisible(true);
						   playbutton.setDisable(true);
						}
						});
						// create vertical STI image
						BufferedImage VImage = new BufferedImage(frameCount, frameHeight, BufferedImage.TYPE_INT_ARGB);
						for (int i=0;i<frameCount;i++) {
							for (int j=0;j<frameHeight;j++) {
								Color c = new Color(STI_vertical[i][j], true);
								VImage.setRGB(i, j, c.getRGB());
							}
						}
						// create horizontal STI image
						BufferedImage HImage = new BufferedImage(frameCount, frameWidth, BufferedImage.TYPE_INT_ARGB);
						for (int i=0;i<frameCount;i++) {
							for (int j=0;j<frameWidth;j++) {
								Color c = new Color(STI_horizontal[i][j], true);
								HImage.setRGB(i, j, c.getRGB());
							}
						}
						// create histogram difference image
						BufferedImage HistImage = new BufferedImage(frameCount, frameWidth, BufferedImage.TYPE_INT_RGB);
						for (int i=0;i<frameCount;i++) {
							for (int j=0;j<frameWidth;j++) {
								int value = greyscale[i][j];
								Color c = new Color(value, value, value);
								HistImage.setRGB(i, j, c.getRGB());
							}
						}
						// create threshold version of histogram difference image
						BufferedImage BinaryImage = new BufferedImage(frameCount, frameWidth, BufferedImage.TYPE_INT_RGB);
						for (int i=0;i<frameCount;i++) {
							for (int j=0;j<frameWidth;j++) {
								int value = binary[i][j];
								Color c = new Color(value, value, value);
								BinaryImage.setRGB(i, j, c.getRGB());
							}
						}						
						// write out the images
						try {
							ImageIO.write(VImage, "png", new File("STI_VImage_" + Integer.toString(index) + ".png"));
							ImageIO.write(HImage, "png", new File("STI_HImage_" + Integer.toString(index) + ".png"));
							ImageIO.write(HistImage, "png", new File("STI_HistImage_" + Integer.toString(index) + ".png"));
							ImageIO.write(BinaryImage, "png", new File("STI_BinaryImage_" +Float.toString(threshold) + "_" + Integer.toString(index) + ".png"));
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			};
			
		// terminate the timer if it is running
			if (timer1 != null && !timer1.isShutdown()) {
				timer1.shutdown();
				timer1.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			}
			// run the frame grabber
			timer1 = Executors.newSingleThreadScheduledExecutor();
			timer1.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
			}
		}
	
	protected void columnGrabber(Mat original, int[] rgbs, int frameHeight, int frameWidth) {
		// grab the center column of the frame and store the RGB value of the pixels into an array 
		BufferedImage image = Utilities.matToBufferedImage(original);
	    for (int i=0;i<frameHeight;i++) {
	    	rgbs[i] = image.getRGB(frameWidth/2, i);
	    }	    
	}
	
	protected void rowGrabber(Mat original, int[] rgbs, int frameWidth, int frameHeight) {
		// grab the center row of the frame and store the RGB value of the pixels into an array
		BufferedImage image = Utilities.matToBufferedImage(original);
	    for (int i=0;i<frameWidth;i++) {
	    	rgbs[i] = image.getRGB(i, frameHeight/2);
	    }
	}
	
	protected Hist[] frameHist(Mat original, int frameHeight, int frameWidth) {
		// calculate the histogram difference for one frame
		int bins =(int) Math.floor(1.0 +Math.log10((double) frameHeight)/Math.log10(2.0));
		Hist[] hists = new Hist[frameWidth];
		for (int i=0;i<frameWidth;i++) {
			Hist hist = new Hist(bins);
			hist.columnHist(original, frameHeight, frameWidth, i);
			convertor(hist, frameHeight);
			hists[i] = hist;
		}
		return hists;
	}
	
	protected void convertor(Hist ahist, int frameHeight) {
		// normalize the histogram such that the sum of all entries is 1
		for (int i=0;i<ahist.getSize();i++) {
			for (int j=0;j<ahist.getSize();j++) {
				ahist.getHist()[i][j] = ahist.getHist()[i][j] / frameHeight;
			}
		}
	}
	
	protected float[] computediff(Hist[] current, Hist[] pre, int frameWidth) {
		// compute the histogram difference using current frame and previous frame and 
		// return back an array storing differences for one frame
		float[] diffs = new float[frameWidth];
		for (int k=0;k<frameWidth;k++) {
			for (int i=0;i<current[k].getSize();i++) {
				for (int j=0;j<current[k].getSize();j++) {
					if (current[k].getHist()[i][j] <= pre[k].getHist()[i][j]) {
						diffs[k] += current[k].getHist()[i][j];
				} else {
					diffs[k] += pre[k].getHist()[i][j];
				}
				}
			}
		}	
		return diffs;
	}
	
	protected int[][] createImage(float[][] bytes, int frameCount, int frameWidth) {
		// convert the histogram differences to RGB values
		int[][] greyscale = new int[frameCount][frameWidth];
		for (int i=0;i<frameCount;i++) {
			for (int j=0;j<frameWidth;j++) {
				greyscale[i][j] =(int) Math.ceil(bytes[i][j] * 255);
			}
		}
		return greyscale;
	}
	
	protected int[][] createBinary(float[][] bytes, int frameCount, int frameWidth, float threshold) {
		// divide the values into 0 and 1
		int[][] binary = new int[frameCount][frameWidth];
		for (int i=0;i<frameCount;i++) {
			for (int j=0;j<frameWidth;j++) {
				if (bytes[i][j] >= threshold) {
					binary[i][j] = 255;
				} else {
					binary[i][j] = 0;
				}
			}
		}
		return binary;
	}
	
	@FXML
	protected void playVideo(ActionEvent event) throws LineUnavailableException, InterruptedException {
		// play the video when the user clicks "analyze"
		if (capture.isOpened()) {
			// open successfully
			if (playbutton.getText() == "Stop") {
				shutdown(timer1);
				capture = new VideoCapture(filepath);
				playbutton.setText("Analyze");
				textbox.setVisible(true);
				return ;
			}
			String textValue = thresholdValue.getText();
			if (textValue.matches("0(\\.\\d+)?|1(\\.0)?")) {
				threshold = Float.parseFloat(textValue);
				playbutton.setText("Stop");
				textbox.setVisible(false);
				imageView.setVisible(true);
				createFrameGrabber();
			} else {
				new Alert(Alert.AlertType.ERROR, "Please insert threshold value between 0 and 1").showAndWait();
			}
		}
	}
	
	@FXML
	protected void changeThreshold(KeyEvent event) {
		// change the threshold for creating binary STI image when the user submits the input
		if (event.getCode().equals(KeyCode.ENTER)) {
			String textValue = thresholdValue.getText();
			if (textValue.matches("0(\\.\\d+)?|1(\\.0)?")) {
				threshold = Float.parseFloat(textValue);
			} else {
				new Alert(Alert.AlertType.ERROR, "Please insert threshold value between 0 and 1").showAndWait();
			}
		}
	}
  }
