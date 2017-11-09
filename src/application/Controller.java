package application;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	private Mat image;
	
	@FXML
	private Slider slider = new Slider();
	@FXML
	private Button playbutton = new Button();
	@FXML
	private Text textbox = new Text();
	@FXML
	private Slider volumeAdjuster = new Slider();
	
	private ScheduledExecutorService timer1;
	private ScheduledExecutorService timer2;
	
//	private Boolean flag = true;
	
	
	private VideoCapture capture;
	
	private int width;
	private int height;
	private int center;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		width = 32;
		height = 32;
		center = 15;
		sampleRate = 8000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 125;
		
		timer1 = Executors.newSingleThreadScheduledExecutor();
		timer2 = Executors.newSingleThreadScheduledExecutor();

		volumeAdjuster.setValue(volumeAdjuster.getMax()/2);
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
	}
	
	private String getImageFilename() {
		// This method should return the filename of the image to be played
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
		
		//return "resources/test.mp4";
	}
	
	private void shutdown(ScheduledExecutorService timer) throws InterruptedException {
		if (timer != null && !timer.isShutdown()) {
			timer.shutdown();
			timer.awaitTermination(Math.round(1), TimeUnit.MILLISECONDS);
		}
		imageView.setVisible(false);
	}
	
	
	@FXML
	protected void openImage(ActionEvent event) throws InterruptedException {
		// This method opens an image and display it using the GUI
//		final String imageFilename = getImageFilename();
//		image = Imgcodecs.imread(imageFilename);
//		imageView.setImage(Utilities.mat2Image(image)); 
		
		String filepath = getImageFilename();
		if (filepath == "empty") {
			
		} else {
			shutdown(timer1);
			shutdown(timer2);
			capture = new VideoCapture(filepath); // open video file
			playbutton.setDisable(false);
			playbutton.setText("Play");
			textbox.setText("Press Play");
			textbox.setVisible(true);
		}
		
		// You don't have to understand how mat2Image() works. 
		// In short, it converts the image from the Mat format to the Image format
		// The Mat format is used by the opencv library, and the Image format is used by JavaFX
		// BTW, you should be able to explain briefly what opencv and JavaFX are after finishing this assignment
	}
	
	@FXML
	protected void createFrameGrabber() throws InterruptedException {
		if (capture != null && capture.isOpened()) { // the video must be open
			//double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
			double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
			int frameHeight = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
			int frameCount = (int) totalFrameCount;
			double framePerSecond = 200;
			int[][] STI_vertical = new int[frameCount][frameHeight];
		// create a runnable to fetch new frames periodically
			Runnable frameGrabber = new Runnable() {
				@Override
				public void run() {
					Mat frame = new Mat();
					if (capture.read(frame)) { // decode successfully
						image = frame;
				
						Image im = Utilities.mat2Image(frame);
						Utilities.onFXThread(imageView.imageProperty(), im);
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						
//						double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
						int frameCount = (int) currentFrameNumber;
						columnGrabber(frame, STI_vertical[frameCount - 1], frameHeight);
						slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin()));
//						if (currentFrameNumber == frameCount) {
//							for (int i=0;i<frameCount-2;i++) {
//								for (int j=0;j<frameHeight;j++) {
//									System.out.print(STI_vertical[i][j]);
//								}
//								System.out.println(" ");
//							}
//						}
					} else { // reach the end of the video
//						capture.set(Videoio.CAP_PROP_POS_FRAMES, 0);
						timer1.shutdown();
						BufferedImage STI = new BufferedImage(frameCount, frameHeight, BufferedImage.TYPE_INT_ARGB);
						for (int i=0;i<frameCount;i++) {
							for (int j=0;j<frameHeight;j++) {
								Color c = new Color(STI_vertical[i][j], true);
								STI.setRGB(i, j, c.getRGB());
							}
						}
						try {
							ImageIO.write(STI, "png", new File("STIimage.png"));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							System.out.println("wrong");
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
	
	protected void columnGrabber(Mat original, int[] rgbs, int frameHeight) {
		BufferedImage image = Utilities.matToBufferedImage(original);
	    for (int i=0;i<frameHeight;i++) {
	    	rgbs[i] = image.getRGB(center, i);
	    	//System.out.println(rgbs[i]);
	    }

//	    for (int i=0;i<rgbs.length;i++) {
//	    	System.out.print(rgbs[i]);
//	    }
//	    System.out.println(" ");
	    
	}

	@FXML
	protected void playVideo(ActionEvent event) throws LineUnavailableException, InterruptedException {
		if (capture.isOpened()) {
			// open successfully
			if (playbutton.getText() == "Stop") {
				shutdown(timer1);
				shutdown(timer2);
				playbutton.setText("Play");
				textbox.setVisible(true);
				return ;
			}
			playbutton.setText("Stop");
			textbox.setVisible(false);
			imageView.setVisible(true);
			createFrameGrabber();
		}
		double framePerSecond = capture.get(Videoio.CAP_PROP_FPS);
		Runnable audioGrabber = new Runnable() {
			@Override
			public void run()  {
				try {
					playImage();
					playClick();
					
				} catch (LineUnavailableException e) {
					e.printStackTrace();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		if (timer2 != null && !timer2.isShutdown()) {
			timer2.shutdown();
			timer2.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
		}
		timer2 = Executors.newSingleThreadScheduledExecutor();
		timer2.scheduleAtFixedRate(audioGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS);
	}
	
	protected float getVolume(Clip clip) {
	    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);        
	    return (float) Math.pow(10f, gainControl.getValue() / 20f);
	}

	protected void setVolume(float volume, Clip clip) {
	    if (volume < 0f || volume > 1f)
	        throw new IllegalArgumentException("Volume not valid: " + volume);
	    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);        
	    gainControl.setValue(20f * (float) Math.log10(volume));
	}
	
	
	protected float getVolume(SourceDataLine audiovolume) {
	    FloatControl gainControl = (FloatControl) audiovolume.getControl(FloatControl.Type.MASTER_GAIN);        
	    return (float) Math.pow(10f, gainControl.getValue() / 20f);
	}
	
	protected void setVolume(float volume, SourceDataLine audiovolume) {
	    if (volume < 0f || volume > 1f)
	        throw new IllegalArgumentException("Volume not valid: " + volume);
	    FloatControl gainControl = (FloatControl) audiovolume.getControl(FloatControl.Type.MASTER_GAIN);        
	    gainControl.setValue(20f * (float) Math.log10(volume));
	}
	
	protected void playClick() throws IOException, LineUnavailableException {
		try {
			File soundFile = new File("resources/click.wav");
			AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
			Clip clip = AudioSystem.getClip();
			clip.open(audioIn);
			float volume = (float) (volumeAdjuster.getValue()/(slider.getMax() - slider.getMin()));
			setVolume(volume * 1f, clip);
			clip.start();
		}
		catch (UnsupportedAudioFileException e) {
			System.out.println("audio file type unsupported");
		}
	}
	
	protected void playImage() throws LineUnavailableException {		
		if (image != null) {
			// convert the image from RGB to greyscale
			Mat grayImage = new Mat();
			Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
			
			// resize the image
			Mat resizedImage = new Mat();
			Imgproc.resize(grayImage, resizedImage, new Size(width, height));
			
			// quantization
			double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
			for (int row = 0; row < resizedImage.rows(); row++) {
				for (int col = 0; col < resizedImage.cols(); col++) {
					roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
				}
			}
			
	        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, sampleRate);
            
            float volume = (float) (volumeAdjuster.getValue()/(slider.getMax() - slider.getMin()));
            setVolume(volume * 1f, sourceDataLine);
            sourceDataLine.start();
            
            for (int col = 0; col < width; col++) {
            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
            		double signal = 0;
                	for (int row = 0; row < height; row++) {
                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
                		int time = t + col * numberOfSamplesPerColumn;
                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
                		signal += roundedImage[row][col] * ss;
                	}
                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
            	}
            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
            }
            sourceDataLine.drain();
            sourceDataLine.close();
		} else {
			//System.out.println("No image");
		}
	} 
}
