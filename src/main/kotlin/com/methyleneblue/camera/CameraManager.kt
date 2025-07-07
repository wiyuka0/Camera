package com.methyleneblue.camera

import com.methyleneblue.camera.obj.CameraObject

class CameraManager {
    companion object {
        val instances = HashMap<Int, CameraObject>()

        fun update(){
            for (camera in instances) {
                camera.value.updateCamera(null)
            }
        }

        fun getCamera(cameraId: Int): CameraObject? {
            return instances[cameraId]
        }

        var currentId = 0

        fun addCamera(camera: CameraObject): Int {
//            this.instances[currentId] = (camera)
            addCameraByIndex(camera, currentId)
            currentId++
            return currentId - 1
        }

        fun addCameraByIndex(camera: CameraObject, index: Int): Int{
            this.instances[index] = camera
            return index
        }

    }
}