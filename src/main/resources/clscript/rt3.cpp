#define FACE_X_NEG 0
#define FACE_X_POS 1
#define FACE_Y_NEG 2
#define FACE_Y_POS 3
#define FACE_Z_NEG 4
#define FACE_Z_POS 5 

#define FACE_NO_HIT    -1
#define FACE_CLIPPED   -2
#define FACE_INSIDE    -3
#define FACE_INVALID   -4
#define FACE_INTERNAL  -5

#define STACK_SIZE 64
#define FLOAT_MAX 3.402823466e+38F
#define EPSILON 1e-3f



const sampler_t defaultSampler = CLK_NORMALIZED_COORDS_FALSE |
                                  CLK_ADDRESS_CLAMP |
                                  CLK_FILTER_NEAREST;

float3 getFaceNormalVector(int face) {
    switch(face) {
        case FACE_X_POS:
            return (float3)(1.0f, 0.0f, 0.0f);
        case FACE_X_NEG:
            return (float3)(-1.0f, 0.0f, 0.0f);
        case FACE_Y_POS:
            return (float3)(0.0f, 1.0f, 0.0f);
        case FACE_Y_NEG:
            return (float3)(0.0f, -1.0f, 0.0f);
        case FACE_Z_POS:
            return (float3)(0.0f, 0.0f, 1.0f);
        case FACE_Z_NEG:
            return (float3)(0.0f, 0.0f, -1.0f);
    }
    return (float3)(0.0f, 0.0f, 0.0f);
}


typedef struct {
    float t;
    int face;
    int materialIndex;
    int textureOffset;
    float3 weightFunctionArguments;
} HitInfo;

typedef struct {
    ulong state;
    ulong inc;
} PCGRNG;

typedef struct {
    int* materialFuncId;
    float* materialSpread;
    int* materialSample;
    int* isLight;
    float3* lightColor; // default to (0.0f, 0.0f, 0.0f)
    float* lightBrightness; // default to 0.0f
    int* textureImage;
} Material;

typedef struct {
    float distance;
    int hitFace;
    float3 hitPoint;
    int materialIndex;
    int textureOffset;
    float3 weightFunctionArguments;
} HitResult;

typedef struct {
    float3* aabbMin;
    float3* aabbMax;
    int* leftLeafIndex;
    int* rightLeafIndex;
    int* isLeaf;
    float3* blockPosition;

    int* textureOffset;
    int* materialIndex;
    float3* weightFunctionArguments;
} BVH;

typedef struct {
    float3 color;
    float3 weight;
    float lightBrightness;
} ColorData;

typedef struct {
    HitResult hitResult; // Last 
    float3 incident;
    float3 wc;
    float wx;
} Ray;


inline uint pcg32_random(__private PCGRNG* rng) {
    ulong oldstate = rng->state;
    rng->state = oldstate * 6364136223846793005UL + (rng->inc | 1UL);
    uint xorshifted = (uint)(((oldstate >> 18u) ^ oldstate) >> 27u);
    uint rot = (uint)(oldstate >> 59u);
    return (xorshifted >> rot) | (xorshifted << ((-rot) & 31));
}

inline float pcg32_random_float(__private PCGRNG* rng) {
    return (float)(pcg32_random(rng)) / 4294967296.0f;
}

int clampi(int val, int minVal, int maxVal) {
    return max(min(val, maxVal), minVal);
}

inline int3 getFullPointInTexture(int localOffset, int textureStartOffset, __global int* textureImage) {
    int pixelIndex = textureStartOffset + localOffset;
    int packed = textureImage[pixelIndex];

    int r = (packed >> 16) & 0xFF;
    int g = (packed >> 8) & 0xFF;
    int b = packed & 0xFF;

    return (int3)(r, g, b);
}

int getAxisFace(int axis, int isNegative) {
    switch (axis) {
        case 0: return isNegative ? FACE_X_POS : FACE_X_NEG;
        case 1: return isNegative ? FACE_Y_POS : FACE_Y_NEG;
        case 2: return isNegative ? FACE_Z_POS : FACE_Z_NEG;
        default: return FACE_INVALID;
    }
}

