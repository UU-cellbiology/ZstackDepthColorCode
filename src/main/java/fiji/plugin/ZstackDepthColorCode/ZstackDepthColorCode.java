package fiji.plugin.ZstackDepthColorCode;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;


public class ZstackDepthColorCode implements PlugIn {

	ImageStack depthCoded;
	ImageProcessor ipc3;

	@Override
	public void run(final String arg) {
		
		int i,j,t,n,x,y;
		
		
		final ImagePlus imp1 = WindowManager.getCurrentImage();
		if (imp1 == null) {
			IJ.noImage();
			return;
		}
		//stack dimensions
		final int channels = imp1.getNChannels();
		final int frames = imp1.getNFrames();
		final int slices = imp1.getNSlices();
		String sStackName = imp1.getTitle();

		if (slices < 2) {
			IJ.error("Depth color code", "Stack required");
			return;
		}
		if (channels > 1) {
			IJ.error("Depth color code", "Only one channel stack/hyperstack required");
			return;
		}
		
		//ask user for the input
		GenericDialog dgLUT = new GenericDialog("Choose LUT");
		int nLutChoice;
		int nOutput;
		String [] luts = IJ.getLuts();
		String [] sOutputStack = new String [] {
				"Color (RGB)", "Composite"};
		dgLUT.addChoice("Use LUT for depth coding:",luts,Prefs.get("ZCodeStack.zLutChoice","Fire"));
		dgLUT.addCheckbox("Invert LUT? ", Prefs.get("ZCodeStack.invertLut", false));
		dgLUT.addMessage("NB: current Brightness/Contrast settings will be used for min/max of the output!");
		dgLUT.addChoice("Output stack format:", sOutputStack, Prefs.get("ZCodeStack.Output", "Color (RGB)"));
		dgLUT.showDialog();
		if (dgLUT.wasCanceled())
            return;
		
		nLutChoice = dgLUT.getNextChoiceIndex();
		Prefs.set("ZCodeStack.zLutChoice", luts[nLutChoice]);
		String sZLUTName = luts[nLutChoice];
		boolean bInvertLut = dgLUT.getNextBoolean();
		Prefs.set("ZCodeStack.invertLut", bInvertLut);
		nOutput = dgLUT.getNextChoiceIndex();
		Prefs.set("ZCodeStack.Output", sOutputStack[nOutput]);	
		
		//store calibration
		final Calibration cal = imp1.getCalibration();
		final int width = imp1.getWidth();
		final int height = imp1.getHeight();
		
		IJ.showStatus("Generating colorcoded Z-stack...");
		
		//making LUT colors array
		ByteProcessor ish = new ByteProcessor(slices,1);
		for ( i=0; i<slices; i++)
		{
			if(!bInvertLut)
			{
				ish.putPixel(i, 0, (int) Math.round((255) * ((double) (i+1) / (double) slices)));
			}
			else
			{
				ish.putPixel(i, 0, (int) Math.round((255) * ((double) (slices-i+2)/ (double) slices)));
			}
			
		}
		
		ImagePlus ccc = new ImagePlus("LUTcode",ish);
		ccc.show();
		IJ.run(sZLUTName);
		IJ.run("RGB Color");
		ccc.setSlice(1);
		final int [][] arrayLUT = new int [slices][3];
		int [] valrgb = new int [3];
		ImageProcessor ip1 = ccc.getProcessor();
		for(i=0;i<slices;i++)
		{
			valrgb= ccc.getPixel(i, 0);
			for (j=0;j<3;j++)
				arrayLUT[i][j]=valrgb[j];
		}
		ccc.changes=false;
		ccc.close();

		//make a new hyperstack
		final ImagePlus finCC = IJ.createHyperStack("Depth_colorcoded_" + sStackName, width, height, 1, slices, frames, 24);
		
		final int[] rgb = new int[3];
		double pixval;
		double imMin, imRange;
		IJ.showProgress(0.0);
		ip1 = imp1.getProcessor();
		imMin=ip1.getMin();
		imRange=ip1.getMax()-imMin;
		
		int total_time=frames*slices;
		for (t = 1; t <= frames; t++) 
		{
			for (n = 1; n <= slices; n++) 
			{
				imp1.setPositionWithoutUpdate(1, n, t);
				finCC.setPositionWithoutUpdate(1, n, t);
				ip1 = imp1.getProcessor();
				
				ipc3 = finCC.getChannelProcessor();
	
				for (x = 0; x < width; x++) {
					for (y = 0; y < height; y++) {
						pixval=(double) (ip1.getf(x, y)-imMin)/imRange;
						if (pixval>1.0)
						{
							pixval=1.0;
						}
						if(pixval<0.0)
						{
							pixval=0.0;
						}
						rgb[0] =
							(int) (pixval * arrayLUT[n-1][0]);
						rgb[1] =
							(int) (pixval * arrayLUT[n-1][1]);
						rgb[2] =
							(int) (pixval * arrayLUT[n-1][2]);
						ipc3.putPixel(x, y, rgb);

					}
				}
				IJ.showProgress((double)((t-1)*slices+n)/(double)total_time);
			}
		}
		
		//output as RGB stack
		if (nOutput==0)
		{
			finCC.show();
		}
		//output as Composite stack
		else
		{
			IJ.showStatus("Converting to Composite...");
			ImageStack stack1 = finCC.getStack();
			n = stack1.getSize();
			ImageStack stack2 = new ImageStack(width, height);
			for (i=0; i<n; i++) {
				ColorProcessor ip = (ColorProcessor)stack1.getProcessor(1);
				stack1.deleteSlice(1);
				byte[] R = new byte[width*height];
				byte[] G = new byte[width*height];
				byte[] B = new byte[width*height];
				ip.getRGB(R, G, B);
				stack2.addSlice(null, R);
				stack2.addSlice(null, G);
				stack2.addSlice(null, B);
			}
			ImagePlus imp2 = new ImagePlus(finCC.getTitle(), stack2);
			imp2.setDimensions(3, slices, frames);
	 		imp2 = new CompositeImage(imp2, IJ.COMPOSITE);
			imp2.show();
		}
		
		IJ.showStatus("Generating colorcoded Z-stack...done.");
		IJ.showProgress(1.1);
		final ImagePlus impD = WindowManager.getCurrentImage();
		final ImageWindow winD = impD.getWindow();
		
		impD.setCalibration(cal);
		System.gc();
		WindowManager.setCurrentWindow(winD);

	}

}
