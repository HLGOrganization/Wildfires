#version 150

/*
 * Direct Forge adaptation of the 2026-08-24 user-supplied nebula shader set.
 * The cloud equations follow 1.txt; the tone mapping and colour grading follow
 * 5.txt, whose Buffer B/C/D and image pass come from sonicether's
 * "Gargantua With HDR Bloom". The stars() routine that 1.txt credits to
 * Shadertoy fl2Bzd is deliberately not compiled here, and the source's opaque
 * backdrop and five-layer background star field are absent: Minecraft's
 * existing world framebuffer is the background.
 * Minecraft-specific changes are limited to world-space ray construction,
 * deterministic first-step jitter in place of iChannel3 history, alpha output,
 * and a separate thresholded highlight pass for the requested bloom.
 * License: non-commercial, share-alike, attribution (Shadertoy platform
 * default). This file is not part of the Wildfires ARR partition.
 * Provenance: third_party/nebula/user-supplied-2026-08-24/PROVENANCE.md
 */
uniform float Opacity;
uniform float Seed;
uniform float GlowPass;
uniform vec3 CenterRelative;
uniform float Radius;
uniform vec2 ScreenSize;

in vec2 texCoord;
in vec3 rayTarget;
out vec4 fragColor;

const mat3 SOURCE_NOISE_MATRIX = mat3(
     0.700098,  1.176714, -1.508157,
    -1.845627,  0.685671, -0.321783,
     0.318402,  1.461516,  1.288119);

// Minecraft-local presentation controls. Opacity remains derived only from visible
// cloud emission; these never restore the excluded backdrop or source absorption alpha.
const float WORLD_OPACITY_GAIN = 2.0;
const float WORLD_ALPHA_CAP = 0.84;
const float WORLD_SATURATION_BOOST = 1.35;

float SinNoise(vec3 p, int octaveCount, float multiplier) {
    float density = 0.0;
    float weight = 3.5;
    float track = 1.0;
    for (int octave = 0; octave < 3; octave++) {
        if (octave >= octaveCount) {
            break;
        }
        p = p * SOURCE_NOISE_MATRIX * multiplier;
        p += sin(p.zxy * track) * 0.25;
        density += 1.8 - abs(dot(cos(p), sin(p.zxy)) - 0.3) * weight;
        weight *= 0.7;
        track *= 1.45;
    }
    return density;
}