bool aabbContains(float3 aabbMin, float3 aabbMax, float3 point) {
    return point.x >= aabbMin.x - EPSILON && point.x <= aabbMax.x + EPSILON &&
           point.y >= aabbMin.y - EPSILON && point.y <= aabbMax.y + EPSILON &&
           point.z >= aabbMin.z - EPSILON && point.z <= aabbMax.z + EPSILON;
}
HitInfo intersectAABB(float3 ro, float3 rd, float3 aabbMin, float3 aabbMax) {
    float tMin = 0.0f;
    float tMax = FLOAT_MAX;
    int hitAxis = -1;
    int hitSign = 0; // 0 for positive dir, 1 for negative dir

    bool inside = aabbContains(aabbMin, aabbMax, ro);
    if (inside) {
        for (int i = 0; i < 3; ++i) {
            float dir = i == 0 ? rd.x : (i == 1 ? rd.y : rd.z);
            if (fabs(dir) >= EPSILON) {
                return (HitInfo){0.0f, getAxisFace(i, dir < 0)};
            }
        }
    }

    for (int i = 0; i < 3; ++i) {
        float roComp = i == 0 ? ro.x : (i == 1 ? ro.y : ro.z);
        float rdComp = i == 0 ? rd.x : (i == 1 ? rd.y : rd.z);
        float minComp = i == 0 ? aabbMin.x : (i == 1 ? aabbMin.y : aabbMin.z);
        float maxComp = i == 0 ? aabbMax.x : (i == 1 ? aabbMax.y : aabbMax.z);

        if (fabs(rdComp) < EPSILON) {
            if (roComp < minComp - EPSILON || roComp > maxComp + EPSILON) {
                return (HitInfo) {-1.0f, FACE_NO_HIT};
            }
            continue;
        }

        if (fabs(rdComp) >= EPSILON) {
            float invD = 1.0f / rdComp;
            float t0 = (minComp - roComp) * invD;
            float t1 = (maxComp - roComp) * invD;

            if (invD < 0.0f) {
                float temp = t0;
                t0 = t1;
                t1 = temp;
            }

            if (t0 > tMin) {
                tMin = t0;
                hitAxis = i;
                hitSign = invD < 0.0f;
            }

            tMax = fmin(tMax, t1);
            if (tMax + EPSILON < tMin) {
                return (HitInfo){-1.0f, FACE_CLIPPED};
            }
        }
    }


    if (hitAxis == -1) {
        return inside ? (HitInfo) {0.0f, FACE_INSIDE} : (HitInfo) {-1.0f, FACE_NO_HIT}; // fallback: no valid face found
    }

    return (HitInfo){tMin, getAxisFace(hitAxis, hitSign), 0, 0, (float3)(0.0f, 0.0f, 0.0f)};
}
inline int2 getTextureCoords(int face, float3 hitPos, int2 textureScale) {

    float3 blockLocation = (float3)(floor(hitPos.x), floor(hitPos.y), floor(hitPos.z));
    float3 normalizedPos = hitPos - blockLocation;

    float u, v;

    switch (face) {
        case FACE_Z_NEG:  // NORTH
            u = 1.0f - normalizedPos.x;
            v = 1.0f - normalizedPos.y;
            break;
        case FACE_Z_POS:  // SOUTH
            u = normalizedPos.x;
            v = 1.0f - normalizedPos.y;
            break;
        case FACE_X_POS:  // EAST
            u = 1.0f - normalizedPos.z;
            v = 1.0f - normalizedPos.y;
            break;
        case FACE_X_NEG:  // WEST
            u = normalizedPos.z;
            v = 1.0f - normalizedPos.y;
            break;
        case FACE_Y_POS:  // UP
            u = normalizedPos.x;
            v = 1.0f - normalizedPos.z;
            break;
        case FACE_Y_NEG:  // DOWN
            u = normalizedPos.x;
            v = normalizedPos.z;
            break;
        default:
            return (int2)(0, 0);
    }

    // 使用纹理尺寸而非纹理尺寸-1来保持正确分布
    int x = clamp((int)(u * textureScale.x), 0, textureScale.x - 1);
    int y = clamp((int)(v * textureScale.y), 0, textureScale.y - 1);

    return (int2)(x, y);
}

