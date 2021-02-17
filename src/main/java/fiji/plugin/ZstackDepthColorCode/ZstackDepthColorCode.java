package fiji.plugin.ZstackDepthColorCode;


import java.awt.Color;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageWindow;
import ij.gui.Toolbar;
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
		boolean bFramesAsSlices=false;
		int nLutChoice;
		int nOutput;
		boolean bInvertLut;
		int nColorbar;
		
		ByteProcessor ipColorbar; 
		
		
		final ImagePlus imp1 = WindowManager.getCurrentImage();
		if (imp1 == null) {
			IJ.noImage();
			return;
		}
		//stack dimensions
		int channels = imp1.getNChannels();
		int frames = imp1.getNFrames();
		int slices = imp1.getNSlices();
		
		int nCurrSlice=imp1.getSlice();
		int nCurrFrame=imp1.getFrame();
		
		String sStackName = imp1.getTitle();

		if (channels > 1) {
			IJ.error("Depth colorcode", "Only one channel stack/hyperstack required");
			return;
		}
		
		if (slices < 2) {
			if(frames < 2)
			{
				IJ.error("Depth colorcode", "Z-stack/hyperstack is required!");
				return;
			}
			else
			{
				IJ.log("Seems like selected image stack contains only time frames.");
				IJ.log("They will be treated as z-slices.");
				bFramesAsSlices=true;
				slices=frames;
				frames=1;
			}
			
		}

		
		//ask user for the input
		GenericDialog dgLUT = new GenericDialog("Choose LUT");

		
		String [] luts = IJ.getLuts();
		String [] sOutputStack = new String [] {
				"Color (RGB)", "Composite"};
		String [] sColorBar = new String [] {
				"No", "Colorbar with all 256 LUT colors","Colorbar slices only"};
		dgLUT.addChoice("Use LUT for depth coding:",luts,Prefs.get("ZCodeStack.zLutChoice","Fire"));
		dgLUT.addCheckbox("Invert LUT? ", Prefs.get("ZCodeStack.invertLut", false));
		dgLUT.addMessage("NB: current Brightness/Contrast settings will be used for min/max of the output!");
		dgLUT.addChoice("Generate LUT colorbar image?", sColorBar, Prefs.get("ZCodeStack.Colorbar", "Colorbar with all 256 LUT colors"));
		dgLUT.addChoice("Output stack format:", sOutputStack, Prefs.get("ZCodeStack.Output", "Color (RGB)"));
		dgLUT.showDialog();
		if (dgLUT.wasCanceled())
            return;
		
		nLutChoice = dgLUT.getNextChoiceIndex();
		Prefs.set("ZCodeStack.zLutChoice", luts[nLutChoice]);
		String sZLUTName = luts[nLutChoice];
		bInvertLut = dgLUT.getNextBoolean();
		Prefs.set("ZCodeStack.invertLut", bInvertLut);
		nColorbar = dgLUT.getNextChoiceIndex();
		Prefs.set("ZCodeStack.Colorbar", sColorBar[nColorbar]);
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
				ish.putPixel(i, 0, (int) Math.round((255) * ((double) (slices-i)/ (double) slices)));
			}
			
		}
		
		ImagePlus ccc = new ImagePlus("LUTcode",ish);
		ccc.show();
		IJ.run(ccc,sZLUTName,"");
		IJ.run(ccc,"RGB Color","");
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
				if(!bFramesAsSlices)
					{imp1.setPositionWithoutUpdate(1, n, t);}
				else
					{imp1.setPositionWithoutUpdate(1, t, n);}
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
		// restore the initial position
		imp1.setPositionWithoutUpdate(1,nCurrSlice,nCurrFrame);
		
		ImageWindow winD = null;
		ImagePlus outIP = null;
		//output as RGB stack
		if (nOutput==0)
		{
			finCC.setCalibration(cal);
			outIP=finCC;

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
	 		imp2.setCalibration(cal);
	 		
	 		outIP=imp2;
	
		}
		
		
		if(!bFramesAsSlices)
			outIP.setPosition(1, 1, 1);
 		else
 			outIP.setPosition(1, nCurrFrame, 1);
 		
		outIP.show();
		outIP.updateAndDraw();
		winD =outIP.getWindow();

		
		IJ.showStatus("Generating colorcoded Z-stack...done.");
		IJ.showProgress(1.1);

		if(nColorbar>0)
		{
			int nFinlines;
			if(nColorbar==1)
			{
				nFinlines=256;
			}
			else
			{
				nFinlines=slices;
			}
			
			ipColorbar = new ByteProcessor(10,nFinlines);
			for ( i=0; i<nFinlines; i++)
			{
				if(!bInvertLut)
				{
					for (j=0;j<10;j++)
						{ipColorbar.putPixel(j, nFinlines-i-1, (int) Math.round((255) * ((double) (i+1) / (double) nFinlines)));}
				}
				else
				{
					for (j=0;j<10;j++)
						{ipColorbar.putPixel(j, nFinlines-i-1, (int) Math.round((255) * ((double) (nFinlines-i)/ (double) nFinlines)));}
				}
				
			}
			
			ImagePlus cbar = new ImagePlus("colorbar_"+ sStackName,ipColorbar);
			cbar.show();
			IJ.run(cbar,sZLUTName,"");
			IJ.run(cbar,"RGB Color","");
			Color saveColor = Toolbar.getBackgroundColor();
			IJ.setBackgroundColor(0, 0, 0);
			IJ.run(cbar,"Canvas Size...","width="+12+" height="+Integer.toString(nFinlines+2)+" position=Center");
			IJ.setBackgroundColor(255, 255, 255);
			IJ.run(cbar,"Canvas Size...","width="+32+" height="+Integer.toString(nFinlines+22)+" position=Center");
			IJ.setBackgroundColor(saveColor.getRed(),saveColor.getGreen(),saveColor.getBlue());
			cbar.show();
			winD =cbar.getWindow();
			
		}
		
		System.gc();
		WindowManager.setCurrentWindow(winD);
		winD.toFront();
		

	}

}