float RandomStep(vec2 coordinate, float seedValue) {
    return fract(sin(dot(coordinate + fract(11.4514 * sin(seedValue)),
            vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 worldToNebula(vec3 relativePosition) {
    // This is the supplied target direction normalize(-1, 1, 0.3), anchored
    // in the Minecraft world instead of the Shadertoy mouse camera.
    vec3 axis = normalize(vec3(-1.0, 1.0, 0.30));
    vec3 tangent = normalize(cross(vec3(0.0, 1.0, 0.0), axis));
    vec3 bitangent = normalize(cross(axis, tangent));
    float roll = (Seed - 0.5) * 0.38;
    float cosine = cos(roll);
    float sine = sin(roll);
    vec3 rolledTangent = tangent * cosine + bitangent * sine;
    vec3 rolledBitangent = bitangent * cosine - tangent * sine;
    return vec3(dot(relativePosition, rolledTangent),
            dot(relativePosition, rolledBitangent), dot(relativePosition, axis));
}

void cloudcolor(inout vec4 baseColor, inout float cloudEmission,
        float stepLength, vec3 efpos) {
    float positionRadius = length(efpos);
    if (positionRadius >= 1.0) {
        return;
    }

    float S1 = pow(pow(efpos.x, 2.0) + pow(efpos.y, 2.0), 2.0);
    float Sf = 1.0 / max(pow(positionRadius,
            2.0 + 1.0 / (1.0 + 1000.0 * efpos.z * efpos.z)), 0.001);

    float shape_dis = S1 - 0.1 * 0.8 * pow(abs(efpos.z) + 0.005, 2.0);
    float eff_dis = shape_dis * Sf;
    float w_shape_dis = S1 - 0.4 * 0.8 * pow(abs(efpos.z) + 0.005, 2.0);
    float weff_dis = w_shape_dis * Sf;
    float ww_shape_dis = S1 - 0.4 * 0.8 * pow(abs(efpos.z) + 0.005, 2.0);
    float wweff_dis = ww_shape_dis * Sf;

    float safeRadius = max(positionRadius, 0.00001);
    float parallelDenominator = sqrt(max(0.000001,
            0.01 * tanh(positionRadius / 0.2) + abs(efpos.z) * 0.316));
    float asinhZ = log(1.5 * efpos.z
            + sqrt(2.25 * efpos.z * efpos.z + 1.0)) / 1.5;
    vec3 parallelPosition = vec3(efpos.x, efpos.y, asinhZ)
            / parallelDenominator;
    float noise0 = SinNoise(parallelPosition, 3, 1.4) - 1.0;

    float spnoise0 = SinNoise(3.0 * efpos, 3, 1.2);
    float spnoise1 = SinNoise(3.0 * efpos
            / max(tanh(positionRadius), 0.00001), 3, 1.3);
    float spnoise2 = SinNoise(1.5 * efpos, 3, 1.2);
    float insph_strength = 0.1 + 0.9 * clamp(0.54 + spnoise0
            + 30.0 * (positionRadius - 0.4), 0.0, 1.0);
    float bristh = clamp(0.54 + spnoise2
            + 30.0 * (positionRadius - 0.35), 0.0, 1.0)
            * exp(-0.8 * max(0.0, 0.54 + spnoise2
            + 30.0 * (positionRadius - 0.35) - 1.0));
    float midsph_strength = clamp(0.54 + spnoise1
            - 20.0 * (positionRadius - 0.6), 0.0, 1.0);
    float mid2sph_strength = clamp(0.54 + spnoise1
            - 40.0 * (positionRadius - 0.53), 0.0, 1.0);
    float outsph_strength = clamp(0.54 + spnoise0
            - 20.0 * (positionRadius - 0.8), 0.0, 1.0)
            * (1.0 - pow(positionRadius, 10.0));

    float strengthw0 = exp(-0.6 * abs(noise0
            - 100.0 * (1.0 - 0.3 * positionRadius) * pow(weff_dis, 1.0)));
    strengthw0 *= midsph_strength * (2.0 - insph_strength);
    float strengthw1 = 0.5 * exp(0.5 * (spnoise0 - 4.994)
            - 0.2 * abs(noise0 - (1.0 - 0.3 * positionRadius)));
    strengthw1 *= midsph_strength;
    float strengthwa = exp(-0.6 * abs(SinNoise(parallelPosition.yxz, 3, 1.4)
            - 1.0 - 30.0 * (1.0 - 0.3 * positionRadius)
            * pow(wweff_dis, 1.0)));
    strengthwa *= outsph_strength;
    strengthwa *= max(1.0 - abs(1.0 - noise0), 0.0);

    float strength0 = exp(-0.6 * abs(noise0 - 100.0 * eff_dis));
    strength0 *= outsph_strength;
    strength0 *= insph_strength;
    float strength1 = clamp(noise0 - 200.0 * eff_dis, 0.0, 1.0);
    float fs1 = strength1 * bristh;
    float fs2 = strength1 * pow(bristh, 2.0);
    strength1 *= outsph_strength;
    strength1 *= mix(insph_strength, 1.6,
            pow(1.0 - (efpos.x * efpos.x + efpos.y * efpos.y), 50.0));

    float fs0 = strength0;
    float nonsp = mix(1.0,
            0.5 + 0.5 * tanh(SinNoise(efpos / safeRadius, 1, 1.3) + 1.0),
            clamp(4.0 * (positionRadius - 0.2), 0.0, 1.0));
    fs0 *= max(0.0, 1.0 - 2.0 * abs(0.5 - nonsp));
    strengthw0 *= nonsp;
    strength0 *= nonsp;
    strength1 *= nonsp;

    vec4 color = vec4(0.478, 0.196, 0.106, 0.0)
            + 0.66 * mid2sph_strength * vec4(0.17, 0.43, 0.61, 0.0)
            + 0.66 * (1.0 - insph_strength) * vec4(0.3, 0.66, 0.66, 0.0);
    vec4 sampleColor = vec4(0.0);
    sampleColor += 5.0 * strength0
            * mix(0.5 + 0.5 * tanh(spnoise0), 1.0, 0.1) * color;
    sampleColor += 2.0 * fs0
            * mix(0.5 + 0.5 * tanh(spnoise0), 1.0, 0.1)
            * vec4(1.0, 0.0, 0.0, 0.0);
    sampleColor += strength1 * color * vec4(min(1.0, positionRadius),
            0.8 + 0.2 * min(1.0, positionRadius), 1.0, 0.0);
    sampleColor += 2.0 * fs1 * vec4(0.76, 0.25, 0.27, 0.0);
    sampleColor += 2.0 * fs2 * vec4(0.3, 0.3, 0.3, 0.0);
    sampleColor += (0.3 + 0.7 * positionRadius) * strengthw0 * 3.0
            * (vec4(0.5, 0.3, 0.2, 0.0)
            + 0.66 * (1.0 - insph_strength) * vec4(0.67, 0.83, 1.01, 0.0));
    sampleColor.a += 10.0 * positionRadius * strengthwa * 3.0;
    sampleColor += strengthw1 * 3.0 * vec4(0.5, 0.3, 0.2, 0.0);
    sampleColor += 10.0 * pow(1.0 - positionRadius, 10.0)
            * (0.5 + 0.5 * tanh(10.0 * eff_dis))
            * vec4(0.4, 0.32, 0.26, 0.0);
    sampleColor *= stepLength;

    float Reddening = 0.6;
    float Saturation = 0.3;
    float aR = 1.0 + Reddening * (1.0 - 1.0);
    float aG = 1.0 + Reddening * (3.0 - 1.0);
    float aB = 1.0 + Reddening * (6.0 - 1.0);
    float remaining = max(1.0 - baseColor.a, 0.0);
    float Sum_rgb = (sampleColor.r + sampleColor.g + sampleColor.b)
            * pow(remaining, aG);
    float r001 = 0.0;
    float g001 = 0.0;
    float b001 = 0.0;
    float denominator = sampleColor.r * pow(remaining, aR)
            + sampleColor.g * pow(remaining, aG)
            + sampleColor.b * pow(remaining, aB);
    if (denominator > 0.000001) {
        r001 = Sum_rgb * sampleColor.r * pow(remaining, aR) / denominator;
        g001 = Sum_rgb * sampleColor.g * pow(remaining, aG) / denominator;
        b001 = Sum_rgb * sampleColor.b * pow(remaining, aB) / denominator;
        float saturationSum = max(r001 + g001 + b001, 0.000001);
        r001 *= pow(3.0 * r001 / saturationSum, Saturation);
        saturationSum = max(r001 + g001 + b001, 0.000001);
        g001 *= pow(3.0 * g001 / saturationSum, Saturation);
        saturationSum = max(r001 + g001 + b001, 0.000001);
        b001 *= pow(3.0 * b001 / saturationSum, Saturation);
    }
    vec3 transportedCloud = vec3(r001, g001, b001);
    baseColor.rgb += transportedCloud;
    // Separate cloud-body coverage while the raymarch is still structured data.
    // No source backdrop code exists in this runtime program.
    cloudEmission += dot(transportedCloud, vec3(0.2126, 0.7152, 0.0722));
    baseColor.a += sampleColor.a * remaining;
}

vec3 sourceToneMap(vec3 color) {
    color = pow(max(color, vec3(0.0)), vec3(1.5));
    color = color / (vec3(1.0) + color);
    color = pow(color, vec3(1.0 / 1.5));
    color = color * color * (vec3(3.0) - 2.0 * color);
    color = pow(max(color, vec3(0.0)), vec3(1.3, 1.20, 1.0));
    color = clamp(color * 1.01, vec3(0.0), vec3(1.0));
    return pow(color, vec3(0.7 / 2.2));
}

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec2 uv = texCoord * 2.0 - 1.0;
    uv.y = -uv.y;
    // This is a world-space proxy quad rather than Shadertoy's opaque screen.
    // Reach zero before the quad edge so its square silhouette can never show.
    float edgeFade = 1.0 - smoothstep(0.88, 1.0, length(uv));
    vec3 rayDirection = normalize(rayTarget);
    vec3 cameraToCenter = -CenterRelative;
    float halfB = dot(rayDirection, cameraToCenter);
    float discriminant = halfB * halfB
            - (dot(cameraToCenter, cameraToCenter) - Radius * Radius);
    vec4 sourceColor = vec4(0.0);
    float cloudEmission = 0.0;

    if (discriminant > 0.0) {
        float root = sqrt(discriminant);
        float tCurrent = max(0.0, -halfB - root);
        float tEnd = -halfB + root;
        float firstStep = RandomStep(gl_FragCoord.xy / ScreenSize,
                Seed * 173.0 + 0.5);
        // 1.txt advances by 0.03 * targetR; 68 iterations cover a diameter.
        for (int stepIndex = 0; stepIndex < 68; stepIndex++) {
            float normalizedStep = (stepIndex == 0 ? firstStep : 1.0) * 0.03;
            tCurrent += normalizedStep * Radius;
            if (tCurrent >= tEnd) {
                break;
            }
            vec3 objectPosition = worldToNebula((rayDirection * tCurrent
                    - CenterRelative) / Radius);
            cloudcolor(sourceColor, cloudEmission, normalizedStep, objectPosition);
            if (sourceColor.a > 0.95) {
                break;
            }
        }
    }

    sourceColor = max(sourceColor, vec4(0.0));

    vec3 gradedColor = sourceToneMap(sourceColor.rgb);
    float preSaturationLuma = luminance(gradedColor);
    gradedColor = clamp(vec3(preSaturationLuma)
            + (gradedColor - vec3(preSaturationLuma)) * WORLD_SATURATION_BOOST,
            vec3(0.0), vec3(1.0));
    // sourceColor.a belongs to the supplied raymarch's internal absorption,
    // reddening and star occlusion.  It is not Minecraft world opacity: using
    // it here turns optically dense but black samples into an opaque rectangle.
    float visiblePeak = max(gradedColor.r, max(gradedColor.g, gradedColor.b));
    float visibleLuma = luminance(gradedColor);
    float cloudCoverage = 1.0 - exp(-cloudEmission * 1.6);
    // Coverage comes only from raymarched cloud emission. The source backdrop is never
    // evaluated, so an empty ray is exactly transparent and exposes the Minecraft world.
    float contentAlpha = clamp(cloudCoverage
            * (visiblePeak * 0.78 + visibleLuma * 0.22) * WORLD_OPACITY_GAIN,
            0.0, WORLD_ALPHA_CAP)
            * edgeFade * Opacity;
    if (GlowPass < 0.5) {
        if (contentAlpha <= 0.002) {
            discard;
        }
        fragColor = vec4(gradedColor * contentAlpha, contentAlpha);
    } else {
        // 5.txt currently comments out GetBloom(); this is the spell's separate
        // requested bloom, not a claim that Buffer B/C/D are running here.
        vec3 thresholdHighlight = max(gradedColor - vec3(0.36), vec3(0.0));
        thresholdHighlight *= thresholdHighlight * 2.2;
        float glowAlpha = clamp(luminance(thresholdHighlight) * 0.72,
                0.0, 0.62) * edgeFade * Opacity;
        if (glowAlpha <= 0.002) {
            discard;
        }
        fragColor = vec4(thresholdHighlight, glowAlpha);
    }
}
