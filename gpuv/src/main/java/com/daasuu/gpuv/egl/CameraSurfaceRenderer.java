package com.daasuu.gpuv.egl;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

class CameraSurfaceRenderer implements GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener {

    private static final boolean DEBUG = false; // TODO set false on release

    private static final String TAG = "CameraSurfaceRenderer";

    private final WeakReference<GlPreviewRenderer> mWeakParent;
    private SurfaceTexture mSTexture;	// API >= 11
    public int hTex;
    public final float[] mStMatrix = new float[16];
    public final float[] mMvpMatrix = new float[16];

    public CameraSurfaceRenderer(final GlPreviewRenderer parent) {
        mWeakParent = new WeakReference<GlPreviewRenderer>(parent);
        Matrix.setIdentityM(mMvpMatrix, 0);
    }

    @Override
    public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
        if (DEBUG) Log.v(TAG, "onSurfaceCreated:");
        // This renderer required OES_EGL_image_external extension
        final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);	// API >= 8
//			if (DEBUG) Log.i(TAG, "onSurfaceCreated:Gl extensions: " + extensions);
        if (!extensions.contains("OES_EGL_image_external"))
            throw new RuntimeException("This system does not support OES_EGL_image_external.");
        // create texture ID
        final int[] args = new int[1];
        GLES20.glGenTextures(args.length, args, 0);
        hTex = args[0];
        // create SurfaceTexture with texture ID.
        mSTexture = new SurfaceTexture(hTex);
        mSTexture.setOnFrameAvailableListener(this);
        // clear screen with yellow color so that you can see rendering rectangle
        //GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);

        mWeakParent.get().onSurfaceCreated(config, args);
    }

    @Override
    public void onSurfaceChanged(final GL10 unused, final int width, final int height) {
        if (DEBUG) Log.v(TAG, String.format("onSurfaceChanged:(%d,%d)", width, height));
        // if at least with or height is zero, initialization of this view is still progress.
        if ((width == 0) || (height == 0)) return;
        updateViewport();
        mWeakParent.get().onSurfaceChanged(width, height);
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSTexture;
    }

    private final void updateViewport() {
        final GlPreviewRenderer parent = mWeakParent.get();
        if (parent != null) {
            final int view_width = parent.getWidthView();
            final int view_height = parent.getHeightView();
            GLES20.glViewport(0, 0, view_width, view_height);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            final double video_width = parent.mVideoWidth;
            final double video_height = parent.mVideoHeight;
            if (video_width == 0 || video_height == 0) return;
            Matrix.setIdentityM(mMvpMatrix, 0);
            final double view_aspect = view_width / (double)view_height;
            Log.i(TAG, String.format("view(%d,%d)%f,video(%1.0f,%1.0f)", view_width, view_height, view_aspect, video_width, video_height));
            final double req = video_width / video_height;
            int x, y;
            int width, height;
            if (view_aspect > req) {
                // if view is wider than camera image, calc width of drawing area based on view height
                y = 0;
                height = view_height;
                width = (int)(req * view_height);
                x = (view_width - width) / 2;
            } else {
                // if view is higher than camera image, calc height of drawing area based on view width
                x = 0;
                width = view_width;
                height = (int)(view_width / req);
                y = (view_height - height) / 2;
            }
            // set viewport to draw keeping aspect ration of camera image
            if (DEBUG) Log.v(TAG, String.format("xy(%d,%d),size(%d,%d)", x, y, width, height));
            GLES20.glViewport(x, y, width, height);
            //if (mDrawer != null){}
            //mDrawer.setMatrix(mMvpMatrix, 0);
        }
    }

    private volatile boolean requesrUpdateTex = false;
    private boolean flip = true;
    /**
     * drawing to GLSurface
     * we set renderMode to GLSurfaceView.RENDERMODE_WHEN_DIRTY,
     * this method is only called when #requestRender is called(= when texture is required to update)
     * if you don't set RENDERMODE_WHEN_DIRTY, this method is called at maximum 60fps
     */
    @Override
    public void onDrawFrame(final GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (requesrUpdateTex) {
            requesrUpdateTex = false;
            // update texture(came from camera)
            mSTexture.updateTexImage();
            // get texture matrix
            mSTexture.getTransformMatrix(mStMatrix);
        }
        mWeakParent.get().onDrawFrame(hTex, mStMatrix, mMvpMatrix);
    }

    @Override
    public void onFrameAvailable(final SurfaceTexture st) {
        requesrUpdateTex = true;
//			final CameraGLView parent = mWeakParent.get();
//			if (parent != null)
//				parent.requestRender();
    }
}
