//package com.methyleneblue.camera.obj
//
//import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
//import com.methyleneblue.camera.raytracepack.bvh.BVHTree
//import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
//import com.methyleneblue.camera.raytracepack.bvh.HitResult
//import com.methyleneblue.camera.texture.TextureManager
//import org.bukkit.Location
//import org.bukkit.Material
//import org.bukkit.boss.BossBar
//import org.bukkit.entity.Player
//import org.bukkit.util.Vector
//import org.jocl.*
//import org.jocl.CL.*
//import org.joml.Vector3f
//import java.awt.image.BufferedImage
//import java.util.*
//import kotlin.math.tan
//
///**
// * OpenCL 蒙特卡洛路径追踪相机
// * 修复版 2.0: 增加颜色回退机制 (Fix Grayscale Issue)
// */
//class CLRTCamera(
//    location: Location,
//    size: Pair<Int, Int>,
//    fov: Double,
//    distance: Double,
//    progressBar: BossBar?,
//    bufferedImage: BufferedImage,
//    depthImage: Array<FloatArray>
//) : BVHCamera(
//    location,
//    size,
//    fov,
//    distance,
//    progressBar,
//    bufferedImage,
//    depthImage,
//) {
//
//    // --- 配置参数 ---
//    private val SAMPLES_PER_PIXEL = 4
//    private val MAX_BOUNCES = 4
//    private val WORLD_SIZE = 64
//
//    // --- OpenCL 状态 ---
//    private var isInitialized = false
//    private var context: cl_context? = null
//    private var queue: cl_command_queue? = null
//    private var program: cl_program? = null
//    private var kernel: cl_kernel? = null
//
//    // Buffers
//    private var memWorld: cl_mem? = null
//    private var memPixelOut: cl_mem? = null
//    private var memSeeds: cl_mem? = null
//    private var memMaterials: cl_mem? = null
//    private var memTextures: cl_mem? = null
//
//    private val width = size.first
//    private val height = size.second
//    private val pixelBuffer = IntArray(width * height)
//    private val worldDataHost = IntArray(WORLD_SIZE * WORLD_SIZE * WORLD_SIZE)
//    private var lastWorldCenter: Location? = null
//
//    // 随机种子数组
//    private val seedsHost = IntArray(width * height * 2)
//
//    // 材质数据缓存
//    private var materialDataHost: FloatArray = FloatArray(0)
//
//    // -------------------------------------------------------------------------
//    // OpenCL Kernel
//    // -------------------------------------------------------------------------
//
////    private val kernelSource = """
////        #define MAX_BOUNCES $MAX_BOUNCES
////        #define WORLD_SIZE $WORLD_SIZE
////        #define PI 3.14159265359f
////
////        // --- 随机数生成 ---
////        uint hash(uint x) {
////            x += (x << 10u); x ^= (x >> 6u); x += (x << 3u);
////            x ^= (x >> 11u); x += (x << 15u); return x;
////        }
////        float randomFloat(uint* state) {
////            *state = hash(*state);
////            return (float)(*state) / (float)(0xFFFFFFFFu);
////        }
////
////        // --- 几何与采样 ---
////        void createCoordinateSystem(float3 N, float3* Nt, float3* Nb) {
////            if (fabs(N.x) > fabs(N.y)) *Nt = (float3)(N.z, 0, -N.x) / sqrt(N.x * N.x + N.z * N.z);
////            else *Nt = (float3)(0, -N.z, N.y) / sqrt(N.y * N.y + N.z * N.z);
////            *Nb = cross(N, *Nt);
////        }
////
////        float3 sampleHemisphere(float3 normal, uint* seed) {
////            float r1 = randomFloat(seed);
////            float r2 = randomFloat(seed);
////            float sinTheta = sqrt(1.0f - r2);
////            float phi = 2.0f * PI * r1;
////            float x = sinTheta * cos(phi);
////            float z = sinTheta * sin(phi);
////            float y = sqrt(r2);
////            float3 Nt, Nb;
////            createCoordinateSystem(normal, &Nt, &Nb);
////            return (Nt * x + normal * y + Nb * z);
////        }
////
////        int getBlock(__global int* world, int x, int y, int z) {
////            if(x < 0 || y < 0 || z < 0 || x >= WORLD_SIZE || y >= WORLD_SIZE || z >= WORLD_SIZE) return 0;
////            return world[x + y * WORLD_SIZE + z * WORLD_SIZE * WORLD_SIZE];
////        }
////
////        // --- 材质查找结构 ---
////        typedef struct {
////            int texOffset;
////            float emission;
////            float spread;
////            float3 baseColor;
////        } MaterialInfo;
////
////        MaterialInfo getMaterial(__global float4* matBuffer, int matID) {
////            float4 data = matBuffer[matID];
////            MaterialInfo info;
////            info.texOffset = (int)data.x;
////            info.emission = data.y;
////            info.spread = data.z;
////
////            int c = as_int(data.w);
////            float r = (float)((c >> 16) & 0xFF) / 255.0f;
////            float g = (float)((c >> 8) & 0xFF) / 255.0f;
////            float b = (float)(c & 0xFF) / 255.0f;
////            info.baseColor = (float3)(r, g, b);
////
////            return info;
////        }
////
////        float3 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
////            // 1. Host 明确说没有纹理 -> 返回白色 (显示 BaseColor)
////            if (baseOffset < 0) return (float3)(1.0f, 1.0f, 1.0f);
////
////            int tx = clamp((int)(u * 16.0f), 0, 15);
////            int ty = clamp((int)(v * 16.0f), 0, 15);
////
////            int pixelIndex = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
////            int argb = textures[pixelIndex];
////
////            // 2. 【调试关键点】读取到透明/空数据
////            // 如果你看到画面是【洋红色】，说明 TextureManager.textureArray 是空的(全0)！
////            // 此时请检查 TextureManager 是否在 CLRTCamera 初始化前就加载完毕了。
////            if (argb == 0) return (float3)(1.0f, 0.0f, 1.0f); // Debug: Magenta
////
////            float r = (float)((argb >> 16) & 0xFF) / 255.0f;
////            float g = (float)((argb >> 8) & 0xFF) / 255.0f;
////            float b = (float)(argb & 0xFF) / 255.0f;
////            return (float3)(r, g, b);
////        }
////
//////        float3 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
//////            // 状态 1: 红色 (RED)
//////            // 含义: Host端传递的 Offset 是 -1。说明 Kotlin 认为这个方块没有材质。
//////            if (baseOffset < 0) return (float3)(1.0f, 0.0f, 0.0f);
//////
//////            int tx = clamp((int)(u * 16.0f), 0, 15);
//////            int ty = clamp((int)(v * 16.0f), 0, 15);
//////
//////            int pixelIndex = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
//////            int argb = textures[pixelIndex];
//////
//////            // 状态 2: 绿色 (GREEN)
//////            // 含义: 读取到的像素数据是 0 (透明/空)。
//////            // 既然Debug模式有纹理，如果这里变绿，说明正常模式下 faceIndex 或 pixelIndex 算错了。
//////            if (argb == 0) return (float3)(0.0f, 1.0f, 0.0f);
//////
//////            float r = (float)((argb >> 16) & 0xFF) / 255.0f;
//////            float g = (float)((argb >> 8) & 0xFF) / 255.0f;
//////            float b = (float)(argb & 0xFF) / 255.0f;
//////            return (float3)(r, g, b);
//////        }
////        // --- 纹理采样 (安全版) ---
//////        float3 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
//////            // 如果 Host 说没纹理 (-1)，返回白色，这样 result = 1.0 * BaseColor(Fallback)
//////            if (baseOffset < 0) return (float3)(1.0f, 1.0f, 1.0f);
//////
//////            int tx = clamp((int)(u * 16.0f), 0, 15);
//////            int ty = clamp((int)(v * 16.0f), 0, 15);
//////
//////            int pixelIndex = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
//////            int argb = textures[pixelIndex];
//////
//////            // 如果采样到空/透明，返回白色，而不是黑色
//////            if (argb == 0) return (float3)(1.0f, 1.0f, 1.0f);
//////
//////            float r = (float)((argb >> 16) & 0xFF) / 255.0f;
//////            float g = (float)((argb >> 8) & 0xFF) / 255.0f;
//////            float b = (float)(argb & 0xFF) / 255.0f;
//////            return (float3)(r, g, b);
//////        }
////
////        // --- Path Tracer ---
////        __kernel void pathTraceKernel(
////            __global int* worldData,
////            __global int* outPixels,
////            __global uint* seeds,
////            __global int* textures,
////            __global float4* materials,
////            const float3 camPos,
////            const float3 camDir,
////            const float3 camRight,
////            const float3 camUp,
////            const int width,
////            const int height,
////            const int originX,
////            const int originY,
////            const int originZ,
////            const int frameCount
////        ) {
////            int gid = get_global_id(0);
////            if (gid >= width * height) return;
////
////            int screenX = gid % width;
////            int screenY = gid / width;
////            uint seed = seeds[gid] + frameCount * 719393;
////
////            float3 finalColor = (float3)(0,0,0);
////
////            for (int s = 0; s < $SAMPLES_PER_PIXEL; s++) {
////                float uOffset = randomFloat(&seed) - 0.5f;
////                float vOffset = randomFloat(&seed) - 0.5f;
////                float u = ((float)screenX + uOffset) / (float)width * 2.0f - 1.0f;
////                float v = ((float)screenY + vOffset) / (float)height * 2.0f - 1.0f;
////
////                float3 rayDir = normalize(camDir + camRight * u + camUp * v);
////                float3 rayPos = camPos;
////                float3 throughput = (float3)(1.0f, 1.0f, 1.0f);
////                float3 accumulated = (float3)(0.0f, 0.0f, 0.0f);
////
////                for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
////                    int mapX = (int)floor(rayPos.x);
////                    int mapY = (int)floor(rayPos.y);
////                    int mapZ = (int)floor(rayPos.z);
////
////                    float3 deltaDist = (float3)(fabs(1.0f/rayDir.x), fabs(1.0f/rayDir.y), fabs(1.0f/rayDir.z));
////                    int3 step;
////                    float3 sideDist;
////
////                    if (rayDir.x < 0) { step.x = -1; sideDist.x = (rayPos.x - mapX) * deltaDist.x; }
////                    else              { step.x = 1;  sideDist.x = (mapX + 1.0f - rayPos.x) * deltaDist.x; }
////                    if (rayDir.y < 0) { step.y = -1; sideDist.y = (rayPos.y - mapY) * deltaDist.y; }
////                    else              { step.y = 1;  sideDist.y = (mapY + 1.0f - rayPos.y) * deltaDist.y; }
////                    if (rayDir.z < 0) { step.z = -1; sideDist.z = (rayPos.z - mapZ) * deltaDist.z; }
////                    else              { step.z = 1;  sideDist.z = (mapZ + 1.0f - rayPos.z) * deltaDist.z; }
////
////                    int hit = 0;
////                    int side = 0;
////                    int blockID = 0;
////                    float dist = 0.0f;
////
////                    while (hit == 0 && dist < 120.0f) {
////                        if (sideDist.x < sideDist.y) {
////                            if (sideDist.x < sideDist.z) {
////                                dist = sideDist.x; sideDist.x += deltaDist.x; mapX += step.x; side = 0;
////                            } else {
////                                dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2;
////                            }
////                        } else {
////                            if (sideDist.y < sideDist.z) {
////                                dist = sideDist.y; sideDist.y += deltaDist.y; mapY += step.y; side = 1;
////                            } else {
////                                dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2;
////                            }
////                        }
////                        blockID = getBlock(worldData, mapX - originX, mapY - originY, mapZ - originZ);
////                        if (blockID > 0) hit = 1;
////                    }
////
////                    if (hit) {
////                        MaterialInfo mat = getMaterial(materials, blockID);
////
////                        if (mat.emission > 0.0f) {
//////                          accumulated += throughput * mat.baseColor * mat.emission;
////                            break;
////                        }
////
////                        float3 normal = (float3)(0,0,0);
////                        float2 uv = (float2)(0,0);
////                        int faceIndex = 0;
////
////                        float3 hitPos = rayPos + rayDir * dist;
////                        float3 localHit = hitPos - (float3)(mapX, mapY, mapZ);
////
////                        if (side == 0) {
////                             normal.x = -step.x;
////                             if (step.x > 0) { faceIndex = 0; uv = (float2)(localHit.z, 1.0f - localHit.y); }
////                             else            { faceIndex = 1; uv = (float2)(1.0f - localHit.z, 1.0f - localHit.y); }
////                        } else if (side == 1) {
////                             normal.y = -step.y;
////                             if (step.y > 0) { faceIndex = 2; uv = (float2)(localHit.x, localHit.z); }
////                             else            { faceIndex = 3; uv = (float2)(localHit.x, 1.0f - localHit.z); }
////                        } else {
////                             normal.z = -step.z;
////                             if (step.z > 0) { faceIndex = 4; uv = (float2)(1.0f - localHit.x, 1.0f - localHit.y); }
////                             else            { faceIndex = 5; uv = (float2)(localHit.x, 1.0f - localHit.y); }
////                        }
////
////                        // --- 正常渲染流程 ---
////                        float3 texColor = sampleTexture(textures, mat.texOffset, faceIndex, uv.x, uv.y);
////
////                        // 混合：
////                        // 如果有纹理，mat.baseColor 是 1.0 (白)，结果 = 纹理原色
////                        // 如果没纹理，mat.baseColor 是 Fallback，texColor 是 1.0 (白)，结果 = Fallback
////                        float3 albedo = texColor * mat.baseColor;
////
////                        accumulated += albedo;
////
////                        rayPos = hitPos + normal * 0.001f;
////                        throughput *= albedo;
////
////                        if (bounce > 2) {
////                            float p = max(throughput.x, max(throughput.y, throughput.z));
////                            if (randomFloat(&seed) > p) break;
////                            throughput *= 1.0f / p;
////                        }
////
////                        float3 diffuseDir = sampleHemisphere(normal, &seed);
////                        float3 specularDir = rayDir - 2.0f * dot(rayDir, normal) * normal;
////
////                        if (mat.spread < 1.0f) {
////                            float3 fuzz = (float3)(randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f);
////                            specularDir = normalize(specularDir + fuzz * mat.spread);
////                            if (dot(specularDir, normal) < 0) specularDir = diffuseDir;
////                        }
////
////                        if (randomFloat(&seed) < mat.spread) {
////                            rayDir = diffuseDir;
////                        } else {
////                            rayDir = specularDir;
////                        }
////
////                    } else {
////                        // Sky
////                        float t = 0.5f * (rayDir.y + 1.0f);
////                        float3 skyColor = (1.0f - t) * (float3)(1.0f, 1.0f, 1.0f) + t * (float3)(0.5f, 0.7f, 1.0f);
////                        accumulated += throughput * skyColor * 1.5f;
////                        break;
////                    }
////                }
////                finalColor += accumulated;
////            }
////
////            finalColor /= (float)$SAMPLES_PER_PIXEL;
////            finalColor = pow(finalColor, (float3)(1.0f/2.2f));
////
////            int r = clamp((int)(finalColor.x * 255.0f), 0, 255);
////            int g = clamp((int)(finalColor.y * 255.0f), 0, 255);
////            int b = clamp((int)(finalColor.z * 255.0f), 0, 255);
////
////            outPixels[gid] = (255 << 24) | (r << 16) | (g << 8) | b;
////        }
////    """.trimIndent()
//    private val kernelSource = """
//        #define MAX_BOUNCES $MAX_BOUNCES
//        #define WORLD_SIZE $WORLD_SIZE
//        #define PI 3.14159265359f
//        #define TEXTURE_SIZE 16
//
//        // --- 随机数生成 ---
//        uint hash(uint x) {
//            x += (x << 10u); x ^= (x >> 6u); x += (x << 3u);
//            x ^= (x >> 11u); x += (x << 15u); return x;
//        }
//        float randomFloat(uint* state) {
//            *state = hash(*state);
//            return (float)(*state) / (float)(0xFFFFFFFFu);
//        }
//
//        // --- 几何与采样 ---
//        void createCoordinateSystem(float3 N, float3* Nt, float3* Nb) {
//            if (fabs(N.x) > fabs(N.y)) *Nt = (float3)(N.z, 0, -N.x) / sqrt(N.x * N.x + N.z * N.z);
//            else *Nt = (float3)(0, -N.z, N.y) / sqrt(N.y * N.y + N.z * N.z);
//            *Nb = cross(N, *Nt);
//        }
//
//        float3 sampleHemisphere(float3 normal, uint* seed) {
//            float r1 = randomFloat(seed);
//            float r2 = randomFloat(seed);
//            float sinTheta = sqrt(1.0f - r2);
//            float phi = 2.0f * PI * r1;
//            float x = sinTheta * cos(phi);
//            float z = sinTheta * sin(phi);
//            float y = sqrt(r2);
//            float3 Nt, Nb;
//            createCoordinateSystem(normal, &Nt, &Nb);
//            return (Nt * x + normal * y + Nb * z);
//        }
//
//        int getBlock(__global int* world, int x, int y, int z) {
//            if(x < 0 || y < 0 || z < 0 || x >= WORLD_SIZE || y >= WORLD_SIZE || z >= WORLD_SIZE) return 0;
//            return world[x + y * WORLD_SIZE + z * WORLD_SIZE * WORLD_SIZE];
//        }
//
//        // --- 材质查找结构 ---
//        typedef struct {
//            int texOffset;
//            float emission;
//            float spread;
//            float3 baseColor; // 无论是发光还是漫反射，都存这里
//        } MaterialInfo;
//
//        MaterialInfo getMaterial(__global float4* matBuffer, int matID) {
//            float4 data = matBuffer[matID];
//            MaterialInfo info;
//            info.texOffset = (int)data.x;
//            info.emission = data.y;
//            info.spread = data.z;
//
//            // 解包颜色 (ARGB int -> float3)
//            int c = as_int(data.w);
//            float r = (float)((c >> 16) & 0xFF) / 255.0f;
//            float g = (float)((c >> 8) & 0xFF) / 255.0f;
//            float b = (float)(c & 0xFF) / 255.0f;
//            info.baseColor = (float3)(r, g, b);
//
//            return info;
//        }
//
//        float3 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
//            // 状态 1: 红色 (RED)
//            // 含义: Host端传递的 Offset 是 -1。说明 Kotlin 认为这个方块没有材质。
//            if (baseOffset < 0) return (float3)(1.0f, 0.0f, 0.0f);
//
//            int tx = clamp((int)(u * 16.0f), 0, 15);
//            int ty = clamp((int)(v * 16.0f), 0, 15);
//
//            int pixelIndex = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
//            int argb = textures[pixelIndex];
//
//            // 状态 2: 绿色 (GREEN)
//            // 含义: 读取到的像素数据是 0 (透明/空)。
//            // 既然Debug模式有纹理，如果这里变绿，说明正常模式下 faceIndex 或 pixelIndex 算错了。
//            if (argb == 0) return (float3)(0.0f, 1.0f, 0.0f);
//
//            float r = (float)((argb >> 16) & 0xFF) / 255.0f;
//            float g = (float)((argb >> 8) & 0xFF) / 255.0f;
//            float b = (float)(argb & 0xFF) / 255.0f;
//            return (float3)(r, g, b);
//        }
//
//        // --- 纹理采样 ---
////        float3 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
////            // 【新增】如果 offset 是 -1，说明 Host 端没找到贴图
////            // 直接返回纯白 (1.0)，这样最终颜色 = 1.0 * BaseColor (显示 Fallback 颜色)
////            // 或者你可以返回洋红色 (1.0, 0.0, 1.0) 来作为“丢失材质”的调试色
////            if (baseOffset < 0) return (float3)(1.0f, 0.0f, 1.0f);
////
////            int tx = clamp((int)(u * 16.0f), 0, 15);
////            int ty = clamp((int)(v * 16.0f), 0, 15);
////
////            // 计算绝对索引
////            int pixelIndex = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
////
////            int argb = textures[pixelIndex];
////
////            // 如果读到 0 (全透明/无数据)，也返回白色以便显示 BaseColor
////            if (argb == 0) return (float3)(1.0f, 1.0f, 1.0f);
////
////            float r = (float)((argb >> 16) & 0xFF) / 255.0f;
////            float g = (float)((argb >> 8) & 0xFF) / 255.0f;
////            float b = (float)(argb & 0xFF) / 255.0f;
////            return (float3)(r, g, b);
////        }
//
//        // --- Path Tracer ---
//        __kernel void pathTraceKernel(
//            __global int* worldData,
//            __global int* outPixels,
//            __global uint* seeds,
//            __global int* textures,
//            __global float4* materials,
//            const float3 camPos,
//            const float3 camDir,
//            const float3 camRight,
//            const float3 camUp,
//            const int width,
//            const int height,
//            const int originX,
//            const int originY,
//            const int originZ,
//            const int frameCount
//        ) {
//            int gid = get_global_id(0);
//            if (gid >= width * height) return;
//
//            int screenX = gid % width;
//            int screenY = gid / width;
//            uint seed = seeds[gid] + frameCount * 719393;
//
//            float3 finalColor = (float3)(0,0,0);
//
//            for (int s = 0; s < $SAMPLES_PER_PIXEL; s++) {
//                float uOffset = randomFloat(&seed) - 0.5f;
//                float vOffset = randomFloat(&seed) - 0.5f;
//                float u = ((float)screenX + uOffset) / (float)width * 2.0f - 1.0f;
//                float v = ((float)screenY + vOffset) / (float)height * 2.0f - 1.0f;
//
//                float3 rayDir = normalize(camDir + camRight * u + camUp * v);
//                float3 rayPos = camPos;
//                float3 throughput = (float3)(1.0f, 1.0f, 1.0f);
//                float3 accumulated = (float3)(0.0f, 0.0f, 0.0f);
//
//                for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
//                    int mapX = (int)floor(rayPos.x);
//                    int mapY = (int)floor(rayPos.y);
//                    int mapZ = (int)floor(rayPos.z);
//
//                    float3 deltaDist = (float3)(fabs(1.0f/rayDir.x), fabs(1.0f/rayDir.y), fabs(1.0f/rayDir.z));
//                    int3 step;
//                    float3 sideDist;
//
//                    if (rayDir.x < 0) { step.x = -1; sideDist.x = (rayPos.x - mapX) * deltaDist.x; }
//                    else              { step.x = 1;  sideDist.x = (mapX + 1.0f - rayPos.x) * deltaDist.x; }
//                    if (rayDir.y < 0) { step.y = -1; sideDist.y = (rayPos.y - mapY) * deltaDist.y; }
//                    else              { step.y = 1;  sideDist.y = (mapY + 1.0f - rayPos.y) * deltaDist.y; }
//                    if (rayDir.z < 0) { step.z = -1; sideDist.z = (rayPos.z - mapZ) * deltaDist.z; }
//                    else              { step.z = 1;  sideDist.z = (mapZ + 1.0f - rayPos.z) * deltaDist.z; }
//
//                    int hit = 0;
//                    int side = 0;
//                    int blockID = 0;
//                    float dist = 0.0f;
//
//                    // DDA 算法
//                    while (hit == 0 && dist < 120.0f) {
//                        if (sideDist.x < sideDist.y) {
//                            if (sideDist.x < sideDist.z) {
//                                dist = sideDist.x; sideDist.x += deltaDist.x; mapX += step.x; side = 0;
//                            } else {
//                                dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2;
//                            }
//                        } else {
//                            if (sideDist.y < sideDist.z) {
//                                dist = sideDist.y; sideDist.y += deltaDist.y; mapY += step.y; side = 1;
//                            } else {
//                                dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2;
//                            }
//                        }
//                        blockID = getBlock(worldData, mapX - originX, mapY - originY, mapZ - originZ);
//                        if (blockID > 0) hit = 1;
//                    }
//
//                    if (hit) {
////                        MaterialInfo mat = getMaterial(materials, blockID);
////
////                        // 1. 处理发光 (Emissive)
////                        if (mat.emission > 0.0f) {
////                            // 发光体直接使用 baseColor 作为光源颜色
////                            accumulated += throughput * mat.baseColor * mat.emission;
////                            break;
////                        }
////
////                        // 2. 计算几何信息
////                        float3 normal = (float3)(0,0,0);
////                        float2 uv = (float2)(0,0);
////                        int faceIndex = 0;
////
////                        float3 hitPos = rayPos + rayDir * dist;
////                        float3 localHit = hitPos - (float3)(mapX, mapY, mapZ);
////
////                        if (side == 0) {
////                             normal.x = -step.x;
////                             if (step.x > 0) { faceIndex = 0; uv = (float2)(localHit.z, 1.0f - localHit.y); }
////                             else            { faceIndex = 1; uv = (float2)(1.0f - localHit.z, 1.0f - localHit.y); }
////                        } else if (side == 1) {
////                             normal.y = -step.y;
////                             if (step.y > 0) { faceIndex = 2; uv = (float2)(localHit.x, localHit.z); }
////                             else            { faceIndex = 3; uv = (float2)(localHit.x, 1.0f - localHit.z); }
////                        } else {
////                             normal.z = -step.z;
////                             if (step.z > 0) { faceIndex = 4; uv = (float2)(1.0f - localHit.x, 1.0f - localHit.y); }
////                             else            { faceIndex = 5; uv = (float2)(localHit.x, 1.0f - localHit.y); }
////                        }
////
////                        // 3. 获取颜色 (纹理 * 材质颜色)
////                        // 关键修复：即使没有纹理，也使用 mat.baseColor 作为基础色
////                        float3 texColor = sampleTexture(textures, mat.texOffset, faceIndex, uv.x, uv.y);
////
////                        // 混合模式：纹理颜色 * 材质基色
////                        // 如果纹理未加载（全白/全灰），则显示 baseColor
////                        float3 albedo = texColor * mat.baseColor;
////
////                        rayPos = hitPos + normal * 0.001f;
////                        throughput *= albedo;
////
////                        // 4. 俄罗斯轮盘赌
////                        if (bounce > 2) {
////                            float p = max(throughput.x, max(throughput.y, throughput.z));
////                            if (randomFloat(&seed) > p) break;
////                            throughput *= 1.0f / p;
////                        }
////
////                        // 5. 材质混合 (漫反射 vs 镜面反射)
////                        float3 diffuseDir = sampleHemisphere(normal, &seed);
////                        float3 specularDir = rayDir - 2.0f * dot(rayDir, normal) * normal;
////
////                        // 粗糙度处理
////                        if (mat.spread < 1.0f) {
////                            float3 fuzz = (float3)(randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f);
////                            specularDir = normalize(specularDir + fuzz * mat.spread);
////                            if (dot(specularDir, normal) < 0) specularDir = diffuseDir;
////                        }
////
////                        if (randomFloat(&seed) < mat.spread) {
////                            rayDir = diffuseDir;
////                        } else {
////                            rayDir = specularDir;
////                        }
//
//
//
//
//                        MaterialInfo mat = getMaterial(materials, blockID);
//
//                        if (mat.emission > 0.0f) {
//                          accumulated += throughput * mat.baseColor * mat.emission;
////                          printf(
////                              "gid=%d accumulated = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              accumulated.x / s,
////                              accumulated.y / s,
////                              accumulated.z / s
////                          );
//                            break;
//                        }
//
//                        float3 normal = (float3)(0,0,0);
//                        float2 uv = (float2)(0,0);
//                        int faceIndex = 0;
//
//                        float3 hitPos = rayPos + rayDir * dist;
//                        float3 localHit = hitPos - (float3)(mapX, mapY, mapZ);
//
//                        if (side == 0) {
//                             normal.x = -step.x;
//                             if (step.x > 0) { faceIndex = 0; uv = (float2)(localHit.z, 1.0f - localHit.y); }
//                             else            { faceIndex = 1; uv = (float2)(1.0f - localHit.z, 1.0f - localHit.y); }
//                        } else if (side == 1) {
//                             normal.y = -step.y;
//                             if (step.y > 0) { faceIndex = 2; uv = (float2)(localHit.x, localHit.z); }
//                             else            { faceIndex = 3; uv = (float2)(localHit.x, 1.0f - localHit.z); }
//                        } else {
//                             normal.z = -step.z;
//                             if (step.z > 0) { faceIndex = 4; uv = (float2)(1.0f - localHit.x, 1.0f - localHit.y); }
//                             else            { faceIndex = 5; uv = (float2)(localHit.x, 1.0f - localHit.y); }
//                        }
//
//                        // --- 正常渲染流程 ---
//                        float3 texColor = sampleTexture(textures, mat.texOffset, faceIndex, uv.x, uv.y);
////                       printf(
////                           "gid=%d texColor = R:%f G:%f B:%f\n",
////                           get_global_id(0),
////                           texColor.x,
////                           texColor.y,
////                           texColor.z
////                       );
//
//                        // 混合：
//                        // 如果有纹理，mat.baseColor 是 1.0 (白)，结果 = 纹理原色
//                        // 如果没纹理，mat.baseColor 是 Fallback，texColor 是 1.0 (白)，结果 = Fallback
//                        float3 albedo = texColor * mat.baseColor;
//
////                        printf(
////                              "gid=%d albedo = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              albedo.x,
////                              albedo.y,
////                              albedo.z
////                          );
//
////                          printf(
////                              "gid=%d mat.baseColor = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              mat.baseColor.x,
////                              mat.baseColor.y,
////                              mat.baseColor.z
////                          );
//
//                        rayPos = hitPos + normal * 0.001f;
//                        throughput *= albedo;
//
////                          printf(
////                              "gid=%d mat.baseColor = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              mat.baseColor.x,
////                              mat.baseColor.y,
////                              mat.baseColor.z
////                          );
//
//                        if (bounce > 2) {
//                            float p = max(throughput.x, max(throughput.y, throughput.z));
//                            if (randomFloat(&seed) > p) break;
//                            throughput *= 1.0f / p;
//                        }
//
//                        float3 diffuseDir = sampleHemisphere(normal, &seed);
//                        float3 specularDir = rayDir - 2.0f * dot(rayDir, normal) * normal;
//
//                        if (mat.spread < 1.0f) {
//                            float3 fuzz = (float3)(randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f);
//                            specularDir = normalize(specularDir + fuzz * mat.spread);
//                            if (dot(specularDir, normal) < 0) specularDir = diffuseDir;
//                        }
//
//                        if (randomFloat(&seed) < mat.spread) {
//                            rayDir = diffuseDir;
//                        } else {
//                            rayDir = specularDir;
//                        }
//
////MaterialInfo mat = getMaterial(materials, blockID);
////
////    // 保持这一段计算 UV 的逻辑不变
////    float3 normal = (float3)(0,0,0);
////    float2 uv = (float2)(0,0);
////    int faceIndex = 0;
////    float3 hitPos = rayPos + rayDir * dist;
////    float3 localHit = hitPos - (float3)(mapX, mapY, mapZ);
////
////    if (side == 0) {
////        normal.x = -step.x;
////        if (step.x > 0) { faceIndex = 0; uv = (float2)(localHit.z, 1.0f - localHit.y); }
////        else            { faceIndex = 1; uv = (float2)(1.0f - localHit.z, 1.0f - localHit.y); }
////    } else if (side == 1) {
////        normal.y = -step.y;
////        if (step.y > 0) { faceIndex = 2; uv = (float2)(localHit.x, localHit.z); }
////        else            { faceIndex = 3; uv = (float2)(localHit.x, 1.0f - localHit.z); }
////    } else {
////        normal.z = -step.z;
////        if (step.z > 0) { faceIndex = 4; uv = (float2)(1.0f - localHit.x, 1.0f - localHit.y); }
////        else            { faceIndex = 5; uv = (float2)(localHit.x, 1.0f - localHit.y); }
////    }
////
////    // ============== 调试模式 ==============
////
////    // 1. 强制读取纹理，不管 offset 是多少 (假设是 Stone=0)
////    // 这样能排除 getMaterial 返回错误 offset 的可能性
////    int debugOffset = mat.texOffset;
////
////    // 2. 采样
////    // 注意：这里我们手动把 faceIndex 设为 0-5 的某个值测试，或者信赖上面的计算
////    float3 rawTex = sampleTexture(textures, debugOffset, faceIndex, uv.x, uv.y);
////
////    // 3. 直接输出颜色，不进行任何光照计算
////    // 如果屏幕是黑的 -> UV 算错或者是负数
////    // 如果屏幕是白的 -> 显存全是 0 (且 sampleTexture 返回了默认白)
////    // 如果屏幕有画面 -> 之前的光照/BaseColor 逻辑有 bug
////    accumulated = rawTex;
////
////    // 强制结束光线反弹，只显示第一帧结果
////    break;
//    // ============== 调试结束 ==============
//
//                    } else {
//                        // 天空光
//                        float t = 0.5f * (rayDir.y + 1.0f);
//                        float3 skyColor = (1.0f - t) * (float3)(1.0f, 1.0f, 1.0f) + t * (float3)(0.5f, 0.7f, 1.0f);
//                        accumulated += throughput * skyColor * 1.5f;
//                        break;
//                    }
//                }
////                            printf(
////                              "gid=%d accumulated = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              accumulated.x,
////                              accumulated.y,
////                              accumulated.z
////                          );
//                finalColor += accumulated;
//            }
//
////            printf(
////                              "gid=%d finalColor = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              finalColor.x,
////                              finalColor.y,
////                              finalColor.z
////                          );
//
//            // Gamma 校正与输出
//            finalColor /= (float)$SAMPLES_PER_PIXEL;
//
////            printf(
////                              "gid=%d finalColorDived = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              finalColor.x,
////                              finalColor.y,
////                              finalColor.z
////                          );
////            finalColor = pow(finalColor, (float3)(1.0f/2.2f));
//
////            printf(
////                              "gid=%d gammaFixed = R:%f G:%f B:%f\n",
////                              get_global_id(0),
////                              finalColor.x,
////                              finalColor.y,
////                              finalColor.z
////                          );
//
//            float scale = 1.0f;
//
//            int r = clamp((int)(finalColor.x * scale), 0, 255);
//            int g = clamp((int)(finalColor.y * scale), 0, 255);
//            int b = clamp((int)(finalColor.z * scale), 0, 255);
//
////            printf(
////                              "gid=%d finalColor = R:%d G:%d B:%d\n",
////                              get_global_id(0),
////                              r,
////                              g,
////                              b
////                          );
//
//            outPixels[gid] = (255 << 24) | (r << 16) | (g << 8) | b;
//        }
//    """.trimIndent()
//
//    // -------------------------------------------------------------------------
//    // Kotlin Host 逻辑
//    // -------------------------------------------------------------------------
//
//    private var cpuAccumulator: FloatArray = FloatArray(0)
//
//    override fun updateCamera(
//        player: Player?,
//        mixinTimes: Int,
//        maxDepth: Float
//    ): Pair<BufferedImage, Array<FloatArray>> {
//
//        if (!isInitialized) initOpenCL()
//
//        val pixelCount = width * height
//        val requiredSize = pixelCount * 3
//        if (cpuAccumulator.size != requiredSize) {
//            cpuAccumulator = FloatArray(requiredSize)
//        }
//        cpuAccumulator.fill(0f)
//
//        val renderLoc = player?.eyeLocation ?: this.location
//        val dir = renderLoc.direction
//        updateWorldDataIfNeeded(renderLoc)
//
//        val vFovRad = Math.toRadians(this.fov)
//        val halfHeight = Math.tan(vFovRad / 2.0)
//        val halfWidth = halfHeight * (width.toDouble() / height.toDouble())
//        val w = dir.clone().normalize()
//        val up = Vector(0, 1, 0)
//        val u = w.clone().crossProduct(up).normalize()
//        val v = u.clone().crossProduct(w).normalize().multiply(-1)
//        val camRight = u.multiply(halfWidth)
//        val camUp = v.multiply(halfHeight)
//
//        var a = 0
//        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memWorld))
//        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memPixelOut))
//        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memSeeds))
//        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memTextures))
//        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memMaterials))
//
//        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(renderLoc.x.toFloat(), renderLoc.y.toFloat(), renderLoc.z.toFloat())))
//        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(w.x.toFloat(), w.y.toFloat(), w.z.toFloat())))
//        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(camRight.x.toFloat(), camRight.y.toFloat(), camRight.z.toFloat())))
//        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(camUp.x.toFloat(), camUp.y.toFloat(), camUp.z.toFloat())))
//
//        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(width)))
//        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(height)))
//
//        val origin = lastWorldCenter!!
//        val ox = origin.blockX - WORLD_SIZE / 2
//        val oy = origin.blockY - WORLD_SIZE / 2
//        val oz = origin.blockZ - WORLD_SIZE / 2
//        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(ox)))
//        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(oy)))
//        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(oz)))
//
//        val frameCountArgIndex = a
//        val loops = if (mixinTimes < 1) 1 else mixinTimes
//        val globalWorkSize = longArrayOf(pixelCount.toLong())
//
//        for (i in 0 until loops) {
//            val seedOffset = (System.currentTimeMillis() % 100000).toInt() + (i * 719)
//            clSetKernelArg(kernel, frameCountArgIndex, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(seedOffset)))
//            clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null)
//
//            clEnqueueReadBuffer(queue, memPixelOut, CL_TRUE, 0, (Sizeof.cl_int * pixelCount).toLong(), Pointer.to(pixelBuffer), 0, null, null)
//
//            // CPU 累加 (用于平滑噪点)
//            for (p in 0 until pixelCount) {
//                val rgb = pixelBuffer[p]
//                cpuAccumulator[p * 3] += ((rgb shr 16) and 0xFF).toFloat()
//                cpuAccumulator[p * 3 + 1] += ((rgb shr 8) and 0xFF).toFloat()
//                cpuAccumulator[p * 3 + 2] += (rgb and 0xFF).toFloat()
//            }
//        }
//
//        // 输出到 BufferedImage
//        val div = loops.toFloat()
//        for (p in 0 until pixelCount) {
//            val idx = p * 3
//            val r = (cpuAccumulator[idx] / div).toInt().coerceIn(0, 255)
//            val g = (cpuAccumulator[idx + 1] / div).toInt().coerceIn(0, 255)
//            val b = (cpuAccumulator[idx + 2] / div).toInt().coerceIn(0, 255)
//            pixelBuffer[p] = (255 shl 24) or (r shl 16) or (g shl 8) or b
//        }
//
//        bufferedImage.setRGB(0, 0, width, height, pixelBuffer, 0, width)
//        return Pair(bufferedImage, depthImage)
//    }
//
//    private fun updateWorldDataIfNeeded(center: Location) {
//        val dist = lastWorldCenter?.distance(center) ?: 999.0
//        if (dist < 8.0) return
//
//        lastWorldCenter = center.clone()
//        val sx = center.blockX - WORLD_SIZE / 2
//        val sy = center.blockY - WORLD_SIZE / 2
//        val sz = center.blockZ - WORLD_SIZE / 2
//        val world = center.world
//
//        var idx = 0
//        for (z in 0 until WORLD_SIZE) {
//            for (y in 0 until WORLD_SIZE) {
//                for (x in 0 until WORLD_SIZE) {
//                    val mat = world.getBlockAt(sx + x, sy + y, sz + z).type
//                    worldDataHost[idx++] = mat.ordinal
//                }
//            }
//        }
//        clEnqueueWriteBuffer(queue, memWorld, CL_TRUE, 0, (Sizeof.cl_int * worldDataHost.size).toLong(), Pointer.to(worldDataHost), 0, null, null)
//    }
//
//
//    // --- 简单的颜色辅助函数 (用于测试) ---
//    private fun getFallbackColor(mat: Material): Int {
//        // 你可以稍后扩展这里，使用 mapColor 或其他方式
//        // 这里手动映射几个关键颜色，确保测试图里有颜色
//        return when {
//            mat == Material.GOLD_BLOCK -> 0xFFD700 // 金色
//            mat == Material.RED_CONCRETE -> 0xFF0000 // 红色
//            mat == Material.LIME_CONCRETE || mat == Material.GRASS_BLOCK -> 0x00FF00 // 绿色
//            mat == Material.BLUE_CONCRETE || mat == Material.WATER -> 0x0000FF // 蓝色
//            mat == Material.WHITE_CONCRETE || mat == Material.IRON_BLOCK -> 0xDDDDDD // 白色
//            mat == Material.STONE -> 0x7F7F7F // 石头灰
//            mat == Material.DIRT -> 0x5C4033 // 泥土褐
//            mat == Material.SEA_LANTERN || mat == Material.GLOWSTONE -> 0xFFFFFF // 光源白
//            else -> 0xFFFFFF // 默认白色
//        }
//    }
//
//    private fun initOpenCL() {
//
//        TextureManager.debugPrintTextureStatus()
//        CL.setExceptionsEnabled(true)
//        val numPlatforms = IntArray(1)
//        clGetPlatformIDs(0, null, numPlatforms)
//        val platform = arrayOfNulls<cl_platform_id>(1).apply { clGetPlatformIDs(1, this, null) }[0]
//
//        val device = arrayOfNulls<cl_device_id>(1)
//        clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, device, null)
//
//        val contextProps = cl_context_properties()
//        contextProps.addProperty(CL_CONTEXT_PLATFORM.toLong(), platform)
//        context = clCreateContext(contextProps, 1, device, null, null, null)
//        queue = clCreateCommandQueueWithProperties(context, device[0], null, null)
//
//        program = clCreateProgramWithSource(context, 1, arrayOf(kernelSource), null, null)
//        try {
//            clBuildProgram(program, 0, null, null, null, null)
//        } catch (e: Exception) {
//            val size = LongArray(1)
//            clGetProgramBuildInfo(program, device[0], CL_PROGRAM_BUILD_LOG, 0, null, size)
//            val buffer = ByteArray(size[0].toInt())
//            clGetProgramBuildInfo(program, device[0], CL_PROGRAM_BUILD_LOG, size[0], Pointer.to(buffer), null)
//            throw RuntimeException("CL Build Error: " + String(buffer))
//        }
//        kernel = clCreateKernel(program, "pathTraceKernel", null)
//
//        // --- 准备材质数据 (修复颜色问题) ---
//        val maxMatId = Material.entries.size
//        materialDataHost = FloatArray(maxMatId * 4)
//
////        for (mat in Material.entries) {
////            val id = mat.ordinal
////            val rtMat = RayTraceMaterial.getMaterialReflectionData(mat)
////            val texOffset = TextureManager.getMaterialOffset(mat)
////
////            materialDataHost[id * 4 + 0] = texOffset.toFloat()
////
////            if (rtMat.isLight) {
////                // 发光物体：设置强度，Roughness=1.0，颜色
////                materialDataHost[id * 4 + 1] = rtMat.brightness
////                materialDataHost[id * 4 + 2] = 1.0f
////                val c = (rtMat.lightColor.x shl 16) or (rtMat.lightColor.y shl 8) or rtMat.lightColor.z
////                materialDataHost[id * 4 + 3] = Float.fromBits(c)
////            } else {
////                // 普通物体：无发光，设置Roughness，设置**回退颜色**
////                materialDataHost[id * 4 + 1] = 0.0f
////                materialDataHost[id * 4 + 2] = rtMat.spread
////
////                // 关键修改：即使不发光，也传入一个颜色作为 Albedo (漫反射颜色)
////                // 这样当纹理缺失时，会显示这个颜色而不是灰色
////                val fallbackRGB = getFallbackColor(mat)
////                materialDataHost[id * 4 + 3] = Float.fromBits(fallbackRGB)
////            }
////        }
//
//
//        for (mat in Material.entries) {
//            val id = mat.ordinal
//            val rtMat = RayTraceMaterial.getMaterialReflectionData(mat)
//            val texOffset = TextureManager.getMaterialOffset(mat)
//
//            materialDataHost[id * 4 + 0] = texOffset.toFloat()
//
//            if (rtMat.isLight) {
//                // 发光物体逻辑保持不变
//                materialDataHost[id * 4 + 1] = rtMat.brightness
//                materialDataHost[id * 4 + 2] = 1.0f
//                val c = (rtMat.lightColor.x shl 16) or (rtMat.lightColor.y shl 8) or rtMat.lightColor.z
//                materialDataHost[id * 4 + 3] = Float.fromBits(c)
//            } else {
//                // 普通物体
//                materialDataHost[id * 4 + 1] = 0.0f
//                materialDataHost[id * 4 + 2] = rtMat.spread
//
//                // --- 修复开始 ---
//                if (texOffset >= 0) {
//                    // 【关键】如果有纹理，BaseColor 必须是白色 (0xFFFFFF)，否则会和纹理颜色相乘导致变色
//                    // 只有当 sampleTexture 返回纯白时，这里设为白色才能还原纹理原色
//                    materialDataHost[id * 4 + 3] = Float.fromBits(0xFFFFFF)
//                } else {
//                    // 如果没有纹理 (offset = -1)，则使用 Fallback 颜色
//                    val fallbackRGB = getFallbackColor(mat)
//                    materialDataHost[id * 4 + 3] = Float.fromBits(fallbackRGB)
//                }
//                // --- 修复结束 ---
//            }
//        }
//        // --- 分配显存 ---
//        memWorld = clCreateBuffer(context, CL_MEM_READ_ONLY, (Sizeof.cl_int * worldDataHost.size).toLong(), null, null)
//        memPixelOut = clCreateBuffer(context, CL_MEM_WRITE_ONLY, (Sizeof.cl_int * width * height).toLong(), null, null)
//        memSeeds = clCreateBuffer(context, CL_MEM_READ_WRITE, (Sizeof.cl_uint * width * height).toLong(), null, null)
//        memMaterials = clCreateBuffer(context, CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR, (Sizeof.cl_float * materialDataHost.size).toLong(), Pointer.to(materialDataHost), null)
//
//        // 纹理 (确保 TextureManager 即使为空也不崩)
//        var texArray = TextureManager.textureArray
//        if (texArray == null || texArray.isEmpty()) {
//            texArray = IntArray(256 * 256) // 占位符，防止Crash
//        }
//        memTextures = clCreateBuffer(context, CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR, (Sizeof.cl_int * texArray.size).toLong(), Pointer.to(texArray), null)
//
//        val r = Random()
//        for(i in seedsHost.indices) seedsHost[i] = r.nextInt()
//        clEnqueueWriteBuffer(queue, memSeeds, CL_TRUE, 0, (Sizeof.cl_uint * width * height).toLong(), Pointer.to(seedsHost), 0, null, null)
//
//        isInitialized = true
//    }
//
//    fun cleanup() {
//        if (!isInitialized) return
//        if (memWorld != null) clReleaseMemObject(memWorld)
//        if (memPixelOut != null) clReleaseMemObject(memPixelOut)
//        if (memSeeds != null) clReleaseMemObject(memSeeds)
//        if (memTextures != null) clReleaseMemObject(memTextures)
//        if (memMaterials != null) clReleaseMemObject(memMaterials)
//
//        if (kernel != null) clReleaseKernel(kernel)
//        if (program != null) clReleaseProgram(program)
//        if (queue != null) clReleaseCommandQueue(queue)
//        if (context != null) clReleaseContext(context)
//        isInitialized = false
//    }
//
//    override fun getColorInWorld(
//        rayTraceResult: HitResult?, startDirection: Vector3f, flatBVHNode: Array<FlatBVHNode>, bvhTree: BVHTree
//    ): java.awt.Color = java.awt.Color.BLACK
//}
// -------------------------------------------------------------------------
// OpenCL Kernel (V3.0 Final)
// -------------------------------------------------------------------------
//    private val kernelSource = """
//        #define MAX_BOUNCES $MAX_BOUNCES
//        #define WORLD_SIZE $WORLD_SIZE
//        #define PI 3.14159265359f
//        #define EPSILON 0.001f
//
//        // --- Utilities ---
//        uint hash(uint x) {
//            x += (x << 10u); x ^= (x >> 6u); x += (x << 3u);
//            x ^= (x >> 11u); x += (x << 15u); return x;
//        }
//        float randomFloat(uint* state) {
//            *state = hash(*state);
//            return (float)(*state) / (float)(0xFFFFFFFFu);
//        }
//
//        // --- Tone Mapping (ACES) ---
//        float3 ACESFilm(float3 x) {
//            float a = 2.51f; float b = 0.03f; float c = 2.43f; float d = 0.59f; float e = 0.14f;
//            return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0f, 1.0f);
//        }
//
//        // --- Geometry & Sampling ---
//        void createCoordinateSystem(float3 N, float3* Nt, float3* Nb) {
//            if (fabs(N.x) > fabs(N.y)) *Nt = (float3)(N.z, 0, -N.x) / sqrt(N.x * N.x + N.z * N.z);
//            else *Nt = (float3)(0, -N.z, N.y) / sqrt(N.y * N.y + N.z * N.z);
//            *Nb = cross(N, *Nt);
//        }
//
//        // Cosine Weighted Sampling (Better Diffuse)
//        float3 sampleCosineHemisphere(float3 normal, uint* seed) {
//            float r1 = randomFloat(seed);
//            float r2 = randomFloat(seed);
//            float sinTheta = sqrt(1.0f - r1);
//            float phi = 2.0f * PI * r2;
//            float x = sinTheta * cos(phi);
//            float z = sinTheta * sin(phi);
//            float y = sqrt(r1);
//            float3 Nt, Nb;
//            createCoordinateSystem(normal, &Nt, &Nb);
//            return (Nt * x + normal * y + Nb * z);
//        }
//
//        // --- Fresnel (Schlick) ---
//        float schlickFresnel(float cosine, float ior) {
//            float r0 = (1.0f - ior) / (1.0f + ior);
//            r0 = r0 * r0;
//            return r0 + (1.0f - r0) * pow((1.0f - cosine), 5.0f);
//        }
//
//        int getBlock(__global int* world, int x, int y, int z) {
//            if(x < 0 || y < 0 || z < 0 || x >= WORLD_SIZE || y >= WORLD_SIZE || z >= WORLD_SIZE) return 0;
//            return world[x + y * WORLD_SIZE + z * WORLD_SIZE * WORLD_SIZE];
//        }
//
//        // --- Material Struct ---
//        typedef struct {
//            int texOffset;
//            float emission;
//            float roughness;
//            float ior;
//            float3 baseColor;
//        } MaterialInfo;
//
//        // Fetch from the 8-float stride buffer
//        MaterialInfo getMaterial(__global float* matBuffer, int matID) {
//            int base = matID * 8;
//            MaterialInfo info;
//            info.texOffset = (int)matBuffer[base + 0];
//            info.emission  = matBuffer[base + 1];
//            info.roughness = matBuffer[base + 2];
//            info.ior       = matBuffer[base + 3];
//            info.baseColor = (float3)(matBuffer[base + 4], matBuffer[base + 5], matBuffer[base + 6]);
//            return info;
//        }
//
//        // --- Texture Sampling ---
//        float4 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
//            // No texture -> White (Alpha 1.0)
//            if (baseOffset < 0) return (float4)(1.0f, 1.0f, 1.0f, 1.0f);
//
//            int tx = clamp((int)(u * 16.0f), 0, 15);
//            int ty = clamp((int)(v * 16.0f), 0, 15);
//            int idx = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
//            int argb = textures[idx];
//
//            float a = (float)((argb >> 24) & 0xFF) / 255.0f;
//            // Ghost pass threshold: if alpha very low, return 0 alpha to signal skip
//            if (a < 0.1f) return (float4)(1.0f, 1.0f, 1.0f, 0.0f);
//
//            float r = (float)((argb >> 16) & 0xFF) / 255.0f;
//            float g = (float)((argb >> 8) & 0xFF) / 255.0f;
//            float b = (float)(argb & 0xFF) / 255.0f;
//            return (float4)(r, g, b, a);
//        }
//
//        // --- Main Kernel ---
//        __kernel void pathTraceKernel(
//            __global int* worldData,
//            __global int* outPixels,
//            __global uint* seeds,
//            __global int* textures,
//            __global float* materials, // Changed to float pointer (stride 8)
//            const float3 camPos,
//            const float3 camDir,
//            const float3 camRight,
//            const float3 camUp,
//            const int width,
//            const int height,
//            const int originX,
//            const int originY,
//            const int originZ,
//            const int frameCount
//        ) {
//            int gid = get_global_id(0);
//            if (gid >= width * height) return;
//
//            int screenX = gid % width;
//            int screenY = gid / width;
//            uint seed = seeds[gid] + frameCount * 719393;
//            float3 finalColor = (float3)(0,0,0);
//
//            // Sun Direction (Static for now, could be passed as arg)
//            float3 sunDir = normalize((float3)(0.3f, 0.8f, 0.4f));
//
//            for (int s = 0; s < $SAMPLES_PER_PIXEL; s++) {
//                // Anti-aliasing jitter
//                float uOffset = randomFloat(&seed) - 0.5f;
//                float vOffset = randomFloat(&seed) - 0.5f;
//                float u = ((float)screenX + uOffset) / (float)width * 2.0f - 1.0f;
//                float v = ((float)screenY + vOffset) / (float)height * 2.0f - 1.0f;
//
//                float3 rayDir = normalize(camDir + camRight * u + camUp * v);
//                float3 rayPos = camPos;
//                float3 throughput = (float3)(1.0f, 1.0f, 1.0f);
//                float3 accumulated = (float3)(0.0f, 0.0f, 0.0f);
//
//                for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
//                    // --- DDA Setup ---
//                    int mapX = (int)floor(rayPos.x); int mapY = (int)floor(rayPos.y); int mapZ = (int)floor(rayPos.z);
//                    float3 deltaDist = (float3)(fabs(1.0f/rayDir.x), fabs(1.0f/rayDir.y), fabs(1.0f/rayDir.z));
//                    int3 step; float3 sideDist;
//
//                    if (rayDir.x < 0) { step.x = -1; sideDist.x = (rayPos.x - mapX) * deltaDist.x; }
//                    else              { step.x = 1;  sideDist.x = (mapX + 1.0f - rayPos.x) * deltaDist.x; }
//                    if (rayDir.y < 0) { step.y = -1; sideDist.y = (rayPos.y - mapY) * deltaDist.y; }
//                    else              { step.y = 1;  sideDist.y = (mapY + 1.0f - rayPos.y) * deltaDist.y; }
//                    if (rayDir.z < 0) { step.z = -1; sideDist.z = (rayPos.z - mapZ) * deltaDist.z; }
//                    else              { step.z = 1;  sideDist.z = (mapZ + 1.0f - rayPos.z) * deltaDist.z; }
//
//                    int hit = 0; int side = 0; int blockID = 0; float dist = 0.0f;
//
//                    while (hit == 0 && dist < 120.0f) {
//                        if (sideDist.x < sideDist.y) {
//                            if (sideDist.x < sideDist.z) { dist = sideDist.x; sideDist.x += deltaDist.x; mapX += step.x; side = 0; }
//                            else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2; }
//                        } else {
//                            if (sideDist.y < sideDist.z) { dist = sideDist.y; sideDist.y += deltaDist.y; mapY += step.y; side = 1; }
//                            else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2; }
//                        }
//                        blockID = getBlock(worldData, mapX - originX, mapY - originY, mapZ - originZ);
//                        if (blockID > 0) hit = 1;
//                    }
//
//                    if (hit) {
//                        MaterialInfo mat = getMaterial(materials, blockID);
//
//                        if (mat.emission > 0.0f) {
//                            accumulated += throughput * mat.baseColor * mat.emission;
//                            break;
//                        }
//
//                        // Geometry
//                        float3 normal = (float3)(0,0,0); float2 uv = (float2)(0,0); int faceIndex = 0;
//                        float3 hitPos = rayPos + rayDir * dist;
//                        float3 localHit = hitPos - (float3)(mapX, mapY, mapZ);
//
//                        if (side == 0) {
//                             normal.x = -step.x;
//                             if (step.x > 0) { faceIndex = 0; uv = (float2)(localHit.z, 1.0f - localHit.y); }
//                             else            { faceIndex = 1; uv = (float2)(1.0f - localHit.z, 1.0f - localHit.y); }
//                        } else if (side == 1) {
//                             normal.y = -step.y;
//                             if (step.y > 0) { faceIndex = 2; uv = (float2)(localHit.x, localHit.z); }
//                             else            { faceIndex = 3; uv = (float2)(localHit.x, 1.0f - localHit.z); }
//                        } else {
//                             normal.z = -step.z;
//                             if (step.z > 0) { faceIndex = 4; uv = (float2)(1.0f - localHit.x, 1.0f - localHit.y); }
//                             else            { faceIndex = 5; uv = (float2)(localHit.x, 1.0f - localHit.y); }
//                        }
//
//                        float4 texData = sampleTexture(textures, mat.texOffset, faceIndex, uv.x, uv.y);
//
//                        // Foliage Transparency Skip
//                        if (texData.w < 0.1f) {
//                            rayPos = hitPos + rayDir * EPSILON;
//                            bounce--; // Don't count as bounce
//                            continue;
//                        }
//
//                        // Color Mixing
//                        float3 albedo = texData.xyz * mat.baseColor;
//                        throughput *= albedo;
//                        rayPos = hitPos + normal * EPSILON;
//
//                        // Russian Roulette
//                        if (bounce > 2) {
//                            float p = max(throughput.x, max(throughput.y, throughput.z));
//                            if (randomFloat(&seed) > p) break;
//                            throughput *= 1.0f / p;
//                        }
//
//                        // Next Ray Direction
//                        float3 diffuseDir = sampleCosineHemisphere(normal, &seed);
//                        float3 specularDir = rayDir - 2.0f * dot(rayDir, normal) * normal; // reflect
//
//                        // Fresnel & Roughness
//                        float cosTheta = fabs(dot(rayDir, normal));
//                        float fresnel = schlickFresnel(cosTheta, mat.ior);
//
//                        // Mix Diffuse/Specular based on Fresnel and Roughness
//                        // Low roughness + High Fresnel = Specular
//                        bool isSpecular = false;
//                        if (randomFloat(&seed) < fresnel) isSpecular = true;
//
//                        // Apply roughness jitter to specular
//                        if (isSpecular || mat.roughness < 0.9f) {
//                             // Simple roughness model
//                             float3 fuzz = (float3)(randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f);
//                             specularDir = normalize(specularDir + fuzz * mat.roughness);
//                             if (dot(specularDir, normal) < 0) specularDir = diffuseDir; // Prevent light leak
//
//                             // If roughness is high, probability of specular drops
//                             if (randomFloat(&seed) > mat.roughness) rayDir = specularDir;
//                             else rayDir = diffuseDir;
//                        } else {
//                             rayDir = diffuseDir;
//                        }
//
//                    } else {
//                        // Sky & Sun
//                        float t = 0.5f * (rayDir.y + 1.0f);
//                        float3 skyColor = (1.0f - t) * (float3)(0.6f, 0.7f, 0.9f) + t * (float3)(0.1f, 0.3f, 0.7f);
//
//                        // Sun Disc
//                        float sunDot = dot(rayDir, sunDir);
//                        if (sunDot > 0.995f) {
//                             skyColor = (float3)(15.0f, 13.0f, 10.0f); // Bright Sun
//                        }
//
//                        accumulated += throughput * skyColor;
//                        break;
//                    }
//                }
//                finalColor += accumulated;
//            }
//
//            finalColor /= (float)$SAMPLES_PER_PIXEL;
//
//            // Exposure & Tone Mapping
//            finalColor *= 0.7f; // Exposure
//            finalColor = ACESFilm(finalColor);
//            // Inverse Gamma (sRGB conversion approx)
//            finalColor = pow(finalColor, (float3)(1.0f/2.2f));
//
//            int r = clamp((int)(finalColor.x * 255.0f), 0, 255);
//            int g = clamp((int)(finalColor.y * 255.0f), 0, 255);
//            int b = clamp((int)(finalColor.z * 255.0f), 0, 255);
//
//            outPixels[gid] = (255 << 24) | (r << 16) | (g << 8) | b;
//        }
//    """.trimIndent()
//    private val kernelSource = """
//    #define MAX_BOUNCES $MAX_BOUNCES
//    #define WORLD_SIZE $WORLD_SIZE
//    #define PI 3.14159265359f
//    #define EPSILON 0.005f
//    #define MAX_TRANSPARENCY_LAYERS 30
//
//    // --- Volumetric Settings ---
//    #define FOG_DENSITY 0.0f
//    #define FOG_ANISOTROPY 0.7f
//
//    // --- Utilities ---
//    uint hash(uint x) {
//        x += (x << 10u); x ^= (x >> 6u); x += (x << 3u);
//        x ^= (x >> 11u); x += (x << 15u); return x;
//    }
//    float randomFloat(uint* state) {
//        *state = hash(*state);
//        return (float)(*state) / (float)(0xFFFFFFFFu);
//    }
//
//    float3 toLinear(float3 c) {
//        return pow(c, (float3)(2.2f));
//    }
//
//    float3 toGamma(float3 c) {
//        return pow(c, (float3)(1.0f / 2.2f));
//    }
//
//    float3 ACESFilm(float3 x) {
//        float a = 2.51f; float b = 0.03f; float c = 2.43f; float d = 0.59f; float e = 0.14f;
//        return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0f, 1.0f);
//    }
//
//    void createCoordinateSystem(float3 N, float3* Nt, float3* Nb) {
//        if (fabs(N.x) > fabs(N.y)) *Nt = (float3)(N.z, 0, -N.x) / sqrt(N.x * N.x + N.z * N.z);
//        else *Nt = (float3)(0, -N.z, N.y) / sqrt(N.y * N.y + N.z * N.z);
//        *Nb = cross(N, *Nt);
//    }
//
//    float3 sampleCosineHemisphere(float3 normal, uint* seed) {
//        float r1 = randomFloat(seed);
//        float r2 = randomFloat(seed);
//        float sinTheta = sqrt(1.0f - r1);
//        float phi = 2.0f * PI * r2;
//        float x = sinTheta * cos(phi);
//        float z = sinTheta * sin(phi);
//        float y = sqrt(r1);
//        float3 Nt, Nb;
//        createCoordinateSystem(normal, &Nt, &Nb);
//        return (Nt * x + normal * y + Nb * z);
//    }
//
//    // --- Henyey-Greenstein Phase Function ---
//    float3 sampleHenyeyGreenstein(float3 incomingDir, float g, uint* seed) {
//        float r1 = randomFloat(seed);
//        float r2 = randomFloat(seed);
//        float phi = 2.0f * PI * r1;
//        float cosTheta;
//        if (fabs(g) < 0.001f) {
//            cosTheta = 1.0f - 2.0f * r2;
//        } else {
//            float sqrTerm = (1.0f - g * g) / (1.0f - g + 2.0f * g * r2);
//            cosTheta = (1.0f + g * g - sqrTerm * sqrTerm) / (2.0f * g);
//        }
//        float sinTheta = sqrt(max(0.0f, 1.0f - cosTheta * cosTheta));
//        float3 Nt, Nb;
//        createCoordinateSystem(incomingDir, &Nt, &Nb);
//        return Nt * (sinTheta * cos(phi)) + Nb * (sinTheta * sin(phi)) + incomingDir * cosTheta;
//    }
//
//    float phaseHG(float cosTheta, float g) {
//        float denom = 1.0f + g*g - 2.0f*g*cosTheta;
//        return (1.0f - g*g) / (4.0f * PI * pow(denom, 1.5f));
//    }
//
//    // --- Fresnel (Schlick) ---
//    float schlickFresnel(float cosine, float ior) {
//        float r0 = (1.0f - ior) / (1.0f + ior);
//        r0 = r0 * r0;
//        return r0 + (1.0f - r0) * pow((1.0f - cosine), 5.0f);
//    }
//
//    int getBlock(__global int* world, int x, int y, int z) {
//        if(x < 0 || y < 0 || z < 0 || x >= WORLD_SIZE || y >= WORLD_SIZE || z >= WORLD_SIZE) return 0;
//        return world[x + y * WORLD_SIZE + z * WORLD_SIZE * WORLD_SIZE];
//    }
//
//    // --- Shadow Ray (DDA) ---
//    float traceShadow(__global int* world, float3 ro, float3 rd, float maxDist, int originX, int originY, int originZ) {
//        int mapX = (int)floor(ro.x); int mapY = (int)floor(ro.y); int mapZ = (int)floor(ro.z);
//
//        float3 deltaDist;
//        deltaDist.x = fabs(1.0f / (fabs(rd.x) < 1e-5f ? 1e-5f : rd.x));
//        deltaDist.y = fabs(1.0f / (fabs(rd.y) < 1e-5f ? 1e-5f : rd.y));
//        deltaDist.z = fabs(1.0f / (fabs(rd.z) < 1e-5f ? 1e-5f : rd.z));
//
//        int3 step; float3 sideDist;
//
//        if (rd.x < 0) { step.x = -1; sideDist.x = (ro.x - mapX) * deltaDist.x; }
//        else          { step.x = 1;  sideDist.x = (mapX + 1.0f - ro.x) * deltaDist.x; }
//        if (rd.y < 0) { step.y = -1; sideDist.y = (ro.y - mapY) * deltaDist.y; }
//        else          { step.y = 1;  sideDist.y = (mapY + 1.0f - ro.y) * deltaDist.y; }
//        if (rd.z < 0) { step.z = -1; sideDist.z = (ro.z - mapZ) * deltaDist.z; }
//        else          { step.z = 1;  sideDist.z = (mapZ + 1.0f - ro.z) * deltaDist.z; }
//
//        float dist = 0.0f;
//        while (dist < maxDist) {
//            if (sideDist.x < sideDist.y) {
//                if (sideDist.x < sideDist.z) { dist = sideDist.x; sideDist.x += deltaDist.x; mapX += step.x; }
//                else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; }
//            } else {
//                if (sideDist.y < sideDist.z) { dist = sideDist.y; sideDist.y += deltaDist.y; mapY += step.y; }
//                else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; }
//            }
//            int bid = getBlock(world, mapX - originX, mapY - originY, mapZ - originZ);
//            if (bid > 0) return 0.0f;
//        }
//        return 1.0f;
//    }
//
//    // --- Structs & Material ---
//    typedef struct {
//        int texOffset;
//        float emission;
//        float roughness;
//        float ior;
//        float3 baseColor;
//    } MaterialInfo;
//
//    MaterialInfo getMaterial(__global float* matBuffer, int matID) {
//        int base = matID * 8;
//        MaterialInfo info;
//        info.texOffset = (int)matBuffer[base + 0];
//        info.emission  = matBuffer[base + 1];
//        info.roughness = matBuffer[base + 2];
//        info.ior       = matBuffer[base + 3];
//        info.baseColor = (float3)(matBuffer[base + 4], matBuffer[base + 5], matBuffer[base + 6]);
//        return info;
//    }
//
//    float4 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
//        if (baseOffset < 0) return (float4)(1.0f, 1.0f, 1.0f, 1.0f);
//        int tx = clamp((int)(u * 16.0f), 0, 15);
//        int ty = clamp((int)(v * 16.0f), 0, 15);
//        int idx = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
//        int argb = textures[idx];
//        float a = (float)((argb >> 24) & 0xFF) / 255.0f;
//        float r = (float)((argb >> 16) & 0xFF) / 255.0f;
//        float g = (float)((argb >> 8) & 0xFF) / 255.0f;
//        float b = (float)(argb & 0xFF) / 255.0f;
//        return (float4)(r, g, b, a);
//    }
//
//    // --- Main Kernel ---
//    __kernel void pathTraceKernel(
//        __global int* worldData,
//        __global int* outPixels,
//        __global uint* seeds,
//        __global int* textures,
//        __global float* materials,
//        const float3 camPos,
//        const float3 camDir,
//        const float3 camRight,
//        const float3 camUp,
//        const int width,
//        const int height,
//        const int originX,
//        const int originY,
//        const int originZ,
//        const int frameCount
//    ) {
//        int gid = get_global_id(0);
//        if (gid >= width * height) return;
//
//        int screenX = gid % width;
//        int screenY = gid / width;
//        uint seed = seeds[gid] ^ hash(frameCount * 719393);
//
//        float3 finalColor = (float3)(0,0,0);
//
//        float3 sunDir = normalize((float3)(0.3f, 0.8f, 0.4f));
//        // [修复] 大幅提升光源强度。在线性空间和ACES下，15.0太暗了。
//        float3 sunColor = (float3)(60.0f, 55.0f, 45.0f);
//        // [修复] 提升天空光强度，让阴影不至于死黑
//        float skyIntensity = 10.0f;
//
//        for (int s = 0; s < $SAMPLES_PER_PIXEL; s++) {
//            float uOffset = randomFloat(&seed) - 0.5f;
//            float vOffset = randomFloat(&seed) - 0.5f;
//            float u = ((float)screenX + uOffset) / (float)width * 2.0f - 1.0f;
//            float v = ((float)screenY + vOffset) / (float)height * 2.0f - 1.0f;
//
//            float3 rayDir = normalize(camDir + camRight * u + camUp * v);
//            float3 rayPos = camPos;
//            float3 throughput = (float3)(1.0f, 1.0f, 1.0f);
//            float3 accumulated = (float3)(0.0f, 0.0f, 0.0f);
//
//            int transparencyIter = 0;
//
//            for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
//
//                // 1. Geometry Intersection (DDA)
//                int mapX = (int)floor(rayPos.x); int mapY = (int)floor(rayPos.y); int mapZ = (int)floor(rayPos.z);
//
//                float3 deltaDist;
//                deltaDist.x = fabs(1.0f / (fabs(rayDir.x) < 1e-5f ? 1e-5f : rayDir.x));
//                deltaDist.y = fabs(1.0f / (fabs(rayDir.y) < 1e-5f ? 1e-5f : rayDir.y));
//                deltaDist.z = fabs(1.0f / (fabs(rayDir.z) < 1e-5f ? 1e-5f : rayDir.z));
//
//                int3 step; float3 sideDist;
//
//                if (rayDir.x < 0) { step.x = -1; sideDist.x = (rayPos.x - mapX) * deltaDist.x; }
//                else              { step.x = 1;  sideDist.x = (mapX + 1.0f - rayPos.x) * deltaDist.x; }
//                if (rayDir.y < 0) { step.y = -1; sideDist.y = (rayPos.y - mapY) * deltaDist.y; }
//                else              { step.y = 1;  sideDist.y = (mapY + 1.0f - rayPos.y) * deltaDist.y; }
//                if (rayDir.z < 0) { step.z = -1; sideDist.z = (rayPos.z - mapZ) * deltaDist.z; }
//                else              { step.z = 1;  sideDist.z = (mapZ + 1.0f - rayPos.z) * deltaDist.z; }
//
//                int hit = 0; int side = 0; int blockID = 0; float geoDist = 0.0f;
//                float maxScanDist = 80.0f;
//
//                while (hit == 0 && geoDist < maxScanDist) {
//                    if (sideDist.x < sideDist.y) {
//                        if (sideDist.x < sideDist.z) { geoDist = sideDist.x; sideDist.x += deltaDist.x; mapX += step.x; side = 0; }
//                        else                         { geoDist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2; }
//                    } else {
//                        if (sideDist.y < sideDist.z) { geoDist = sideDist.y; sideDist.y += deltaDist.y; mapY += step.y; side = 1; }
//                        else                         { geoDist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2; }
//                    }
//                    blockID = getBlock(worldData, mapX - originX, mapY - originY, mapZ - originZ);
//                    if (blockID > 0) hit = 1;
//                }
//                if (!hit) geoDist = 10000.0f;
//
//                // 2. Volumetric Interaction
//                float distToMedium = -log(randomFloat(&seed)) / FOG_DENSITY;
//
//                if (distToMedium < geoDist) {
//                    rayPos += rayDir * distToMedium;
//
//                    // Volumetric NEE (Sample Sun from fog)
//                    float shadowVis = traceShadow(worldData, rayPos + sunDir * 0.05f, sunDir, 40.0f, originX, originY, originZ);
//                    if (shadowVis > 0.0f) {
//                        float cosTheta = dot(rayDir, sunDir);
//                        float phase = phaseHG(cosTheta, FOG_ANISOTROPY);
//                        accumulated += throughput * sunColor * phase * shadowVis;
//                    }
//
//                    throughput *= 0.99f;
//                    rayDir = sampleHenyeyGreenstein(rayDir, FOG_ANISOTROPY, &seed);
//
//                    if (bounce > 3) {
//                        if (randomFloat(&seed) > 0.8f) break;
//                        throughput /= 0.8f;
//                    }
//                    continue;
//                }
//
//                // 3. Surface Event
//                if (hit) {
//                    MaterialInfo mat = getMaterial(materials, blockID);
//                    if (mat.emission > 0.0f) {
//                        accumulated += throughput * mat.baseColor * mat.emission * 10.0f; // 增强自发光
//                        break;
//                    }
//
//                    float3 normal = (float3)(0,0,0); float2 uv = (float2)(0,0); int faceIndex = 0;
//                    float3 hitPos = rayPos + rayDir * geoDist;
//                    float3 localHit = hitPos - (float3)(mapX, mapY, mapZ);
//
//                    if (side == 0) { normal.x = -step.x; uv = (step.x > 0) ? (float2)(localHit.z, 1.0f-localHit.y) : (float2)(1.0f-localHit.z, 1.0f-localHit.y); faceIndex = (step.x > 0) ? 0 : 1; }
//                    else if (side == 1) { normal.y = -step.y; uv = (step.y > 0) ? (float2)(localHit.x, localHit.z) : (float2)(localHit.x, 1.0f-localHit.z); faceIndex = (step.y > 0) ? 2 : 3; }
//                    else { normal.z = -step.z; uv = (step.z > 0) ? (float2)(1.0f-localHit.x, 1.0f-localHit.y) : (float2)(localHit.x, 1.0f-localHit.y); faceIndex = (step.z > 0) ? 4 : 5; }
//
//                    float4 texData = sampleTexture(textures, mat.texOffset, faceIndex, uv.x, uv.y);
//
//                    if (texData.w < 0.1f) {
//                        rayPos = hitPos + rayDir * EPSILON;
//                        transparencyIter++;
//                        if (transparencyIter < MAX_TRANSPARENCY_LAYERS) {
//                            bounce--;
//                            continue;
//                        }
//                    }
//
//                    float3 albedo = toLinear(texData.xyz) * mat.baseColor;
//
//                    // --- Surface Next Event Estimation (NEE) ---
//                    float3 surfHitPos = hitPos + normal * EPSILON; // 使用新的 EPSILON
//                    float shadowVis = traceShadow(worldData, surfHitPos, sunDir, 40.0f, originX, originY, originZ);
//
//                    if (shadowVis > 0.0f) {
//                        float nDotL = max(0.0f, dot(normal, sunDir));
//                        accumulated += throughput * albedo * sunColor * nDotL * shadowVis;
//                    }
//
//                    throughput *= albedo;
//                    rayPos = hitPos + normal * EPSILON;
//
//                    // Russian Roulette
//                    if (bounce > 2) {
//                        float p = max(throughput.x, max(throughput.y, throughput.z));
//                        if (randomFloat(&seed) > p) break;
//                        throughput *= 1.0f / p;
//                    }
//
//                    float3 diffuseDir = sampleCosineHemisphere(normal, &seed);
//                    float3 specularDir = rayDir - 2.0f * dot(rayDir, normal) * normal;
//                    float cosTheta = fabs(dot(rayDir, normal));
//                    float fresnel = schlickFresnel(cosTheta, mat.ior);
//
//                    bool isSpecular = (randomFloat(&seed) < fresnel);
//                    if (isSpecular || mat.roughness < 0.9f) {
//                         float3 fuzz = (float3)(randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f);
//                         float3 roughSpec = normalize(specularDir + fuzz * mat.roughness);
//                         if (dot(roughSpec, normal) < 0.0f) roughSpec = diffuseDir;
//                         rayDir = (randomFloat(&seed) > mat.roughness) ? roughSpec : diffuseDir;
//                    } else {
//                         rayDir = diffuseDir;
//                    }
//                } else {
//                    // Sky Miss
//                    float t = 0.5f * (rayDir.y + 1.0f);
//                    float3 baseSky = (1.0f - t) * (float3)(0.6f, 0.7f, 0.9f) + t * (float3)(0.1f, 0.3f, 0.7f);
//                    float3 skyColor = baseSky * skyIntensity;
//
//                    if (dot(rayDir, sunDir) > 0.995f) skyColor = sunColor;
//
//                    accumulated += throughput * skyColor;
//                    break;
//                }
//            }
//            finalColor += accumulated;
//        }
//
//        finalColor /= (float)$SAMPLES_PER_PIXEL;
//
////        finalColor *= 1.5f;
//
//        finalColor = ACESFilm(finalColor);
//
//        finalColor = toGamma(finalColor);
//
//        int r = clamp((int)(finalColor.x * 255.0f), 0, 255);
//        int g = clamp((int)(finalColor.y * 255.0f), 0, 255);
//        int b = clamp((int)(finalColor.z * 255.0f), 0, 255);
//        outPixels[gid] = (255 << 24) | (r << 16) | (g << 8) | b;
//    }
//""".trimIndent()



