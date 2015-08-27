package mset;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

class Calculator implements Runnable {
	volatile public boolean abend = false;
	public int rows, cols, iters;
	public BigDecimal x, y, size;
	private BigDecimal xmin, xmax, ymin, ymax;
	private GLrenderer r;
	public MathContext mc;
	private int tile;
	private int parts;
	static private BigDecimal two = BigDecimal.valueOf(2);
	static private BigDecimal four = BigDecimal.valueOf(4);

	public Calculator(GLrenderer renderer, int cols, int rows, int iters, BigDecimal x, BigDecimal y, BigDecimal size, int parts, int tile) {
		this.tile = tile;
		this.parts = parts;
		this.r = renderer;
		int precision = (int)Math.ceil(Math.log10((double)cols*(double)rows/size.doubleValue()));
		mc = new MathContext(precision, RoundingMode.HALF_EVEN);
		int i = 0;
		while(i<rows) {
			int j = tile;
			while(j < cols)	synchronized(r.vlData){
				int k = 4*(i/2*cols+j);
				r.vlData[k] = 2.0f*((float)j/(float)cols)-1.0f;
				r.vlData[k+1] = 2.0f*((float)i/(float)rows)-1.0f;
				r.vlData[k+2] = 2.0f*(((float)j)/(float)cols)-1.0f;
				r.vlData[k+3] = 2.0f*(((float)i+1.0f)/(float)rows)-1.0f;				
				j += parts;
			}
			i += 2;
		}
		this.rows = rows;
		this.cols = cols;
		this.iters = iters;
		this.x = x;
		this.y = y;
		this.size = size;
		BigDecimal ysize = size.multiply(BigDecimal.valueOf(rows), mc).divide(BigDecimal.valueOf(cols), mc);
		this.xmin = x.subtract(size.divide(two, mc),mc);
		this.xmax = x.add(size.divide(two, mc),mc);
		this.ymin = y.subtract(ysize.divide(two, mc), mc);
		this.ymax = y.add(ysize.divide(two, mc),mc);
	}
	
	@Override
	public void run() {
		BigDecimal xstep = xmax.subtract(xmin,mc).divide(BigDecimal.valueOf(cols),mc);
		BigDecimal ystep = ymax.subtract(ymin,mc).divide(BigDecimal.valueOf(rows),mc);
		int i = 0; 
		int k;
		while(i<rows) {
			int j = tile;
			while(j < cols)	{
				int n = 6 * (j + i/2*cols);
				BigDecimal zr = BigDecimal.valueOf(0);
				BigDecimal zi = BigDecimal.valueOf(0);
				BigDecimal cr = xmin.add(xstep.multiply(BigDecimal.valueOf(j),mc),mc);
				BigDecimal ci = ymin.add(ystep.multiply(BigDecimal.valueOf(i),mc),mc);
				for(k = 0; k < iters; k++) {
					if(this.abend) return;
					BigDecimal zr2 = zr.multiply(zr,mc);
					BigDecimal zi2 = zi.multiply(zi,mc);
					if(zr2.add(zi2).compareTo(four) > 0) break;
					zi = ci.add(two.multiply(zr.multiply(zi,mc),mc),mc);
					zr = cr.add(zr2.subtract(zi2,mc),mc);
				}
				if(k != iters) synchronized(r.vcData){
					r.vcData[n] = (float)(5*k%iters)/(float)iters;
					r.vcData[n+1] = (float) Math.sin(5.0*Math.PI*(float)k/(float)iters);
					r.vcData[n+2] = 1.0f-(float)(5*k%iters)/(float)iters;
				} else synchronized(r.vcData){
					r.vcData[n] = 0.0f;
					r.vcData[n+1] = 0.0f;
					r.vcData[n+2] = 0.0f;
				}
				n += 3;
				zr = BigDecimal.valueOf(0);
				zi = BigDecimal.valueOf(0);
				cr = xmin.add(xstep.multiply(BigDecimal.valueOf(j),mc),mc);
				ci = ymin.add(ystep.multiply(BigDecimal.valueOf(i+1),mc),mc);
				for(k = 0; k < iters; k++) {
					if(this.abend) return;
					BigDecimal zr2 = zr.multiply(zr,mc);
					BigDecimal zi2 = zi.multiply(zi,mc);
					if(zr2.add(zi2).compareTo(four) > 0) break;
					zi = ci.add(two.multiply(zr.multiply(zi,mc),mc),mc);
					zr = cr.add(zr2.subtract(zi2,mc),mc);
				}
				if(k != iters) synchronized(r.vcData){
					r.vcData[n] = (float)(5*k%iters)/(float)iters;
					r.vcData[n+1] = (float) Math.sin(5.0*Math.PI*(float)k/(float)iters);
					r.vcData[n+2] = 1.0f-(float)(5*k%iters)/(float)iters;
				} else synchronized(r.vcData){
					r.vcData[n] = 0.0f;
					r.vcData[n+1] = 0.0f;
					r.vcData[n+2] = 0.0f;
				}
				j+=parts;
			}
			i+=2;
		}
	}
}