inline int getLocalTextureOffset(int face, float3 hitPos) {
    int2 textureCoord = getTextureCoords(face, hitPos, (int2) (16, 16));
    int localOffset = textureCoord.y * 16 + textureCoord.x;

    return localOffset + (face * 256);
}

HitResult rayTrace(
    BVH* bvh,

    float3 rayOrigin,
    float3 rayDirection
) {
    float3 selfRO = rayOrigin;
    float3 selfRD = rayDirection;

    // hitPointResult[gid] = (float4)(NAN, NAN, NAN, 0.0f);


    int stack[STACK_SIZE];
    int top = 0;
    stack[top++] = 0;

    // HitInfo closestHit = {-1.0f, FACE_NO_HIT, (float3)(NAN, NAN, NAN), 0, 0, 0};
    HitInfo closestHit;
    closestHit.t = -1.0f;
    closestHit.face = FACE_NO_HIT;
    closestHit.materialIndex = 0;
    closestHit.textureOffset = 0;
    closestHit.weightFunctionArguments = (float3)(0.0f);

    while (top > 0) {
        if(top >= STACK_SIZE) {
            printf("OVER_STACK");
        }
        int nodeIndex = stack[--top];
        
        float3 nodeMin = bvh->aabbMin[nodeIndex];
        float3 nodeMax = bvh->aabbMax[nodeIndex];
        int leftIndex = bvh->leftLeafIndex[nodeIndex];
        int rightIndex = bvh->rightLeafIndex[nodeIndex];
        int isLeafNode = bvh->isLeaf[nodeIndex];
        float3 blockPos = bvh->blockPosition[nodeIndex];
        int materialIndex = bvh->materialIndex[nodeIndex];
        // int2 textureIndex = (int2)(bvh->textureOffsetX[nodeIndex], bvh->textureOffsetY[nodeIndex]);
        int textureOffset = bvh->textureOffset[nodeIndex];
        float3 weightFunctionArguments = bvh->weightFunctionArguments[nodeIndex];

        HitInfo hitDist = intersectAABB(selfRO, selfRD, nodeMin, nodeMax);
        if (hitDist.t < 0.0f) continue;

        if (isLeafNode) {
            if (hitDist.t >= 0 && (closestHit.t < 0.0f || hitDist.t < closestHit.t)) {
                closestHit = hitDist;
                closestHit.materialIndex = materialIndex;
                closestHit.textureOffset = textureOffset;
                closestHit.weightFunctionArguments = weightFunctionArguments;
            }
        } else {
            if (top < STACK_SIZE - 2) {
                stack[top++] = leftIndex;
                stack[top++] = rightIndex;
            }
        }
    }
    

    HitResult hitResult;

    // distanceResult[gid] = closestHit.t;
    // hitFaceResult[gid] = closestHit.face;

    hitResult.distance = closestHit.t;
    hitResult.hitFace = closestHit.face;
    hitResult.weightFunctionArguments = closestHit.weightFunctionArguments;

    hitResult.textureOffset = closestHit.textureOffset;



    if (closestHit.t < -1 + EPSILON && closestHit.t > -1 + (-EPSILON) && closestHit.face != -1) {
        printf("zero1\n");
        printf("%i\n", closestHit.face);
    }

    if (closestHit.t >= 0.0f) {
        float3 hitPoint = selfRO + (selfRD * closestHit.t);
        hitResult.hitPoint = (float3)(hitPoint.x, hitPoint.y, hitPoint.z);
    } else {
        hitResult.hitPoint = (float3)(NAN, NAN, NAN);
    }
    return hitResult;
}

/* Weight Functions index++*/

inline float gaussian(float4 params) {
    float x = params.x;
    float m = params.y;

    float xm = x * m;
    float coefficient = 1.0f / (0.4f * sqrt(2.0f * M_PI_F));
    float exponent = -(xm * xm) / 0.8f;

    return coefficient * exp(exponent);
}

inline float linear(float4 args) {
    return 1.0f - args.x;
}

inline float cos_func(float4 args) {
    return cos(args.x * args.y);
}

/* Weight Function */


#define FUNC_GAUSSIAN 0
#define FUNC_LINEAR   1
#define FUNC_COS      2

