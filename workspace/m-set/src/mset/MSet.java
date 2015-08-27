package mset;

import java.math.BigDecimal;
import java.util.Random;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.awt.Color;
import java.awt.Frame;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import jogl.util.*;
import jogl.util.data.*;
import jogl.util.data.av.*;

import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.*;
import com.jogamp.opengl.math.*;
import com.jogamp.opengl.math.geom.*;
import com.jogamp.opengl.util.Animator;
import com.jogamp.opengl.util.glsl.*;
import com.jogamp.opengl.util.glsl.sdk.*;
import com.jogamp.opengl.util.texture.awt.*;
import com.jogamp.opengl.util.texture.spi.*;
import com.jogamp.opengl.util.texture.spi.awt.*;

final class GLrenderer implements GLEventListener, MouseListener {
	static final int NTHREADS=36;
	public volatile float[] vlData;
	public volatile float[] vcData;

	private static String[] vertexShaderCode = {new String("#version 330\n"
			+ "layout(location = 1) in vec2 vertices;\n"
			+ "layout(location = 2) in vec3 colors;\n"
			+ "//centroid out vec4 vCoord;\n"
			+ "smooth out vec3 vColor ;\n"
			+ "void main(void) {\n"
			+ " gl_Position.xy = vertices;\n"
			+ " gl_Position.z = 0.0f;"
			+ " gl_Position.w = 1.0f;\n"
			+ " gl_PointSize = 2.0f;"
			+ " vColor = colors;\n"
			+ "}")};
	
	private static String[] fragmentShaderCode = {new String("#version 330\n"
			+ "smooth in vec3 vColor;\n"
			+ "out vec4 fColor;\n"
			+ "void main(void) {\n"
			+ " fColor = vec4(vColor,1.0f);\n"
			+ "}")};
	private int vShader,fShader,glProg;
	private FloatBuffer vlBuffer,vcBuffer;
	private IntBuffer glBuffers, vArray; 
	private GL4 gl2;
	private Thread[] t = new Thread[NTHREADS];
	private Calculator[] z = new Calculator[NTHREADS];

	public GLrenderer(GLCanvas c) {
		this.vlData = new float[c.getSurfaceWidth()*c.getSurfaceHeight()*2];
		this.vcData = new float[c.getSurfaceWidth()*c.getSurfaceHeight()*3];	
		this.vlBuffer = null;
		this.vcBuffer = null;
	}
	public void init(GLAutoDrawable drawable) throws GLException {
		 GL gl = drawable.getGL();
		 GLProfile glprof = gl.getGLProfile();
		 System.out.println(glprof);		
		 if(!glprof.hasGLSL()) throw(new GLException("sorry, no shaders"));
		 if(gl.isGL4core()) gl2 = gl.getGL4();
		 if(gl.isGL3core()) gl2 = (GL4)gl.getGL3();
		 if(gl.isGL2()) gl2 = (GL4)gl.getGL2();		 
		 vShader = gl2.glCreateShader(GL2.GL_VERTEX_SHADER);
		 fShader = gl2.glCreateShader(GL2.GL_FRAGMENT_SHADER);
		 gl2.glShaderSource(vShader, 1, vertexShaderCode, null, 0);
		 gl2.glShaderSource(fShader, 1, fragmentShaderCode, null, 0);
		 gl2.glCompileShader(vShader);
		 int[] a = {1024};
		 byte[] b = new byte[1024];
		 gl2.glGetShaderInfoLog(vShader, 1024, IntBuffer.wrap(a), ByteBuffer.wrap(b));
		 System.out.println(new String(b));
		 int x = gl2.glGetError();
		 if(x != GL2.GL_NO_ERROR) throw(new GLException(x + "vertex shader compile failed"));
		 gl2.glCompileShader(fShader);
		 b = new byte[1024];
		 gl2.glGetShaderInfoLog(fShader, 1024, IntBuffer.wrap(a), ByteBuffer.wrap(b));
		 System.out.println(new String(b));
		 x = gl2.glGetError();
		 if(x != GL2.GL_NO_ERROR) throw(new GLException(x + "fragement shader compile failed"));
		 glProg = gl2.glCreateProgram();
		 gl2.glAttachShader(glProg, vShader);
		 gl2.glAttachShader(glProg, fShader);
		 gl2.glLinkProgram(glProg);
		 b = new byte[1024];
		 gl2.glGetProgramInfoLog(glProg, 1024, IntBuffer.wrap(a), ByteBuffer.wrap(b));
		 System.out.println(new String(b));
		 x = gl2.glGetError();
		 if(x != GL2.GL_NO_ERROR) throw(new GLException("0x" + Integer.toHexString(x) + " program link failed"));
		 gl2.glValidateProgram(glProg);
 		 vArray = IntBuffer.allocate(1);
		 gl2.glGenVertexArrays(1, vArray);
		 glBuffers = IntBuffer.allocate(2);
		 gl2.glGenBuffers(2, glBuffers);
	}
	@Override
	public void dispose(GLAutoDrawable drawable) {
		GL gl = drawable.getGL();
		GLProfile glprof = gl.getGLProfile();
		System.out.println(glprof);		
		if(!glprof.hasGLSL()) throw(new GLException("sorry, no shaders"));
		if(gl.isGL4core()) gl2 = gl.getGL4();
		if(gl.isGL3core()) gl2 = (GL4)gl.getGL3();
		if(gl.isGL2()) gl2 = (GL4)gl.getGL2();		 
		gl2.glDeleteProgram(glProg);
	}

