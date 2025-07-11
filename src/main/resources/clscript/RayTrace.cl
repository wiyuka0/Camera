__kernel void render_kernel(
    __global uchar4* output,
    const int width,
    const int height,
    const float fov,
    const float3 camPos,
    const float3 camDir,
    __global const Block* blocks,
    const int numBlocks
) {
    int x = get_global_id(0);
    int y = get_global_id(1);
    if (x >= width || y >= height) return;

    float aspect = (float)width / (float)height;
    float fovScale = tan(radians(fov * 0.5f));
    float u = ((float)x + 0.5f) / (float)width * 2.0f - 1.0f;
    float v = (1.0f - ((float)y + 0.5f) / (float)height) * 2.0f - 1.0f;

    float3 forward = normalize(camDir);
    float3 right = normalize(cross(forward, (float3)(0,1,0)));
    float3 up = normalize(cross(right, forward));
    float3 rayDir = normalize(forward + right * u * fovScale + up * v * fovScale);

    float3 color = trace_ray(camPos, rayDir, blocks, numBlocks, 0);

    int idx = y * width + x;
    output[idx] = (uchar4)(
        (uchar)(color.x * 255.0f),
        (uchar)(color.y * 255.0f),
        (uchar)(color.z * 255.0f),
        255
    );
}
#define MAX_DEPTH 3
#define BLOCK_MAX 128

inline float3 sky_color(float3 dir) {
    float t = 0.5f * (dir.y + 1.0f);
    return (1.0f - t) * (float3)(1.0f, 1.0f, 1.0f) + t * (float3)(0.5f, 0.7f, 1.0f);
}

inline float3 reflect(float3 I, float3 N) {
    return I - 2.0f * dot(I, N) * N;
}

inline int intersect_aabb(
    float3 origin, float3 dir,
    float3 bmin, float3 bmax,
    float* tHit, float3* normal
) {
    float3 invD = 1.0f / dir;
    float3 t0s = (bmin - origin) * invD;
    float3 t1s = (bmax - origin) * invD;
    float3 tsm = fmin(t0s, t1s);
    float3 tsmx = fmax(t0s, t1s);

    float tmin = fmax(fmax(tsm.x, tsm.y), tsm.z);
    float tmax = fmin(fmin(tsmx.x, tsmx.y), tsmx.z);

    if (tmax < 0 || tmin > tmax) return 0;

    *tHit = tmin;

    float3 p = origin + dir * tmin;
    *normal = (float3)(0.0f);
    for (int i = 0; i < 3; ++i) {
        if (fabs(p.s[i] - bmin.s[i]) < 1e-3f) (*normal).s[i] = -1.0f;
        if (fabs(p.s[i] - (bmin.s[i] + (bmax.s[i] - bmin.s[i]))) < 1e-3f) (*normal).s[i] = 1.0f;
    }

    return 1;
}

float3 trace_ray(
    float3 origin, float3 dir,
    __global const Block* blocks, int numBlocks,
    int depth
) {
    if (depth >= MAX_DEPTH) return (float3)(0.0f);

    float nearestT = 1e30f;
    int hitIndex = -1;
    float3 hitNormal = (float3)(0.0f);

    for (int i = 0; i < numBlocks; ++i) {
        float tHit;
        float3 normal;
        float3 bmin = blocks[i].location;
        float3 bmax = blocks[i].location + blocks[i].scale;

        if (intersect_aabb(origin, dir, bmin, bmax, &tHit, &normal)) {
            if (tHit < nearestT && tHit > 0.001f) {
                nearestT = tHit;
                hitIndex = i;
                hitNormal = normal;
            }
        }
    }

    if (hitIndex == -1) {
        return sky_color(dir);
    }

    Block hit = blocks[hitIndex];
    float3 hitPoint = origin + dir * nearestT;

    // Emission (self-lighting)
    float3 baseColor = hit.color * hit.brightness;

    float3 finalColor = baseColor;

    if (hit.reflectivity > 0.01f) {
        float3 reflectDir = reflect(dir, hitNormal);
        float3 reflectedColor = trace_ray(hitPoint + hitNormal * 0.001f, reflectDir, blocks, numBlocks, depth + 1);
        finalColor += reflectedColor * hit.reflectivity;
    }

    return clamp(finalColor, 0.0f, 1.0f);
}