float calcWeight(
    float3 vec1,
    float3 vec2,
    float3 params,          // Extra params: could be used for shape control
    int materialIndex,

    // Material tables
    Material* materials
) {
    float x = dot(vec1, vec2); // Similarity between direction vectors

    // Get function ID for this material
    int funcId = materials->materialFuncId[materialIndex];

    // Build float4 parameter set
    float4 fullParams = (float4)(x, params.x, params.y, params.z);

    // Dispatch by function ID
    switch (funcId) {
        case FUNC_GAUSSIAN:
            return gaussian(fullParams);
        case FUNC_LINEAR:
            return linear(fullParams);
        case FUNC_COS:
            return cos_func(fullParams);
        default:
            return 0.0f; // Fallback for unknown function
    }
}
void nextReflectionVec(
    int materialIndex,
    float3 idealReflectionDirection
);
inline float3 reflectionDirection(float3 incident, float3 normal) {
    return incident - 2.0f * dot(incident, normal) * normal;
}
inline float3 randomUnitVector(PCGRNG* rng) {
    float theta = pcg32_random_float(rng) * 2 * M_PI_F;
    float z = (pcg32_random_float(rng) + 1.0f) - 1.0f;
    float r = sqrt(1.0f - z * z);
    return (float3) (
        (r * cos(theta)),
        (r * sin(theta)),
        z
    );
}
inline float lerp_f(float a, float b, float t) {
    return a + (b - a) * t;
}
inline float coerceIn(float source, float min, float max) {
    if(source > max) {
        return max;
    } else if(source < min){
        return min;
    } else return source;
}
inline float3 rotateAxis(float3 input, float angle, float3 axis) {
    float c = cos(angle);
    float s = sin(angle);
    float oneMinusC = 1.0f - c;
    float3 k = normalize(axis);

    return input * c
         + cross(k, input) * s
         + k * dot(k, input) * oneMinusC;
}
inline float3 rotateVectorFromZAxis(float3 vec, float3 targetDir) {
    float3 up = (float3) (0.0f, 1.0f, 0.0f);
    
    // float3 targetDirNormalized = targetDir.normalize();
    float3 targetDirNormalized = normalize(targetDir);

    float3 zeroVector = (float3) (0.0f, 0.0f, 0.0f);

    float3 axis = cross(targetDir, up); // FIXME: always cross 0

    float angle = acos(coerceIn(dot(up, targetDirNormalized), -1.0f, 1.0f));

    if(length(axis) < EPSILON) {
        return rotateAxis(vec, angle, (float3)(1.0f, 0.0f, 0.0f));
    }

    return rotateAxis(vec, angle, axis);
}
inline float3 perturbedDirection(float3 base, float spread, PCGRNG* rng) {
    if(spread >= -EPSILON && spread <= EPSILON) {
        return base;
    }

    if(spread >= 1.0f - EPSILON && spread <= 1.0f + EPSILON) {
        return randomUnitVector(rng);
    }

    float coneAngle = spread * M_PI_F / 2.0f;
    float cosTheta = lerp_f(1.0f, cos(coneAngle), pcg32_random_float(rng));
    float sinTheta = sqrt(1.0f - cosTheta * cosTheta);
    float phi = pcg32_random_float(rng) * 2 * M_PI_F;

    float x = (cos(phi) * sinTheta);
    float y = (sin(phi) * sinTheta);
    float z = cosTheta;

    float3 output = (float3) (x, y, z);

    return rotateVectorFromZAxis(output, base);
}

