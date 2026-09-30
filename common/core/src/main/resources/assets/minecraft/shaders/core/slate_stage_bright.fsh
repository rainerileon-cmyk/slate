#version 150

// Slate stage: keeps what is bright in a scene, to be blurred into its glow.

uniform sampler2D Sampler0;
uniform float Threshold;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, texCoord0);
    float peak = max(c.r, max(c.g, c.b));
    float keep = smoothstep(Threshold, Threshold + 0.3, peak);
    fragColor = vec4(c.rgb * keep, 1.0);
}
