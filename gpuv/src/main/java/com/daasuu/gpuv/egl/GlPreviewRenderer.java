package com.daasuu.gpuv.egl;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.util.Size;
import com.daasuu.gpuv.camerarecorder.capture.MediaVideoEncoder;
import com.daasuu.gpuv.egl.filter.GlFilter;

import javax.microedition.khronos.egl.EGLConfig;

import static android.opengl.GLES20.*;



public class GlPreviewRenderer implements SurfaceTexture.OnFrameAvailableListener {

    private final Handler handler = new Handler();

    //private GlSurfaceTexture previewTexture;

    // private final Camera camera;


    private final GLSurfaceView glView;

    private GlFramebufferObject filterFramebufferObject;
    private GlPreview previewShader;

    private GlFilter glFilter;
    private boolean isNewShader;

    private int angle = 0;
    private float aspectRatio = 1f;
    private float scaleRatio = 1f;
    private float drawScale = 1f;
    private float gestureScale = 1f;

    private Size cameraResolution;

    private int updateTexImageCounter = 0;
    private int updateTexImageCompare = 0;

    private SurfaceCreateListener surfaceCreateListener;
    private MediaVideoEncoder videoEncoder;

    private CameraSurfaceRenderer mRenderer;

    public int mVideoWidth;

    public int mVideoHeight;

    private boolean flip = false;

    public GlPreviewRenderer(GLSurfaceView glView, int videoWidth, int videoHeight) {
        this.glView = glView;
        this.glView.setEGLConfigChooser(new GlConfigChooser(false));
        this.glView.setEGLContextFactory(new GlContextFactory());
        mRenderer = new CameraSurfaceRenderer(this);
        this.glView.setRenderer(mRenderer);
        this.glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        this.mVideoWidth = videoWidth;
        this.mVideoHeight = videoHeight;

    }

    public void onStartPreview(float cameraPreviewWidth, float cameraPreviewHeight, boolean isLandscapeDevice) {


    }

    public int getWidthView() {
        return glView.getWidth();
    }

    public int getHeightView() {
        return glView.getHeight();
    }

    public void setGlFilter(final GlFilter filter) {
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (glFilter != null) {
                    glFilter.release();
                }
                glFilter = filter;
                isNewShader = true;
                glView.requestRender();
            }
        });
    }


    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // increment every time a new frame is avail
        updateTexImageCounter++;
        glView.requestRender();
    }

    public void onSurfaceCreated(EGLConfig config, int[] args) {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        GLES20.glBindTexture(GlPreview.GL_TEXTURE_EXTERNAL_OES, mRenderer.hTex);
        // GL_TEXTURE_EXTERNAL_OES
        EglUtil.setupSampler(GlPreview.GL_TEXTURE_EXTERNAL_OES, GL_LINEAR, GL_NEAREST);
        GLES20.glBindTexture(GL_TEXTURE_2D, 0);

        filterFramebufferObject = new GlFramebufferObject();
        // GL_TEXTURE_EXTERNAL_OES
        previewShader = new GlPreview(GlPreview.GL_TEXTURE_EXTERNAL_OES);
        previewShader.setup();


        /*Matrix.setLookAtM(VMatrix, 0,
                0.0f, 0.0f, 5.0f,
                0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        );*/


        if (glFilter != null) {
            isNewShader = true;
        }

        GLES20.glGetIntegerv(GL_MAX_TEXTURE_SIZE, args, 0);

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (surfaceCreateListener != null) {
                    surfaceCreateListener.onCreated(mRenderer.getSurfaceTexture());
                }
            }
        });
    }

    public void onSurfaceChanged(int width, int height) {

        filterFramebufferObject.setup(width, height);
        previewShader.setFrameSize(width, height);
        if (glFilter != null) {
            glFilter.setFrameSize(width, height);
        }
        scaleRatio = (float) width / height;
        //Matrix.frustumM(ProjMatrix, 0, -scaleRatio, scaleRatio, -1, 1, 5, 7);
    }

    public void onDrawFrame(int hText, float[] mStMatrix, float[] mMvpMatrix) {
        if (isNewShader) {
            if (glFilter != null) {
                glFilter.setup();
                //glFilter.setFrameSize(fbo.getWidth(), fbo.getHeight());
            }
            isNewShader = false;
        }

        if (glFilter != null) {
            filterFramebufferObject.enable();
        }

        GLES20.glClear(GL_COLOR_BUFFER_BIT);

        previewShader.draw(hText, mMvpMatrix, mStMatrix, aspectRatio);


        if (glFilter != null) {
            GLES20.glClear(GL_COLOR_BUFFER_BIT);
            glFilter.draw(hText);
        }

        //Rotate image to avoid mirrored image
        Matrix.scaleM(mMvpMatrix, 0, 1, -1, 1);

        if (videoEncoder != null && !videoEncoder.isReduceFps()) {
            flip = false;
            synchronized (this) {
                // notify to capturing thread that the camera frame is available.
                videoEncoder.frameAvailableSoon(hText, mStMatrix, mMvpMatrix, aspectRatio);
            }
        } else {
            flip = !flip;
            if (flip) {
                synchronized (this) {
                    if (videoEncoder != null) {
                        // notify to capturing thread that the camera frame is available.
                        videoEncoder.frameAvailableSoon(hText, mStMatrix, mMvpMatrix, aspectRatio);
                    }
                }
            }
        }
    }

    public void setCameraResolution(Size cameraResolution) {
        this.cameraResolution = cameraResolution;
    }

    public void setVideoEncoder(final MediaVideoEncoder encoder) {
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                synchronized (GlPreviewRenderer.this) {
                    if (encoder != null) {
                        encoder.setEglContext(EGL14.eglGetCurrentContext(), mRenderer.hTex);
                    }
                    videoEncoder = encoder;
                }
            }
        });

    }

    public SurfaceTexture getPreviewTexture() {
        return mRenderer.getSurfaceTexture();
    }

    public void setAngle(int angle) {
        this.angle = angle;
        if (angle == 90 || angle == 270) {
            aspectRatio = (float) cameraResolution.getWidth() / cameraResolution.getHeight();
        } else {
            aspectRatio = (float) cameraResolution.getHeight() / cameraResolution.getWidth();
        }
    }

    public void setGestureScale(float gestureScale) {
        this.gestureScale = gestureScale;
    }

    public GlFilter getFilter() {
        return glFilter;
    }

    public void release() {
        glView.queueEvent(new Runnable() {
            @Override
            public void run() {
                if (glFilter != null) {
                    glFilter.release();
                }
            }
        });
    }

    public interface SurfaceCreateListener {
        void onCreated(SurfaceTexture surface);
    }

    public void setSurfaceCreateListener(SurfaceCreateListener surfaceCreateListener) {
        this.surfaceCreateListener = surfaceCreateListener;
    }
}