#define MAX_PIXEL_RAYS_COUNT 8001 //Max single material reflection ^ maxReflectionTimes
#define MAX_SINGLE_MATERIAL_REFLECTION 20
__kernel void getWorldColor(
    // Camera data
    __global float3* cameraOrigins, // 摄像机原点
    __global float3* directions,    // 像素方向

    // BVH data 
    __global float3* aabbMin,       // BVH节点信息 (FlatBVHNode)
    __global float3* aabbMax,
    __global int* leftLeafIndex,
    __global int* rightLeafIndex,
    __global int* isLeaf,
    __global float3* blockPosition,
    __global int* materialOffset,
    __global int* materialIndex,
    // Material meta data -- Use BVH data index
    __global float4 *weightFunctionArguments, // 材质权重函数参数（属于FlatBVHNode）
    
    // Material type data
    __global int* materialFuncId,    // 材质信息
    __global float* materialSpread,
    __global int* materialSample,
    __global int* isLight,           // 如果材质是光源的信息
    __global float3* lightColor,
    __global float* lightBrightness,
    __global __read_only int* textureImage, // 材质图 (ReflectionMaterial->textureArray)

    // Global meta
    int maxReflectionTimes,
    
    // Medium memory space
    __global Ray* lastPreprocessList,  // 内存块 650MB+ (Kotlin端分配)
    __global ColorData* hitColorList,  // 内存块 380MB+ (Kotlin端分配)
    __global Ray* updateTemp,          // 内存块 55 MB+ (Kotlin端分配)

    long currentWorldTime,             // 当前世界时间

    long maxSingleRayCount,            // 最大单材质采样数（现在是20）
    long maxPixelRaysCount,            // 最大单像素射线数量 (20^最大反射次数)

    __global float3* color //out
){
    int gid = get_global_id(0);
    int globalBaseOffset = gid * MAX_PIXEL_RAYS_COUNT;

    float3 cameraOrigin = cameraOrigins[gid];
    float3 direction = directions[gid];

    HitResult startHitInfo = rayTrace(aabbMin, aabbMax, leftLeafIndex, rightLeafIndex, isLeaf, blockPosition, cameraOrigin, direction);

    int currentReflectionTimes = 0;

    // Max single ray count(MSRC) = max of material samples + 1
    // Max One Point Count = MSRC ^ 2

    // ColorData hitColorList[maxPixelRaysCount];
    
    // Ray lastPreprocessList[maxPixelRaysCount];

    Material materials;
    materials.materialFuncId = materialFuncId;
    materials.materialSpread = materialSpread;
    materials.materialSample = materialSample;
    materials.isLight = isLight;
    materials.lightColor = lightColor;
    materials.lightBrightness = lightBrightness;
    materials.textureImage = textureImage;
    materials.weightFunctionArguments = weightFunctionArguments;

    BVH bvh;
    bvh.aabbMin = aabbMin;
    bvh.aabbMax = aabbMax;
    bvh.leftLeafIndex = leftLeafIndex;
    bvh.rightLeafIndex = rightLeafIndex;
    bvh.isLeaf = isLeaf;
    bvh.blockPosition = blockPosition;
    bvh.textureOffset = materialOffset;
    bvh.materialIndex = materialIndex;

    PCGRNG rng;
    rng.state = gid ^ currentWorldTime;
    rng.inc = gid * 2 + 1;



    int hitColorListIndex = 0;
    int lastPreprocessListIndex = 0;

    Ray startRay;

    startRay.hitResult = startHitInfo;
    startRay.incident = direction;
    startRay.wc = (float3) (1.0f, 1.0f, 1.0f);
    startRay.wx = 1.0f;

    lastPreprocessList[lastPreprocessListIndex + globalBaseOffset] = startRay;
    lastPreprocessListIndex++;

    while(currentReflectionTimes < maxReflectionTimes) {
        
        int updateTempIndex = 0;
        for(int i = 0; i < lastPreprocessListIndex; i++) {
            Ray currRay = lastPreprocessList[i + globalBaseOffset];
            if(currRay.hitResult.hitFace >= 0) {
                rayTraceFromLastRay(
                    currRay.hitResult.materialIndex,
                    currentReflectionTimes,
                    maxReflectionTimes,
                    &currRay,
                    updateTemp,
                    &updateTempIndex,
                    hitColorList,
                    &hitColorListIndex,
                    &materials,
                    &bvh,
                    &rng
                );
            }
        }
        
        //Copy memory `updateTemp` -> `lastPreprocessListIndex`

        for(int j = 0; j < updateTempIndex; j++) {
            lastPreprocessList[j + globalBaseOffset] = updateTemp[j + globalBaseOffset];
        }
        lastPreprocessListIndex = updateTempIndex; // Reset top pointer
        currentReflectionTimes++;
    }

    // Mix color and return to host
    float3 finalColor = (float3)(0.0f, 0.0f, 0.0f);
    float totalWeight = 0.0f;

    for (int i = 0; i < hitColorListIndex; i++) {
        ColorData data = hitColorList[i + globalBaseOffset];
        float weight = data.wx * data.lightBrightness;
        finalColor += data.color * weight;
        totalWeight += weight;
    }

    if (totalWeight > EPSILON) {
        finalColor /= totalWeight;
        finalColor = clamp(finalColor, (float3)(0.0f), (float3)(1.0f)); // 归一化防止溢出
    } else {
        finalColor = (float3)(0.0f, 0.0f, 0.0f); // 或者你可以设置背景色
    }
    // 写回最终像素颜色
    color[gid] = finalColor;
}
void rayTraceFromLastRay(
    int materialIndex,
    int currentReflectionTimes,
    int maxReflectionTimes,
    Ray* incidentRay,

    // float3* reflectionVectors, // out Require size >= maxMaterialSamples
    // float* reflectionWeights,

    Ray* reflectionRays,
    int* startIndex,

    float3* colorList,
    int* colorListIndex,

    // Material table
    Material* materials,
    // BVH Data
    BVH* bvh,
    PCGRNG* rng

) {

    if(currentReflectionTimes >= maxReflectionTimes) {
        return;
    } else {
        HitResult currentPoint = rayTrace(
            bvh,
            incidentRay->hitResult.hitPoint,
            incidentRay->incident
        );

        if(materials->isLight[currentPoint.materialIndex]) {
            // TODO: Hit to light, add LightColor to colorList
            float brightness = materials->lightBrightness[currentPoint.materialIndex];
            float3 lightColor = materials->lightColor[currentPoint.materialIndex];

            float3 wc = incidentRay->wc * lightColor;


            ColorData colorData;

            colorData.lightBrightness = brightness;
            colorData.color = wc;
            colorData.weight = incidentRay->wx;

            colorList[*colorListIndex] = colorData;
            (*colorListIndex)++;
            return; // or reflection continue.
        }

        int hitFace = currentPoint.hitFace;

        if(hitFace == FACE_NO_HIT) {
            // TODO: Hit to sky, add SkyColor to colorList
            return;
        } else if(hitFace < 0) {
            printf("RayTrace Failed.");
            return;
        }

        float3 hitFaceNormal = getFaceNormalVector(hitFace);

        float3 idealReflectionDirection = reflectionDirection(incidentRay->incident, hitFaceNormal);

        int textureBaseOffset = currentPoint.textureOffset;
        // int2 textureCoord = getTextureCoords(currentPoint.hitFace, currentPoint.hitPoint, (int2) (16, 16));
        int localOffset = getLocalTextureOffset(currentPoint.hitFace, currentPoint.hitPoint);

        int3 currentHitPosColor = getFullPointInTexture(localOffset, textureBaseOffset, materials->textureImage);

        float3 normalizedColor = (float3)(currentHitPosColor.x / 255.0f, currentHitPosColor.y / 255.0f, currentHitPosColor.z / 255.0f);
        float3 currentWeightColor = normalizedColor * incidentRay->wc;

        int samples = materials->materialSample[currentPoint.materialIndex];
        
        float materialSpread = materials->materialSpread[currentPoint.materialIndex];

        for(int currentSample = 0; currentSample < samples; currentSample++) {
            float3 perturbedVecReflection = perturbedDirection(idealReflectionDirection, materialSpread, rng);
            if(dot(perturbedVecReflection, hitFaceNormal) < 0.0f) continue;
            // float weight = calcWeight(idealReflectionDirection, perturbedVecReflection, incidentRay.materialIndex, incidentRay.hitResult.weightFunctionArguments, materials);  
            float weight = calcWeight(
                idealReflectionDirection,
                perturbedVecReflection,
                incidentRay->hitResult.weightFunctionArguments,
                incidentRay->hitResult.materialIndex,
                materials
            );

            float3 offsetStartPos = currentPoint.hitPoint + (hitFaceNormal * EPSILON);
            Ray ray;
            ray.hitResult = rayTrace(bvh, offsetStartPos, perturbedVecReflection);
            ray.wx = weight * incidentRay->wx;
            ray.wc = currentWeightColor;
            ray.incident = perturbedVecReflection;

            reflectionRays[*startIndex] = ray;
            (*startIndex)++;        
        }
    }
}
