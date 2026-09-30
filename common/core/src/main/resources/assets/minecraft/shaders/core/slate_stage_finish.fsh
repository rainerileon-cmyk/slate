#version 150

// Slate stage: the look of a finished scene. The scene (premultiplied alpha) plus its glow, a touch more
// saturation and contrast, a warm tint, darker corners. Premultiplied out, so a see-through scene stays see-through.

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float Bloom;
uniform float Vignette;
uniform float Contrast;
uniform float Saturation;
uniform float Warmth;
uniform float Alpha;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 scene = texture(Sampler0, texCoord0);
    vec3 glow = texture(Sampler1, texCoord0).rgb * Bloom;
    float a = scene.a;

    vec3 c = scene.rgb;
    float grey = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(grey), c, Saturation);
    // Contrast about mid grey; the colour is premultiplied, so mid grey is too.
    c = (c - 0.5 * a) * Contrast + 0.5 * a;
    c += vec3(Warmth, Warmth * 0.3, -Warmth) * a;
    c = max(c, vec3(0.0)) + glow;

    vec2 d = (texCoord0 - 0.5) * vec2(1.0, 0.82);
    float corner = smoothstep(0.32, 0.92, length(d) * 1.6);
    c *= 1.0 - Vignette * corner;

    float outA = max(a, clamp(max(glow.r, max(glow.g, glow.b)), 0.0, 1.0));
    fragColor = vec4(c, outA) * Alpha;
}
