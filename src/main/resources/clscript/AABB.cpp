#define FACE_X_NEG 0
#define FACE_X_POS 1
#define FACE_Y_NEG 2
#define FACE_Y_POS 3
#define FACE_Z_NEG 4
#define FACE_Z_POS 5

__kernel void rayIntersectAABB(
    __global const float3* origins,
    __global const float3* dirs,
    __global const float3* mins,
    __global const float3* maxs,
    __global float* tOut,
    __global int* faceOut,
    const float tMin,
    const float tMax
) {
    int gid = get_global_id(0);
    float3 origin = origins[gid];
    float3 dir = dirs[gid];
    float3 minB = mins[gid];
    float3 maxB = maxs[gid];

    const float EPSILON = 1e-6f;

    float tMinLocal = tMin;
    float tMaxLocal = tMax;
    int hitFace = -1;

    // Check if inside box
    bool inside = true;
    for (int i = 0; i < 3; ++i) {
        float o, minv, maxv;
        if (i == 0) {
            o = origin.x; minv = minB.x; maxv = maxB.x;
        } else if (i == 1) {
            o = origin.y; minv = minB.y; maxv = maxB.y;
        } else {
            o = origin.z; minv = minB.z; maxv = maxB.z;
        }

        if (o < minv - EPSILON || o > maxv + EPSILON) {
            inside = false;
            break;
        }
    }

    if (inside) {
        for (int i = 0; i < 3; ++i) {
            float d;
            if (i == 0) d = dir.x;
            else if (i == 1) d = dir.y;
            else d = dir.z;

            if (fabs(d) >= EPSILON) {
                hitFace = (d < 0) ? i * 2 + 1 : i * 2;
                tOut[gid] = 0.0f;
                faceOut[gid] = hitFace;
                return;
            }
        }
        tOut[gid] = 0.0f;
        faceOut[gid] = FACE_Y_POS;
        return;
    }

    for (int i = 0; i < 3; ++i) {
        float d, o, minv, maxv;

        if (i == 0) {
            d = dir.x; o = origin.x; minv = minB.x; maxv = maxB.x;
        } else if (i == 1) {
            d = dir.y; o = origin.y; minv = minB.y; maxv = maxB.y;
        } else {
            d = dir.z; o = origin.z; minv = minB.z; maxv = maxB.z;
        }

        if (fabs(d) < EPSILON) {
            if (o < minv - EPSILON || o > maxv + EPSILON) {
                tOut[gid] = -1.0f;
                faceOut[gid] = -1;
                return;
            }
            continue;
        }

        float invD = 1.0f / d;
        float t0 = (minv - o) * invD;
        float t1 = (maxv - o) * invD;

        if (invD < 0.0f) {
            float temp = t0;
            t0 = t1;
            t1 = temp;
        }

        if (t0 > tMinLocal) {
            tMinLocal = t0;
            hitFace = (invD < 0) ? i * 2 + 1 : i * 2;
        }

        if (t1 < tMaxLocal) {
            tMaxLocal = t1;
        }

        if (tMaxLocal + EPSILON < tMinLocal) {
            tOut[gid] = -1.0f;
            faceOut[gid] = -1;
            return;
        }
    }

    tOut[gid] = tMinLocal;
    faceOut[gid] = (hitFace == -1) ? FACE_Y_POS : hitFace;
}