	@Override
	public void display(GLAutoDrawable drawable) throws GLException {
		if(vlBuffer==null)
			vlBuffer = ByteBuffer.allocateDirect(Float.BYTES*vlData.length).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
		if(vcBuffer==null)
			vcBuffer = ByteBuffer.allocateDirect(Float.BYTES*vcData.length).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
		GL gl = drawable.getGL();
		GLProfile glprof = gl.getGLProfile();
		//System.out.println(glprof);		
		if(!glprof.hasGLSL()) throw(new GLException("sorry, no shaders"));
		if(gl.isGL4core()) gl2 = gl.getGL4();
		if(gl.isGL3core()) gl2 = (GL4)gl.getGL3();
		if(gl.isGL2()) gl2 = (GL4)gl.getGL2();		 
		gl2.glUseProgram(glProg);
		gl2.glViewport(0, 0, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
		gl2.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
		gl2.glClear(GL2.GL_COLOR_BUFFER_BIT|GL2.GL_DEPTH_BUFFER_BIT); 
		gl2.glEnable(GL4.GL_DEPTH_TEST);
		gl2.glDisable(GL4.GL_CULL_FACE);
		gl2.glEnable(GL4.GL_MULTISAMPLE); 
		gl2.glPointSize(2.0f);
		vlBuffer.put(this.vlData);
		vlBuffer.flip();
		vcBuffer.put(this.vcData);
		vcBuffer.flip();
		gl2.glBindVertexArray(vArray.get(0));
		gl2.glBindBuffer(GL4.GL_ARRAY_BUFFER, glBuffers.get(0));
		gl2.glBufferData(GL4.GL_ARRAY_BUFFER, Float.BYTES*vlBuffer.capacity(), vlBuffer, GL4.GL_STREAM_DRAW);
		gl2.glEnableVertexAttribArray(1);
		gl2.glVertexAttribPointer(1, 2, GL4.GL_FLOAT, false, 0, 0);
		gl2.glBindBuffer(GL4.GL_ARRAY_BUFFER, glBuffers.get(1));
    	gl2.glBufferData(GL4.GL_ARRAY_BUFFER, Float.BYTES*vcBuffer.capacity(), vcBuffer, GL4.GL_STREAM_DRAW);
		gl2.glEnableVertexAttribArray(2);
		gl2.glVertexAttribPointer(2, 3, GL4.GL_FLOAT, false, 0, 0);
//		gl2.glBindFragDataLocation(glProg, 0, "fColor");
		gl2.glDrawArrays(GL4.GL_POINTS, 0, vlBuffer.capacity());
		int x = gl2.glGetError();
		if(x != GL4.GL_NO_ERROR) throw(new GLException("0x" + Integer.toHexString(x) + " error drawing"));
	}

	@Override
	synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		if(t[0] != null) {
			z[0].abend = true;
			try {
				t[0].join();
				} catch (InterruptedException e) {
			}
		}
		this.vlData = new float[(drawable.getSurfaceWidth()+1)*(drawable.getSurfaceHeight()+1)*2];
		this.vcData = new float[(drawable.getSurfaceWidth()+1)*(drawable.getSurfaceHeight()+1)*3];		
		vlBuffer = ByteBuffer.allocateDirect(Float.BYTES*vlData.length).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
		vcBuffer = ByteBuffer.allocateDirect(Float.BYTES*vcData.length).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
		System.gc();
		for(int i = 0; i < NTHREADS; i++)
		{
			if(z[i] != null) {
				int iters = z[i].iters;
				BigDecimal xx = z[i].x;
				BigDecimal yy = z[i].y;
				BigDecimal size = z[i].size;
				z[i] = new Calculator(	this, 
										drawable.getSurfaceWidth(),
										drawable.getSurfaceHeight(), 
										iters, 
										xx, 
										yy, 
										size, 
										NTHREADS,
										i);
			} else {
				z[i] = new Calculator(	this, 
										drawable.getSurfaceWidth(), 
										drawable.getSurfaceHeight(),
										100, 
										new BigDecimal("-0.75"),
										new BigDecimal("0"),
										new BigDecimal("4"),
										NTHREADS,
										i);
			}
			t[i] = new Thread(z[i]);
			t[i].setPriority(Thread.MAX_PRIORITY);
		}
		for(int i = 0; i < NTHREADS; i++)
			t[i].start(); 
		display(drawable);
	}
	@Override
	public void mouseClicked(MouseEvent e) {
		for(int i = 0; i < NTHREADS; i++) {
			if(t[i] != null) {
				z[i].abend = true;
				try {
					t[i].join();
				} catch (InterruptedException ex) {
				}
			}
		}
		System.gc();
		BigDecimal size = z[0].size;
		int iters = z[0].iters;
		BigDecimal ysize = z[0].size.multiply(BigDecimal.valueOf(z[0].rows).divide(BigDecimal.valueOf(z[0].cols),z[0].mc),z[0].mc);
		BigDecimal x = (((BigDecimal.valueOf(e.getX()).divide(BigDecimal.valueOf(z[0].cols),z[0].mc)).subtract(new BigDecimal("0.5"),z[0].mc)).multiply(size,z[0].mc)).add(z[0].x);
		BigDecimal y = (new BigDecimal("0.5").subtract(BigDecimal.valueOf(e.getY()).divide(BigDecimal.valueOf(z[0].rows),z[0].mc))).multiply(ysize,z[0].mc).add(z[0].y);
		if(e.getButton() == MouseEvent.BUTTON1) {
			size = size.multiply(new BigDecimal("0.75"),z[0].mc);	
			iters = iters*7/6;
		}
		else {
			size = size.divide(new BigDecimal("0.75"), z[0].mc);
			iters = iters*6/7;
		}
		for(int i = 0; i < NTHREADS; i++)
		{
			z[i] = new Calculator(this, z[i].cols, z[i].rows, iters, x, y, size, NTHREADS, i);
			t[i] = new Thread(z[i]);
			t[i].setPriority(Thread.MAX_PRIORITY);
		}
		for(int i = 0; i < NTHREADS; i++)
			t[i].start(); 
		display((GLAutoDrawable) e.getSource());
	}
	@Override
	public void mouseEntered(MouseEvent e) {
	}
	@Override
	public void mouseExited(MouseEvent e) {
	}
	@Override
	public void mousePressed(MouseEvent e) {
	}
	@Override
	public void mouseReleased(MouseEvent e) {
	}
}


