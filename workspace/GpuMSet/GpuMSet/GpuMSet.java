package GpuMSet;

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
			+ "//centroid out vec4 vCoord;\n"
			+ "void main(void) {\n"
			+ " gl_Position.xy = vertices;\n"
			+ " gl_Position.z = 0.0f;"
			+ " gl_Position.w = 1.0f;\n"
			+ "}")};
	
	private static String[] fragmentShaderCode = {new String("#version 330\n"
			+ "precision highp float;"
			+ "out vec4 fColor;\n"
			+ "uniform vec2 size;\n"
			+ "uniform vec3 coords;\n"
			+ "uniform uint iters;\n"
			+ "\n"
			+ "uint iterate(in vec2 position) {\n"
			+ " float xmin,ymin,zr,zi,cr,ci,zr2,zi2;\n"
			+ " uint i;\n"
			+ " xmin = coords[0]-coords[2]/2.0f;\n"
			+ " ymin = coords[1]-coords[2]/2.0f*size[1]/size[0];\n"
			+ " zr = 0.0f;"
			+ " zi = 0.0f;"
			+ " cr = xmin + (position[0]/size[0])*coords[2];"
			+ " ci = ymin + (position[1]/size[1])*(coords[2]*size[1]/size[0]);\n"
			+ " for(i = 0u; i < iters; i++)\n {"
			+ "  zr2 = zr*zr;\n"
			+ "  zi2 = zi*zi;\n"
			+ "  if((zr2 + zi2) > 100.0f) return(i);\n"
			+ "  zi = 2*zr*zi + ci;\n"
			+ "  zr = zr2 - zi2 + cr;\n"
			+ " }\n"
			+ " return(iters);\n"
			+ "}\n"
			+ "\n"
			+ "void main(void) {\n"
			+ " uint i;"
			+ " i = iterate(gl_FragCoord.xy);\n"
			+ " if(i == iters) discard;\n"
			+ " fColor.r = 0.5*(1+sin(3.14f*2.5f*i/iters));\n"
			+ " fColor.g = 0.5*(1-sin(3.14f*2.5f*i/iters));\n"
			+ " fColor.b = 0.5*(1+cos(3.14f*2.5f*i/iters));\n"
			+ " fColor.a = 1.0f;"
			+ "}")};
	private int vShader,fShader,glProg;
	private FloatBuffer vlBuffer,vcBuffer;
	private IntBuffer glBuffers, vArray; 
	private GL4 gl2;
	private int iters;
	private float x,y,size;

	public GLrenderer(GLCanvas c) {
		this.vlData = new float[]{-1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f};
		this.vcData = new float[]{1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
		this.vlBuffer = null;
		this.vcBuffer = null;
		this.x = -0.75f;
		this.y = 0.0f;
		this.size = 4.0f;
		this.iters = 100;
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
		int usize = gl2.glGetUniformLocation(glProg, "size");
		gl2.glUniform2f(usize, (float)drawable.getSurfaceWidth(), (float)drawable.getSurfaceHeight());
		int ucoords = gl2.glGetUniformLocation(glProg, "coords");
		gl2.glUniform3f(ucoords, x, y, size);
		int uiters = gl2.glGetUniformLocation(glProg, "iters");
		gl2.glUniform1ui(uiters, iters);
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
		gl2.glDrawArrays(GL4.GL_TRIANGLE_STRIP, 0, vlBuffer.capacity());
		int x = gl2.glGetError();
		if(x != GL4.GL_NO_ERROR) throw(new GLException("0x" + Integer.toHexString(x) + " error drawing"));
	}

	@Override
	synchronized public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		display(drawable);
	}
	@Override
	public void mouseClicked(MouseEvent e) {
	}
	@Override
	public void mouseEntered(MouseEvent e) {
	}
	@Override
	public void mouseExited(MouseEvent e) {
	}
	@Override
	public void mousePressed(MouseEvent e) {
		float width = (float)e.getComponent().getSize().width;
		float height = (float)e.getComponent().getSize().height;
		this.x = this.x + ((float)e.getX()/width-0.5f) * this.size;
		this.y = this.y + (0.5f-(float)e.getY()/height) * this.size * height/width;
		if(e.getButton()==MouseEvent.BUTTON1){
			this.size = this.size * 0.75f;
			this.iters = this.iters * 7/6;
		} else {
			this.size = this.size / 0.75f;
			this.iters = this.iters * 6/7;
		}
		System.out.println(this.iters);	
		display((GLAutoDrawable) e.getSource());

	}
	@Override
	public void mouseReleased(MouseEvent e) {
		display((GLAutoDrawable) e.getSource());
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

public final class GpuMSet {

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
			GLCapabilities c = new GLCapabilities(GLProfile.getMaxProgrammableCore(false));
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
