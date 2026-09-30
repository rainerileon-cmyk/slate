#version 150

// Slate stage: one direction of a Gaussian blur (nine taps, read as five through linear filtering).

uniform sampler2D Sampler0;
uniform vec2 Step;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec3 sum = texture(Sampler0, texCoord0).rgb * 0.2270270270;
    sum += texture(Sampler0, texCoord0 + Step * 1.3846153846).rgb * 0.3162162162;
    sum += texture(Sampler0, texCoord0 - Step * 1.3846153846).rgb * 0.3162162162;
    sum += texture(Sampler0, texCoord0 + Step * 3.2307692308).rgb * 0.0702702703;
    sum += texture(Sampler0, texCoord0 - Step * 3.2307692308).rgb * 0.0702702703;
    fragColor = vec4(sum, 1.0);
}