package com.methyleneblue.camera.obj

import com.methyleneblue.camera.obj.raytrace.RayTraceMaterial
import com.methyleneblue.camera.raytracepack.bvh.BVHTree
import com.methyleneblue.camera.raytracepack.bvh.FlatBVHNode
import com.methyleneblue.camera.raytracepack.bvh.HitResult
import com.methyleneblue.camera.texture.TextureManager
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.jocl.*
import org.jocl.CL.*
import org.joml.Vector3f
import java.awt.image.BufferedImage
import java.util.*
import kotlin.math.tan

class CLRTCamera(
    location: Location,
    size: Pair<Int, Int>,
    fov: Double,
    distance: Double,
    progressBar: BossBar?,
    bufferedImage: BufferedImage,
    depthImage: Array<FloatArray>
) : BVHCamera(
    location,
    size,
    fov,
    distance,
    progressBar,
    bufferedImage,
    depthImage,
) {

    private val SAMPLES_PER_PIXEL = 1
    private val MAX_BOUNCES = 6
    private val WORLD_SIZE = 64
    private val MAX_LIGHTS = 1024

    private var isInitialized = false
    private var context: cl_context? = null
    private var queue: cl_command_queue? = null
    private var program: cl_program? = null
    private var kernel: cl_kernel? = null

    private var memWorld: cl_mem? = null
    private var memPixelOut: cl_mem? = null
    private var memSeeds: cl_mem? = null
    private var memMaterials: cl_mem? = null
    private var memTextures: cl_mem? = null
    private var memLights: cl_mem? = null

    private var currentWidth = size.first
    private var currentHeight = size.second

    private var pixelBuffer = IntArray(currentWidth * currentHeight)
    private val worldDataHost = IntArray(WORLD_SIZE * WORLD_SIZE * WORLD_SIZE)
    private var seedsHost = IntArray(currentWidth * currentHeight)
    private var cpuAccumulator: FloatArray = FloatArray(0)

    private val lightHostBuffer = IntArray(MAX_LIGHTS * 3)
    private var activeLightCount = 0

    private var materialDataHost: FloatArray = FloatArray(0)
    private var lastWorldCenter: Location? = null
    private var totalAccumulatedSamples = 0L

    private var lastTextureArrayHash = 0

    private val kernelSource = """
    #define MAX_BOUNCES $MAX_BOUNCES
    #define WORLD_SIZE $WORLD_SIZE
    #define PI 3.14159265359f
    #define EPSILON 0.005f
    #define CLAMP_VALUE 500.0f 

    typedef struct {
        int texOffset;
        float emission;
        float roughness;
        float ior;
        float3 baseColor;
    } MaterialInfo;

    uint hash(uint x) {
        x += (x << 10u); x ^= (x >> 6u); x += (x << 3u);
        x ^= (x >> 11u); x += (x << 15u); return x;
    }
    float randomFloat(uint* state) {
        *state = hash(*state);
        return (float)(*state) / (float)(0xFFFFFFFFu);
    }

    float3 ACESFilm(float3 x) {
        float a = 2.51f; float b = 0.03f; float c = 2.43f; float d = 0.59f; float e = 0.14f;
        return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0f, 1.0f);
    }

    void createCoordinateSystem(float3 N, float3* Nt, float3* Nb) {
        if (fabs(N.x) > fabs(N.y)) *Nt = (float3)(N.z, 0, -N.x) / sqrt(N.x * N.x + N.z * N.z);
        else *Nt = (float3)(0, -N.z, N.y) / sqrt(N.y * N.y + N.z * N.z);
        *Nb = cross(N, *Nt);
    }
    
    float3 sampleCosineHemisphere(float3 normal, uint* seed) {
        float r1 = randomFloat(seed);
        float r2 = randomFloat(seed);
        float sinTheta = sqrt(1.0f - r1);
        float phi = 2.0f * PI * r2;
        float x = sinTheta * cos(phi);
        float z = sinTheta * sin(phi);
        float y = sqrt(r1);
        float3 Nt, Nb;
        createCoordinateSystem(normal, &Nt, &Nb);
        return (Nt * x + normal * y + Nb * z);
    }

    float schlickFresnel(float cosine, float ior) {
        float r0 = (1.0f - ior) / (1.0f + ior);
        r0 = r0 * r0;
        float t = 1.0f - cosine;
        return r0 + (1.0f - r0) * (t * t * t * t * t);
    }

    int getBlock(__global int* world, int x, int y, int z) {
        if(x < 0 || y < 0 || z < 0 || x >= WORLD_SIZE || y >= WORLD_SIZE || z >= WORLD_SIZE) return 0;
        return world[x + y * WORLD_SIZE + z * WORLD_SIZE * WORLD_SIZE];
    }

    MaterialInfo getMaterial(__global float* matBuffer, int matID) {
        int base = matID * 8;
        MaterialInfo info;
        info.texOffset = (int)matBuffer[base + 0];
        info.emission  = matBuffer[base + 1];
        info.roughness = matBuffer[base + 2];
        info.ior       = matBuffer[base + 3];
        info.baseColor = (float3)(matBuffer[base + 4], matBuffer[base + 5], matBuffer[base + 6]);
        return info;
    }

    bool inShadow(
        __global int* world, 
        __global float* materials,
        float3 startPos, 
        float3 lightPos, 
        float maxDist, 
        int originX, int originY, int originZ
    ) {
        float3 dir = normalize(lightPos - startPos);
        int mapX = (int)floor(startPos.x); 
        int mapY = (int)floor(startPos.y); 
        int mapZ = (int)floor(startPos.z);
        float3 deltaDist = (float3)(fabs(1.0f/dir.x), fabs(1.0f/dir.y), fabs(1.0f/dir.z));
        int3 step; float3 sideDist;

        if (dir.x < 0) { step.x = -1; sideDist.x = (startPos.x - mapX) * deltaDist.x; }
        else           { step.x = 1;  sideDist.x = (mapX + 1.0f - startPos.x) * deltaDist.x; }
        if (dir.y < 0) { step.y = -1; sideDist.y = (startPos.y - mapY) * deltaDist.y; }
        else           { step.y = 1;  sideDist.y = (mapY + 1.0f - startPos.y) * deltaDist.y; }
        if (dir.z < 0) { step.z = -1; sideDist.z = (startPos.z - mapZ) * deltaDist.z; }
        else           { step.z = 1;  sideDist.z = (mapZ + 1.0f - startPos.z) * deltaDist.z; }

        float dist = 0.0f;
        while (dist < maxDist) {
             if (sideDist.x < sideDist.y) {
                 if (sideDist.x < sideDist.z) { dist = sideDist.x; sideDist.x += deltaDist.x; mapX += step.x; }
                 else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; }
             } else {
                 if (sideDist.y < sideDist.z) { dist = sideDist.y; sideDist.y += deltaDist.y; mapY += step.y; }
                 else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; }
             }
             if (dist > maxDist - 0.05f) return false;

             int id = getBlock(world, mapX - originX, mapY - originY, mapZ - originZ);
             if (id > 0) {
                 MaterialInfo m = getMaterial(materials, id);
                 if (m.emission > 0.0f) return false;
                 if (m.ior > 1.0f && m.roughness < 0.2f) continue;
                 return true; 
             }
        }
        return false;
    }
    
    float4 sampleTexture(__global int* textures, int baseOffset, int faceIndex, float u, float v) {
        if (baseOffset < 0) return (float4)(1.0f, 1.0f, 1.0f, 1.0f);
        int tx = clamp((int)(u * 16.0f), 0, 15);
        int ty = clamp((int)(v * 16.0f), 0, 15);
        int idx = baseOffset + (faceIndex * 256) + (ty * 16 + tx);
        int argb = textures[idx];
        float a = (float)((argb >> 24) & 0xFF) / 255.0f;
        if (a < 0.1f) return (float4)(0.0f, 0.0f, 0.0f, 0.0f);
        float r = (float)((argb >> 16) & 0xFF) / 255.0f;
        float g = (float)((argb >> 8) & 0xFF) / 255.0f;
        float b = (float)(argb & 0xFF) / 255.0f;
        return (float4)(r, g, b, a);
    }

    __kernel void pathTraceKernel(
        __global int* worldData,
        __global int* outPixels,
        __global uint* seeds,
        __global int* textures,
        __global float* materials,
        __global int* lightBuffer, 
        const int lightCount,      
        const float3 camPos,
        const float3 camDir,
        const float3 camRight,
        const float3 camUp,
        const int width,
        const int height,
        const int originX,
        const int originY,
        const int originZ,
        const int frameCount
    ) {
        int gid = get_global_id(0);
        if (gid >= width * height) return;

        int screenX = gid % width;
        int screenY = gid / width;
        uint seed = seeds[gid] + frameCount * 719393;
        float3 finalColor = (float3)(0,0,0);

        for (int s = 0; s < $SAMPLES_PER_PIXEL; s++) {
            float uOffset = randomFloat(&seed) - 0.5f;
            float vOffset = randomFloat(&seed) - 0.5f;
            float u = ((float)screenX + uOffset) / (float)width * 2.0f - 1.0f;
            float v = ((float)screenY + vOffset) / (float)height * 2.0f - 1.0f;

            float3 rayDir = normalize(camDir + camRight * u + camUp * v);
            float3 rayPos = camPos;
            float3 throughput = (float3)(1.0f, 1.0f, 1.0f);
            float3 accumulated = (float3)(0.0f, 0.0f, 0.0f);

            for (int bounce = 0; bounce < MAX_BOUNCES; bounce++) {
                int mapX = (int)floor(rayPos.x); int mapY = (int)floor(rayPos.y); int mapZ = (int)floor(rayPos.z);
                float3 deltaDist = (float3)(fabs(1.0f/rayDir.x), fabs(1.0f/rayDir.y), fabs(1.0f/rayDir.z));
                int3 step; float3 sideDist;

                if (rayDir.x < 0) { step.x = -1; sideDist.x = (rayPos.x - mapX) * deltaDist.x; }
                else              { step.x = 1;  sideDist.x = (mapX + 1.0f - rayPos.x) * deltaDist.x; }
                if (rayDir.y < 0) { step.y = -1; sideDist.y = (rayPos.y - mapY) * deltaDist.y; }
                else              { step.y = 1;  sideDist.y = (mapY + 1.0f - rayPos.y) * deltaDist.y; }
                if (rayDir.z < 0) { step.z = -1; sideDist.z = (rayPos.z - mapZ) * deltaDist.z; }
                else              { step.z = 1;  sideDist.z = (mapZ + 1.0f - rayPos.z) * deltaDist.z; }

                int hit = 0; int side = 0; int blockID = 0; float dist = 0.0f;
                
                while (hit == 0 && dist < 120.0f) {
                    if (sideDist.x < sideDist.y) {
                        if (sideDist.x < sideDist.z) { dist = sideDist.x; sideDist.x += deltaDist.x; mapX += step.x; side = 0; }
                        else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2; }
                    } else {
                        if (sideDist.y < sideDist.z) { dist = sideDist.y; sideDist.y += deltaDist.y; mapY += step.y; side = 1; }
                        else                         { dist = sideDist.z; sideDist.z += deltaDist.z; mapZ += step.z; side = 2; }
                    }
                    blockID = getBlock(worldData, mapX - originX, mapY - originY, mapZ - originZ);
                    if (blockID > 0) hit = 1;
                }

                if (hit) {
                    MaterialInfo mat = getMaterial(materials, blockID);

                    if (mat.emission > 0.0f) {

                        float intensity = mat.emission * 80.0f; 
                        accumulated += throughput * mat.baseColor * intensity;
                        break; 
                    }

                    float3 normal = (float3)(0,0,0); float2 uv = (float2)(0,0); int faceIndex = 0;
                    float3 hitPos = rayPos + rayDir * dist;
                    float3 localHit = hitPos - (float3)(mapX, mapY, mapZ);
                    
                    if (side == 0) { normal.x = -step.x; faceIndex = (step.x > 0) ? 0 : 1; uv = (step.x > 0) ? (float2)(localHit.z, 1.0f-localHit.y) : (float2)(1.0f-localHit.z, 1.0f-localHit.y); }
                    else if (side == 1) { normal.y = -step.y; faceIndex = (step.y > 0) ? 2 : 3; uv = (step.y > 0) ? (float2)(localHit.x, localHit.z) : (float2)(localHit.x, 1.0f-localHit.z); }
                    else { normal.z = -step.z; faceIndex = (step.z > 0) ? 4 : 5; uv = (step.z > 0) ? (float2)(1.0f-localHit.x, 1.0f-localHit.y) : (float2)(localHit.x, 1.0f-localHit.y); }

                    float4 texData = sampleTexture(textures, mat.texOffset, faceIndex, uv.x, uv.y);
                    if (texData.w < 0.1f) { 
                        rayPos = hitPos + rayDir * EPSILON; 
                        continue; 
                    }

                    float3 albedo = texData.xyz * mat.baseColor * 1.2f;
                    
                    if (lightCount > 0 && mat.roughness > 0.3f) {
                        int lightIdx = (int)(randomFloat(&seed) * (float)(lightCount - 0.001f));
                        int base = lightIdx * 3;
                        int lx = lightBuffer[base];
                        int ly = lightBuffer[base+1];
                        int lz = lightBuffer[base+2];
                        
                        float3 lightBlockPos = (float3)(lx + 0.5f, ly + 0.5f, lz + 0.5f);
                        lightBlockPos += (float3)(randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f) * 0.9f;

                        float3 toLight = lightBlockPos - hitPos;
                        float distSq = dot(toLight, toLight); // 距离平方
                        float distToLight = sqrt(distSq);
                        float3 L = normalize(toLight);
                        float NdotL = dot(normal, L);
                        
                        if (NdotL > 0.0f) {
                            float3 shadowOrigin = hitPos + normal * EPSILON;
                            bool visible = !inShadow(worldData, materials, shadowOrigin, lightBlockPos, distToLight - 0.05f, originX, originY, originZ);
                            
                            if (visible) {
                                int lightID = getBlock(worldData, lx - originX, ly - originY, lz - originZ);
                                MaterialInfo lMat = getMaterial(materials, lightID);
                                
                                float lightIntensity = lMat.emission * 200.0f; 

                                float weight = (float)lightCount / max(distSq, 1.0f); 
                                
                                float3 directLight = albedo * lightIntensity * NdotL * weight;
                                
                                // Clamp Fireflies
                                float maxC = max(directLight.x, max(directLight.y, directLight.z));
                                if (maxC > CLAMP_VALUE) directLight *= (CLAMP_VALUE / maxC);
                                
                                accumulated += throughput * directLight;
                            }
                        }
                    }

                    throughput *= albedo;
                    rayPos = hitPos + normal * EPSILON;
                    
                    // Russian Roulette
                    if (bounce > 2) {
                        float p = max(throughput.x, max(throughput.y, throughput.z));
                        if (randomFloat(&seed) > p) break;
                        throughput *= 1.0f / p;
                    }

                    // BSDF Sampling
                    float3 diffuseDir = sampleCosineHemisphere(normal, &seed);
                    float3 specularDir = rayDir - 2.0f * dot(rayDir, normal) * normal; 
                    float cosTheta = fabs(dot(rayDir, normal));
                    
                    float fresnel = schlickFresnel(cosTheta, mat.ior);

                    bool isSpecular = false;
                    if (randomFloat(&seed) < fresnel) isSpecular = true;

                    if (isSpecular || mat.roughness < 0.9f) {
                         float3 fuzz = (float3)(randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f, randomFloat(&seed)-0.5f);
                         specularDir = normalize(specularDir + fuzz * mat.roughness);
                         if (dot(specularDir, normal) < 0) specularDir = diffuseDir; 
                         if (randomFloat(&seed) > mat.roughness) rayDir = specularDir;
                         else rayDir = diffuseDir;
                    } else {
                         rayDir = diffuseDir;
                    }

                } else {
                    // Sky Light (Ambient)
                    float t = 0.5f * (rayDir.y + 1.0f);
                    float3 skyColor = ((1.0f - t) * (float3)(0.8f, 0.9f, 1.0f) + t * (float3)(0.2f, 0.5f, 1.0f));
                    accumulated += throughput * skyColor * 2.0f; 
                    break;
                }
            }
            finalColor += accumulated;
        }

        finalColor /= (float)$SAMPLES_PER_PIXEL;
         
        finalColor *= 6.0f; 
        
        finalColor = ACESFilm(finalColor);
        finalColor = pow(finalColor, (float3)(1.0f/2.2f));

        int r = clamp((int)(finalColor.x * 255.0f), 0, 255);
        int g = clamp((int)(finalColor.y * 255.0f), 0, 255);
        int b = clamp((int)(finalColor.z * 255.0f), 0, 255);

        outPixels[gid] = (255 << 24) | (r << 16) | (g << 8) | b;
    }
    """.trimIndent()

    // -------------------------------------------------------------------------
    // Host Logic
    // -------------------------------------------------------------------------

    override fun updateCamera(
        player: Player?,
        mixinTimes: Int,
        maxDepth: Float
    ): Pair<BufferedImage, Array<FloatArray>> {
        return this.updateCamera1(player, mixinTimes, maxDepth, true)
    }

    fun updateCamera1(
        player: Player?,
        mixinTimes: Int,
        maxDepth: Float,
        resetAccumulation: Boolean
    ): Pair<BufferedImage, Array<FloatArray>> {

        // 【修复】检测分辨率变化，防止崩端
        if (bufferedImage.width != currentWidth || bufferedImage.height != currentHeight) {
            cleanup() // 释放旧资源
            currentWidth = bufferedImage.width
            currentHeight = bufferedImage.height
            pixelBuffer = IntArray(currentWidth * currentHeight)
            seedsHost = IntArray(currentWidth * currentHeight)
            // 重新初始化会使用新的尺寸
        }

        if (!isInitialized) initOpenCL()

        val pixelCount = currentWidth * currentHeight
        if (cpuAccumulator.size != pixelCount * 3) {
            cpuAccumulator = FloatArray(pixelCount * 3)
            totalAccumulatedSamples = 0
        }
        if (resetAccumulation) {
            cpuAccumulator.fill(0f)
            totalAccumulatedSamples = 0
        }

        val renderLoc = player?.eyeLocation ?: this.location
        val dir = renderLoc.direction

        updateWorldDataIfNeeded(renderLoc)
        updateTexturesIfNeeded()

        val vFovRad = Math.toRadians(this.fov)
        val halfHeight = tan(vFovRad / 2.0)
        val halfWidth = halfHeight * (currentWidth.toDouble() / currentHeight.toDouble())
        val w = dir.clone().normalize()
        val up = Vector(0, 1, 0)
        val u = w.clone().crossProduct(up).normalize()
        val v = u.clone().crossProduct(w).normalize().multiply(-1)
        val camRight = u.multiply(halfWidth)
        val camUp = v.multiply(halfHeight)

        var a = 0
        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memWorld))
        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memPixelOut))
        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memSeeds))
        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memTextures))
        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memMaterials))
        clSetKernelArg(kernel, a++, Sizeof.cl_mem.toLong(), Pointer.to(memLights))
        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(activeLightCount)))

        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(renderLoc.x.toFloat(), renderLoc.y.toFloat(), renderLoc.z.toFloat())))
        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(w.x.toFloat(), w.y.toFloat(), w.z.toFloat())))
        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(camRight.x.toFloat(), camRight.y.toFloat(), camRight.z.toFloat())))
        clSetKernelArg(kernel, a++, Sizeof.cl_float3.toLong(), Pointer.to(floatArrayOf(camUp.x.toFloat(), camUp.y.toFloat(), camUp.z.toFloat())))

        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(currentWidth)))
        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(currentHeight)))

        val origin = lastWorldCenter ?: renderLoc
        val ox = origin.blockX - WORLD_SIZE / 2
        val oy = origin.blockY - WORLD_SIZE / 2
        val oz = origin.blockZ - WORLD_SIZE / 2
        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(ox)))
        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(oy)))
        clSetKernelArg(kernel, a++, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(oz)))

        val frameCountArgIndex = a

        val loops = if (mixinTimes < 1) 1 else mixinTimes
        val globalWorkSize = longArrayOf(pixelCount.toLong())

        for (i in 0 until loops) {
            val seedOffset = (System.currentTimeMillis() % 100000).toInt() + (i * 997) + totalAccumulatedSamples.toInt()
            clSetKernelArg(kernel, frameCountArgIndex, Sizeof.cl_int.toLong(), Pointer.to(intArrayOf(seedOffset)))
            clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null)

            clEnqueueReadBuffer(queue, memPixelOut, CL_TRUE, 0, (Sizeof.cl_int * pixelCount).toLong(), Pointer.to(pixelBuffer), 0, null, null)

            for (p in 0 until pixelCount) {
                val rgb = pixelBuffer[p]
                cpuAccumulator[p * 3 + 0] += ((rgb shr 16) and 0xFF).toFloat()
                cpuAccumulator[p * 3 + 1] += ((rgb shr 8) and 0xFF).toFloat()
                cpuAccumulator[p * 3 + 2] += (rgb and 0xFF).toFloat()
            }
        }
        totalAccumulatedSamples += loops
        val div = totalAccumulatedSamples.toFloat()

        for (p in 0 until pixelCount) {
            val idx = p * 3
            val r = (cpuAccumulator[idx] / div).toInt().coerceIn(0, 255)
            val g = (cpuAccumulator[idx + 1] / div).toInt().coerceIn(0, 255)
            val b = (cpuAccumulator[idx + 2] / div).toInt().coerceIn(0, 255)
            pixelBuffer[p] = (255 shl 24) or (r shl 16) or (g shl 8) or b
        }

        bufferedImage.setRGB(0, 0, currentWidth, currentHeight, pixelBuffer, 0, currentWidth)
        return Pair(bufferedImage, depthImage)
    }

    private fun updateWorldDataIfNeeded(center: Location) {
        if (lastWorldCenter == null || lastWorldCenter!!.distance(center) > 8.0) {
            lastWorldCenter = center.clone()
            val sx = center.blockX - WORLD_SIZE / 2
            val sy = center.blockY - WORLD_SIZE / 2
            val sz = center.blockZ - WORLD_SIZE / 2
            val world = center.world

            var idx = 0
            activeLightCount = 0

            // 扫描世界
            for (z in 0 until WORLD_SIZE) {
                for (y in 0 until WORLD_SIZE) {
                    for (x in 0 until WORLD_SIZE) {
                        val absX = sx + x
                        val absY = sy + y
                        val absZ = sz + z
                        val mat = world.getBlockAt(absX, absY, absZ).type

                        worldDataHost[idx++] = mat.ordinal

                        // NEE 光源收集
                        val rtMat = RayTraceMaterial.getMaterialReflectionData(mat)
                        if (rtMat.isLight && rtMat.brightness > 0 && activeLightCount < MAX_LIGHTS) {
                            val lightIdx = activeLightCount * 3
                            lightHostBuffer[lightIdx + 0] = absX
                            lightHostBuffer[lightIdx + 1] = absY
                            lightHostBuffer[lightIdx + 2] = absZ
                            activeLightCount++
                        }
                    }
                }
            }

            clEnqueueWriteBuffer(queue, memWorld, CL_TRUE, 0, (Sizeof.cl_int * worldDataHost.size).toLong(), Pointer.to(worldDataHost), 0, null, null)

            if (activeLightCount > 0) {
                clEnqueueWriteBuffer(queue, memLights, CL_TRUE, 0, (Sizeof.cl_int * activeLightCount * 3).toLong(), Pointer.to(lightHostBuffer), 0, null, null)
            }
        }
    }

    private fun updateTexturesIfNeeded() {
        // 简单的 Hash 检查，如果 TextureManager 的数组变了，就更新
        val currentArray = TextureManager.textureArray
        val currentHash = currentArray.contentHashCode()
        if (currentHash != lastTextureArrayHash) {
            lastTextureArrayHash = currentHash
            // 如果大小变了，可能需要重新 create buffer，这里假设大小不变或足够大
            clEnqueueWriteBuffer(queue, memTextures, CL_TRUE, 0, (Sizeof.cl_int * currentArray.size).toLong(), Pointer.to(currentArray), 0, null, null)
        }
    }

    private fun getFallbackColor(mat: Material): Int {
        return when {
            mat == Material.GRASS_BLOCK -> 0x55AA55
            mat == Material.DIRT -> 0x664433
            mat == Material.STONE -> 0x777777
            mat == Material.WATER -> 0x4444FF
            mat == Material.LAVA -> 0xFF4400
            else -> 0xFFFFFF
        }
    }

    private fun initOpenCL() {
        try {
            CL.setExceptionsEnabled(true)
            val numPlatforms = IntArray(1)
            clGetPlatformIDs(0, null, numPlatforms)
            val platform = arrayOfNulls<cl_platform_id>(1).apply { clGetPlatformIDs(1, this, null) }[0]
            val device = arrayOfNulls<cl_device_id>(1)
            clGetDeviceIDs(platform, CL_DEVICE_TYPE_GPU, 1, device, null)

            val contextProps = cl_context_properties()
            contextProps.addProperty(CL_CONTEXT_PLATFORM.toLong(), platform)
            context = clCreateContext(contextProps, 1, device, null, null, null)
            queue = clCreateCommandQueueWithProperties(context, device[0], null, null)

            program = clCreateProgramWithSource(context, 1, arrayOf(kernelSource), null, null)

            try {
                clBuildProgram(program, 0, null, null, null, null)
            } catch (e: Exception) {
                val size = LongArray(1)
                clGetProgramBuildInfo(program, device[0], CL_PROGRAM_BUILD_LOG, 0, null, size)
                val buffer = ByteArray(size[0].toInt())
                clGetProgramBuildInfo(program, device[0], CL_PROGRAM_BUILD_LOG, size[0], Pointer.to(buffer), null)
                throw RuntimeException("CL Build Error: " + String(buffer))
            }
            kernel = clCreateKernel(program, "pathTraceKernel", null)

            // Materials
            val maxMatId = Material.entries.size
            materialDataHost = FloatArray(maxMatId * 8)

            for (mat in Material.entries) {
                val id = mat.ordinal
                val rtMat = RayTraceMaterial.getMaterialReflectionData(mat)
                val texOffset = TextureManager.getMaterialOffset(mat)
                val baseIdx = id * 8

                materialDataHost[baseIdx + 0] = texOffset.toFloat()
                materialDataHost[baseIdx + 1] = if(rtMat.isLight) rtMat.brightness * 1.0f else 0.0f
                materialDataHost[baseIdx + 2] = rtMat.spread
                materialDataHost[baseIdx + 3] = rtMat.refractiveIndex

                var r = 1.0f; var g = 1.0f; var b = 1.0f
                if (rtMat.isLight) {
                    r = rtMat.lightColor.x / 255.0f
                    g = rtMat.lightColor.y / 255.0f
                    b = rtMat.lightColor.z / 255.0f
                } else if (texOffset < 0) {
                    val c = getFallbackColor(mat)
                    r = ((c shr 16) and 0xFF) / 255.0f
                    g = ((c shr 8) and 0xFF) / 255.0f
                    b = (c and 0xFF) / 255.0f
                }
                materialDataHost[baseIdx + 4] = r
                materialDataHost[baseIdx + 5] = g
                materialDataHost[baseIdx + 6] = b
                materialDataHost[baseIdx + 7] = 0.0f
            }

            memWorld = clCreateBuffer(context, CL_MEM_READ_ONLY, (Sizeof.cl_int * worldDataHost.size).toLong(), null, null)
            memPixelOut = clCreateBuffer(context, CL_MEM_WRITE_ONLY, (Sizeof.cl_int * currentWidth * currentHeight).toLong(), null, null)
            memSeeds = clCreateBuffer(context, CL_MEM_READ_WRITE, (Sizeof.cl_uint * currentWidth * currentHeight).toLong(), null, null)
            memMaterials = clCreateBuffer(context, CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR, (Sizeof.cl_float * materialDataHost.size).toLong(), Pointer.to(materialDataHost), null)

            var texArray = TextureManager.textureArray
            if (texArray.isEmpty()) texArray = IntArray(256 * 256)
            lastTextureArrayHash = texArray.contentHashCode()
            memTextures = clCreateBuffer(context, CL_MEM_READ_ONLY or CL_MEM_COPY_HOST_PTR, (Sizeof.cl_int * texArray.size).toLong(), Pointer.to(texArray), null)

            memLights = clCreateBuffer(context, CL_MEM_READ_ONLY, (Sizeof.cl_int * MAX_LIGHTS * 3).toLong(), null, null)

            val r = Random()
            for(i in seedsHost.indices) seedsHost[i] = r.nextInt()
            clEnqueueWriteBuffer(queue, memSeeds, CL_TRUE, 0, (Sizeof.cl_uint * seedsHost.size).toLong(), Pointer.to(seedsHost), 0, null, null)

            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果初始化失败，确保清理
            cleanup()
            throw e
        }
    }

    fun cleanup() {
        if (!isInitialized) return
        if (memWorld != null) clReleaseMemObject(memWorld)
        if (memPixelOut != null) clReleaseMemObject(memPixelOut)
        if (memSeeds != null) clReleaseMemObject(memSeeds)
        if (memTextures != null) clReleaseMemObject(memTextures)
        if (memMaterials != null) clReleaseMemObject(memMaterials)
        if (memLights != null) clReleaseMemObject(memLights)
        if (kernel != null) clReleaseKernel(kernel)
        if (program != null) clReleaseProgram(program)
        if (queue != null) clReleaseCommandQueue(queue)
        if (context != null) clReleaseContext(context)
        isInitialized = false
    }

    override fun getColorInWorld(
        rayTraceResult: HitResult?, startDirection: Vector3f, flatBVHNode: Array<FlatBVHNode>, bvhTree: BVHTree
    ): java.awt.Color = java.awt.Color.BLACK
}