class SingleCalculator implements Runnable {
	public boolean abend = false;
	public int rows, cols, iters;
	public BigDecimal x, y, size;
	private BigDecimal xmin, xmax, ymin, ymax;
	private GLrenderer r;
	public MathContext mc;
	private int tile;
	private int parts;
	static private BigDecimal two = BigDecimal.valueOf(2);

	public SingleCalculator(GLrenderer renderer, int cols, int rows, int iters, BigDecimal x, BigDecimal y, BigDecimal size, int parts, int tile) {
		this.tile = tile;
		this.parts = parts;
		this.r = renderer;
		int precision = (int)Math.ceil(Math.log10((double)cols*(double)rows/size.doubleValue()));
		mc = new MathContext(precision, RoundingMode.HALF_EVEN);
		int i = 0;
		while(i<rows) {
			int j = tile;
			while(j < cols)	synchronized(r.vlData){
				int k = 4*(i/2*cols+j);
				r.vlData[k] = 2.0f*((float)j/(float)cols)-1.0f;
				r.vlData[k+1] = 2.0f*((float)i/(float)rows)-1.0f;
				r.vlData[k+2] = 2.0f*(((float)j)/(float)cols)-1.0f;
				r.vlData[k+3] = 2.0f*(((float)i+1.0f)/(float)rows)-1.0f;				
				j += parts;
			}
			i += 2;
		}
		this.rows = rows;
		this.cols = cols;
		this.iters = iters;
		this.x = x;
		this.y = y;
		this.size = size;
		BigDecimal ysize = size.multiply(BigDecimal.valueOf(rows), mc).divide(BigDecimal.valueOf(cols), mc);
		this.xmin = x.subtract(size.divide(two, mc),mc);
		this.xmax = x.add(size.divide(two, mc),mc);
		this.ymin = y.subtract(ysize.divide(two, mc), mc);
		this.ymax = y.add(ysize.divide(two, mc),mc);
	}
	
	@Override
	public void run() {
		BigDecimal xstep = xmax.subtract(xmin,mc).divide(BigDecimal.valueOf(cols),mc);
		BigDecimal ystep = ymax.subtract(ymin,mc).divide(BigDecimal.valueOf(rows),mc);
		int i = 0; 
		int k;
		while(i<rows) {
			int j = tile;
			while(j < cols)	{
				int n = 6 * (j + i/2*cols);
				float zr = 0.0f;
				float zi = 0.0f;
				float cr = xmin.add(xstep.multiply(BigDecimal.valueOf(j),mc),mc).floatValue();
				float ci = ymin.add(ystep.multiply(BigDecimal.valueOf(i),mc),mc).floatValue();
				for(k = 0; k < iters; k++) {
					if(this.abend) return;
					float zr2 = zr*zr;
					float zi2 = zi*zi;
					if(zr2 + zi2 > 4.0f) break;
					zi = 2*zr*zi+ci;
					zr = zr2-zi2+cr;
				}
				if(k != iters) synchronized(r.vcData){
					r.vcData[n] = (float)(5*k%iters)/(float)iters;
					r.vcData[n+1] = (float) Math.sin(5.0*Math.PI*(float)k/(float)iters);
					r.vcData[n+2] = 1.0f-(float)(5*k%iters)/(float)iters;
				} else synchronized(r.vcData){
					r.vcData[n] = 0.0f;
					r.vcData[n+1] = 0.0f;
					r.vcData[n+2] = 0.0f;
				}
				n += 3;
				zr = 0.0f;
				zi = 0.0f;
				cr = xmin.add(xstep.multiply(BigDecimal.valueOf(j),mc),mc).floatValue();
				ci = ymin.add(ystep.multiply(BigDecimal.valueOf(i+1),mc),mc).floatValue();
				for(k = 0; k < iters; k++) {
					if(this.abend) return;
					float zr2 = zr*zr;
					float zi2 = zi*zi;
					if(zr2 + zi2 > 4.0f) break;
					zi = 2*zr*zi+ci;
					zr = zr2-zi2+cr;
				}
				if(k != iters) synchronized(r.vcData){
					r.vcData[n] = (float)(5*k%iters)/(float)iters;
					r.vcData[n+1] = (float) Math.sin(5.0*Math.PI*(float)k/(float)iters);
					r.vcData[n+2] = 1.0f-(float)(5*k%iters)/(float)iters;
				} else synchronized(r.vcData){
					r.vcData[n] = 0.0f;
					r.vcData[n+1] = 0.0f;
					r.vcData[n+2] = 0.0f;
				}
				j+=parts;
			}
			i+=2;
		}
	}
}