final class ExitListener extends WindowAdapter {
	@Override
	public void windowClosing(WindowEvent e) {
		e.getWindow().dispose();	
		System.exit(0);
	}
	@Override
	public void windowClosed(WindowEvent e) {
		e.getWindow().dispose();	
		System.exit(0);
	}
}

public final class MSet {

	private static Frame topWin;
	private static GLCanvas picture;
	private static ExitListener doExit;

	public static void main(String[] args) {
		//try {
			doExit = new ExitListener();
			topWin = new Frame();
			topWin.setSize(500,500);
			topWin.setTitle(new String("M set"));
			topWin.addWindowListener(doExit);
			topWin.setVisible(true);
			GLCapabilities c = new GLCapabilities(GLProfile.getMaxProgrammableCore(true));
			c.setDoubleBuffered(true);
			c.setHardwareAccelerated(true);
			picture = new GLCanvas(c);
			picture.setBackground(new Color(1,0,0,0));
			GLrenderer r = new GLrenderer(picture);
			picture.addGLEventListener(r);
			picture.addMouseListener(r);
			topWin.add(picture);
			topWin.paintAll(topWin.getGraphics());
			Animator a = new Animator(picture);
			a.start();
		//} catch(exception e) {
		//	e.printStackTrace();
		//	System.exit(1);
		//}
	}
}
