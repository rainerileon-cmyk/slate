#version 150

// Slate stage: soft, stylized light. A warm key light that wraps round the form instead of cutting it in half, a cool
// fill from the other side, a line of light along the edges that turn away, and an ambient floor so nothing goes black.
// All vectors are in view space.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec3 KeyPos;
uniform vec3 KeyColor;
uniform vec3 FillDir;
uniform vec3 FillColor;
uniform vec3 Ambient;
uniform vec3 RimColor;
uniform float RimStrength;
uniform float Wrap;
uniform float Muted;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec3 viewPos;
in vec3 viewNormal;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }
    vec3 n = normalize(viewNormal);
    vec3 toKey = normalize(KeyPos - viewPos);
    float key = clamp((dot(n, toKey) + Wrap) / (1.0 + Wrap), 0.0, 1.0);
    key = key * key * (3.0 - 2.0 * key);
    float fill = clamp(dot(n, normalize(FillDir)) * 0.5 + 0.5, 0.0, 1.0);
    vec3 toEye = normalize(-viewPos);
    float edge = 1.0 - clamp(dot(n, toEye), 0.0, 1.0);
    float rim = edge * edge * edge * RimStrength;

    vec3 light = Ambient + KeyColor * key + FillColor * fill;
    vec3 c = tex.rgb * vertexColor.rgb * light + RimColor * rim * (0.35 + 0.65 * key);
    // Muted: what is there but out of reach keeps its form and loses its colour and half its light.
    float grey = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(c, vec3(grey) * vec3(0.6, 0.59, 0.57), Muted);
    // Distance: what is far goes over into the colour of the air. A stage without fog keeps start and end alike.
    float far = FogEnd > FogStart ? clamp((length(viewPos) - FogStart) / (FogEnd - FogStart), 0.0, 1.0) : 0.0;
    c = mix(c, FogColor.rgb, far * far * (3.0 - 2.0 * far) * FogColor.a);
    fragColor = vec4(c, tex.a * vertexColor.a) * ColorModulator;
}
