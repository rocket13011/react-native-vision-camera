package com.mrousavy.camera.extensions

import android.annotation.SuppressLint
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import com.mrousavy.camera.CameraCannotBeOpenedError
import com.mrousavy.camera.CameraDisconnectedError
import com.mrousavy.camera.CameraQueues
import com.mrousavy.camera.parsers.CameraDeviceError
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "CameraManager"

@SuppressLint("MissingPermission")
suspend fun CameraManager.openCamera(cameraId: String,
                                     onDisconnected: (camera: CameraDevice, reason: Throwable) -> Unit,
                                     queue: CameraQueues.CameraQueue): CameraDevice {
  return suspendCancellableCoroutine { continuation ->
    Log.i(TAG, "Camera $cameraId: Opening...")

    val callback = object: CameraDevice.StateCallback() {
      override fun onOpened(camera: CameraDevice) {
        Log.i(TAG, "Camera $cameraId: Opened!")
        continuation.resume(camera)
      }

      override fun onDisconnected(camera: CameraDevice) {
        Log.i(TAG, "Camera $cameraId: Disconnected!")
        if (continuation.isActive) {
          continuation.resumeWithException(CameraCannotBeOpenedError(cameraId, CameraDeviceError.DISCONNECTED))
        } else {
          onDisconnected(camera, CameraDisconnectedError(cameraId, CameraDeviceError.DISCONNECTED))
        }
        camera.tryClose()
      }

      override fun onError(camera: CameraDevice, errorCode: Int) {
        Log.e(TAG, "Camera $cameraId: Error! $errorCode")
        val error = CameraDeviceError.fromCameraDeviceError(errorCode)
        if (continuation.isActive) {
          continuation.resumeWithException(CameraCannotBeOpenedError(cameraId, error))
        } else {
          onDisconnected(camera, CameraDisconnectedError(cameraId, error))
        }
        camera.tryClose()
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      this.openCamera(cameraId, queue.executor, callback)
    } else {
      this.openCamera(cameraId, callback, queue.handler)
    }
  }
}

fun CameraDevice.tryClose() {
  try {
    Log.i(TAG, "Camera $id: Closing...")
    this.close()
  } catch (e: Throwable) {
    Log.e(TAG, "Camera $id: Failed to close!", e)
  }
}
