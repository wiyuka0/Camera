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

typedef struct {
    float3 aabbMin;
    float3 aabbMax;
    int leftLeafIndex;
    int rightLeafIndex;
    int isLeaf;
    float3 blockPosition;
} BVHNode;

typedef struct {
    float t;
    int face;
} HitInfo;

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

// HitInfo intersectAABB(float3 ro, float3 rd, float3 aabbMin, float3 aabbMax) {
//     float tMin = 0.0f;
//     float tMax = FLOAT_MAX;
//     int hitFace = FACE_NO_HIT;

//     // Inside box case
//     bool inside = aabbContains(aabbMin, aabbMax, ro);
//     if (inside) {
//         for (int i = 0; i < 3; ++i) {
//             float dir = i == 0 ? rd.x : (i == 1 ? rd.y : rd.z);
//             if (fabs(dir) >= EPSILON) {
//                 HitInfo hit = {0.0f, getAxisFace(i, dir < 0)};
//                 return hit;
//             }
//         }
//     }

//     // Slab method
//     for (int i = 0; i < 3; ++i) {
//         float roComp = i == 0 ? ro.x : (i == 1 ? ro.y : ro.z);
//         float rdComp = i == 0 ? rd.x : (i == 1 ? rd.y : rd.z);
//         float minComp = i == 0 ? aabbMin.x : (i == 1 ? aabbMin.y : aabbMin.z);
//         float maxComp = i == 0 ? aabbMax.x : (i == 1 ? aabbMax.y : aabbMax.z);

//         if (fabs(rdComp) >= EPSILON) {
//             float invD = 1.0f / rdComp;
//             float t0 = (minComp - roComp) * invD;
//             float t1 = (maxComp - roComp) * invD;

//             if (invD < 0.0f) {
//                 float temp = t0;
//                 t0 = t1;
//                 t1 = temp;
//             }

//             if (t0 > tMin) {
//                 tMin = t0;
//                 hitFace = getAxisFace(i, invD < 0.0f);
//             }

//             tMax = fmin(tMax, t1);

//             if (tMax + EPSILON < tMin) {
//                 return (HitInfo){-1.0f, FACE_CLIPPED};
//             }
//         }
//     }

//     return (HitInfo){tMin, hitFace != FACE_NO_HIT ? hitFace : FACE_INSIDE};
// }
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

    return (HitInfo){tMin, getAxisFace(hitAxis, hitSign)};

    // int finalFace = (hitAxis != -1) ? getAxisFace(hitAxis, hitSign) : FACE_INSIDE;
    // return (HitInfo){tMin, finalFace};
}

__kernel void rayTrace(
    __global float3* aabbMin,
    __global float3* aabbMax,
    __global int* leftLeafIndex,
    __global int* rightLeafIndex,
    __global int* isLeaf,
    __global float3* blockPosition,

    __global float3* rayOrigin,
    __global float3* rayDirection,

    long nodes,

    __global float* distanceResult,
    __global int* hitFaceResult,
    __global float4* hitPointResult  // ✅ 新增输出
) {
    int gid = get_global_id(0);

    float3 selfRO = rayOrigin[gid];
    float3 selfRD = rayDirection[gid];

    hitPointResult[gid] = (float4)(NAN, NAN, NAN, 0.0f);


    int stack[STACK_SIZE];
    int top = 0;
    stack[top++] = 0;

    HitInfo closestHit = {-1.0f, FACE_NO_HIT};

    while (top > 0) {
        int nodeIndex = stack[--top];

        float3 nodeMin = aabbMin[nodeIndex];
        float3 nodeMax = aabbMax[nodeIndex];
        int leftIndex = leftLeafIndex[nodeIndex];
        int rightIndex = rightLeafIndex[nodeIndex];
        int isLeafNode = isLeaf[nodeIndex];
        float3 blockPos = blockPosition[nodeIndex];

        HitInfo hitDist = intersectAABB(selfRO, selfRD, nodeMin, nodeMax);
        if (hitDist.t < 0.0f) continue;

        if (isLeafNode) {
            if (hitDist.t >= 0 && (closestHit.t < 0.0f || hitDist.t < closestHit.t)) {
                closestHit = hitDist;
            }
        } else {
            if (top < STACK_SIZE - 2) {
                stack[top++] = leftIndex;
                stack[top++] = rightIndex;
            }
        }
    }

    distanceResult[gid] = closestHit.t;
    hitFaceResult[gid] = closestHit.face;

    if (closestHit.t < -1 + EPSILON && closestHit.t > -1 + (-EPSILON) && closestHit.face != -1) {
        printf("zero1\n");
        printf("%i\n", closestHit.face);
    }

    if (closestHit.t >= 0.0f) {
        float3 hitPoint = selfRO + (selfRD * closestHit.t);
        hitPointResult[gid] = (float4)(hitPoint.x, hitPoint.y, hitPoint.z, 0.0f);

        if(closestHit.face == -3) {
            // printf("-3\n");
        }

        // printf("gid: %d, t: %f, face: %d, hit: (%f, %f, %f)\n", gid, closestHit.t, closestHit.face, hitPoint.x, hitPoint.y, hitPoint.z); // 未到达

        // if(hitPoint.x <= EPSILON && hitPoint.x >= -EPSILON){
        //     printf("zero3"); // 未到达
        // }

        // if(hitPoint.x < 0.0f + EPSILON && hitPoint.y < 0.0f + EPSILON && hitPoint.z < 0.0f + EPSILON &&
        //   hitPoint.x > 0.0f - EPSILON && hitPoint.y > 0.0f - EPSILON && hitPoint.z > 0.0f - EPSILON
        // ){
        //     printf("zero");
        // }

        // if(closestHit.t == -1.0f) {
        //     printf("zero3"); // 未到达
        // }
        // printf("%f, %f, %f\n", hitPoint.x,  hitPoint.y,  hitPoint.z);
    } else {
        hitPointResult[gid] = (float4)(NAN, NAN, NAN, 0.0f); // 或者 (0,0,0)
    }
}
