/*
 * Copyright (c) 2023 FIRST
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted (subject to the limitations in the disclaimer below) provided that
 * the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * Neither the name of FIRST nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS
 * LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.firstinspires.ftc.vision;

import android.graphics.Canvas;
import android.util.Size;

import com.qualcomm.robotcore.util.RobotLog;

import io.github.deltacv.vision.internal.source.ftc.SourcedCameraName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.CameraName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.hardware.camera.controls.CameraControl;
import org.firstinspires.ftc.robotcore.internal.camera.calibration.CameraCalibration;
import org.opencv.core.Mat;
import org.openftc.easyopencv.OpenCvCamera;
import org.openftc.easyopencv.OpenCvCameraFactory;
import org.openftc.easyopencv.OpenCvCameraRotation;
import org.openftc.easyopencv.OpenCvWebcam;
import org.openftc.easyopencv.TimestampedOpenCvPipeline;

public class VisionPortalImpl extends VisionPortal
{
    protected OpenCvCamera camera;
    protected volatile CameraState cameraState = CameraState.CAMERA_DEVICE_CLOSED;
    protected VisionProcessor[] processors;
    protected volatile boolean[] processorsEnabled;
    protected volatile CameraCalibration calibration;
    protected final boolean autoPauseCameraMonitor;
    protected final Object userStateMtx = new Object();
    protected final Size cameraResolution;
    protected final StreamFormat webcamStreamFormat;
    protected static final OpenCvCameraRotation CAMERA_ROTATION = OpenCvCameraRotation.SENSOR_NATIVE;
    protected String captureNextFrame;
    protected final Object captureFrameMtx = new Object();

    public VisionPortalImpl(CameraName camera, int cameraMonitorViewId, boolean autoPauseCameraMonitor, Size cameraResolution, StreamFormat webcamStreamFormat, VisionProcessor[] processors)
    {
        this.processors = processors;
        this.cameraResolution = cameraResolution;
        this.webcamStreamFormat = webcamStreamFormat;
        processorsEnabled = new boolean[processors.length];

        for (int i = 0; i < processors.length; i++)
        {
            processorsEnabled[i] = true;
        }

        this.autoPauseCameraMonitor = autoPauseCameraMonitor;

        createCamera(camera, cameraMonitorViewId);
        startCamera();
    }

    protected void startCamera()
    {
        if (camera == null)
        {
            throw new IllegalStateException("This should never happen");
        }

        if (cameraResolution == null) // was the user a silly silly
        {
            throw new IllegalArgumentException("parameters.cameraResolution == null");
        }

        camera.setViewportRenderer(OpenCvCamera.ViewportRenderer.NATIVE_VIEW);

        if(!(camera instanceof OpenCvWebcam))
        {
            camera.setViewportRenderingPolicy(OpenCvCamera.ViewportRenderingPolicy.OPTIMIZE_VIEW);
        }

        cameraState = CameraState.OPENING_CAMERA_DEVICE;
        camera.openCameraDeviceAsync(new OpenCvCamera.AsyncCameraOpenListener()
        {
            @Override
            public void onOpened()
            {
                cameraState = CameraState.CAMERA_DEVICE_READY;
                cameraState = CameraState.STARTING_STREAM;

                camera.startStreaming(cameraResolution.getWidth(), cameraResolution.getHeight(), CAMERA_ROTATION);

                camera.setPipeline(new ProcessingPipeline());
                cameraState = CameraState.STREAMING;
            }

            @Override
            public void onError(int errorCode)
            {
                cameraState = CameraState.ERROR;
                RobotLog.ee("VisionPortalImpl", "Camera opening failed.");
            }
        });
    }

    protected void createCamera(CameraName cameraName, int cameraMonitorViewId)
    {
        if (cameraName == null) // was the user a silly silly
        {
            throw new IllegalArgumentException("parameters.camera == null");
        }
        else if (cameraName instanceof SourcedCameraName) // Webcams
        {
            camera = OpenCvCameraFactory.getInstance().createWebcam((WebcamName) cameraName, cameraMonitorViewId);
        }
        else // ¯\_(ツ)_/¯
        {
            throw new IllegalArgumentException("Unknown camera name");
        }
    }

    @Override
    public void setProcessorEnabled(VisionProcessor processor, boolean enabled)
    {
        int numProcessorsEnabled = 0;
        boolean ok = false;

        for (int i = 0; i < processors.length; i++)
        {
            if (processor == processors[i])
            {
                processorsEnabled[i] = enabled;
                ok = true;
            }

            if (processorsEnabled[i])
            {
                numProcessorsEnabled++;
            }
        }

        if (ok)
        {
            if (autoPauseCameraMonitor)
            {
                if (numProcessorsEnabled == 0)
                {
                    camera.pauseViewport();
                }
                else
                {
                    camera.resumeViewport();
                }
            }
        }
        else
        {
            throw new IllegalArgumentException("Processor not attached to this helper!");
        }
    }

    @Override
    public boolean getProcessorEnabled(VisionProcessor processor)
    {
        for (int i = 0; i < processors.length; i++)
        {
            if (processor == processors[i])
            {
                return processorsEnabled[i];
            }
        }

        throw new IllegalArgumentException("Processor not attached to this helper!");
    }

    @Override
    public CameraState getCameraState()
    {
        return cameraState;
    }

    @Override
    public void setActiveCamera(WebcamName webcamName)
    {
        throw new UnsupportedOperationException("setActiveCamera is only supported for switchable webcams");
    }

    @Override
    public WebcamName getActiveCamera()
    {
        throw new UnsupportedOperationException("getActiveCamera is only supported for switchable webcams");
    }

    @Override
    public <T extends CameraControl> T getCameraControl(Class<T> controlType)
    {
        if (cameraState == CameraState.STREAMING)
        {
            throw new UnsupportedOperationException("Getting controls is not yet supported in EOCV-Sim");
        }
        else
        {
            throw new IllegalStateException("You cannot use camera controls until the camera is streaming");
        }
    }

    class ProcessingPipeline extends TimestampedOpenCvPipeline
    {
        @Override
        public void init(Mat firstFrame)
        {
            for (VisionProcessor processor : processors)
            {
                processor.init(firstFrame.width(), firstFrame.height(), calibration);
            }
        }

        @Override
        public Mat processFrame(Mat input, long captureTimeNanos)
        {
            synchronized (captureFrameMtx)
            {
                if (captureNextFrame != null)
                {
                    // saveMatToDiskFullPath(input, "/sdcard/VisionPortal-" + captureNextFrame + ".png");
                }

                captureNextFrame = null;
            }

            Object[] processorDrawCtxes = new Object[processors.length]; // cannot re-use frome to frame

            for (int i = 0; i < processors.length; i++)
            {
                if (processorsEnabled[i])
                {
                    processorDrawCtxes[i] = processors[i].processFrame(input, captureTimeNanos);
                }
            }

            requestViewportDrawHook(processorDrawCtxes);

            return input;
        }

        @Override
        public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity, Object userContext)
        {
            Object[] ctx = (Object[]) userContext;

            for (int i = 0; i < processors.length; i++)
            {
                if (processorsEnabled[i])
                {
                    processors[i].onDrawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, scaleCanvasDensity, ctx[i]);
                }
            }
        }
    }

    @Override
    public void saveNextFrameRaw(String filepath)
    {
        synchronized (captureFrameMtx)
        {
            captureNextFrame = filepath;
        }
    }

    @Override
    public void stopStreaming()
    {
        synchronized (userStateMtx)
        {
            if (cameraState == CameraState.STREAMING || cameraState == CameraState.STARTING_STREAM)
            {
                cameraState = CameraState.STOPPING_STREAM;
                new Thread(() ->
                {
                    synchronized (userStateMtx)
                    {
                        camera.stopStreaming();
                        cameraState = CameraState.CAMERA_DEVICE_READY;
                    }
                }).start();
            }
            else if (cameraState == CameraState.STOPPING_STREAM
                    || cameraState == CameraState.CAMERA_DEVICE_READY
                    || cameraState == CameraState.CLOSING_CAMERA_DEVICE)
            {
                // be idempotent
            }
            else
            {
                throw new RuntimeException("Illegal CameraState when calling stopStreaming()");
            }
        }
    }

    @Override
    public void resumeStreaming()
    {
        synchronized (userStateMtx)
        {
            if (cameraState == CameraState.CAMERA_DEVICE_READY || cameraState == CameraState.STOPPING_STREAM)
            {
                cameraState = CameraState.STARTING_STREAM;
                new Thread(() ->
                {
                    synchronized (userStateMtx)
                    {
                        if (camera instanceof OpenCvWebcam)
                        {
                            ((OpenCvWebcam)camera).startStreaming(cameraResolution.getWidth(), cameraResolution.getHeight(), CAMERA_ROTATION, webcamStreamFormat.eocvStreamFormat);
                        }
                        else
                        {
                            camera.startStreaming(cameraResolution.getWidth(), cameraResolution.getHeight(), CAMERA_ROTATION);
                        }
                        cameraState = CameraState.STREAMING;
                    }
                }).start();
            }
            else if (cameraState == CameraState.STREAMING
                    || cameraState == CameraState.STARTING_STREAM
                    || cameraState == CameraState.OPENING_CAMERA_DEVICE) // we start streaming automatically after we open
            {
                // be idempotent
            }
            else
            {
                throw new RuntimeException("Illegal CameraState when calling stopStreaming()");
            }
        }
    }

    @Override
    public void stopLiveView()
    {
        OpenCvCamera cameraSafe = camera;

        if (cameraSafe != null)
        {
            camera.pauseViewport();
        }
    }

    @Override
    public void resumeLiveView()
    {
        OpenCvCamera cameraSafe = camera;

        if (cameraSafe != null)
        {
            camera.resumeViewport();
        }
    }

    @Override
    public float getFps()
    {
        OpenCvCamera cameraSafe = camera;

        if (cameraSafe != null)
        {
            return cameraSafe.getFps();
        }
        else
        {
            return 0;
        }
    }

    @Override
    public void close()
    {
        synchronized (userStateMtx)
        {
            cameraState = CameraState.CLOSING_CAMERA_DEVICE;

            if (camera != null)
            {
                camera.closeCameraDeviceAsync(() -> cameraState = CameraState.CAMERA_DEVICE_CLOSED);
            }

            camera = null;
        }
    }
}