#version 150

// GhostParams: x = opacity, y = saturation (0 grey .. 1 true colour), z = time in seconds, w = rim strength.
// GhostAccent: rim colour of normal ghosts. GhostReplace / GhostRemove: rgb tint + mix amount (a).

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec4 GhostParams;
uniform vec4 GhostAccent;
uniform vec4 GhostReplace;
uniform vec4 GhostRemove;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightColor;
in vec2 texCoord0;
in vec3 viewPos;
in vec3 viewNormal;
flat in int ghostStyle;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }
    vec3 rgb = tex.rgb * vertexColor.rgb * lightColor.rgb;
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(luma), rgb, GhostParams.y);
    // A slight lift toward white: ghosts read as light, not as a darkened copy of the block.
    rgb = mix(rgb, vec3(1.0), 0.1);

    vec3 tint = GhostAccent.rgb;
    float tintMix = 0.0;
    if (ghostStyle == 1) {
        tint = GhostReplace.rgb;
        tintMix = GhostReplace.a;
    } else if (ghostStyle == 2) {
        // Diagonal screen-space hatching that slowly drifts: "this goes away".
        float band = fract((gl_FragCoord.x + gl_FragCoord.y) / 12.0 - GhostParams.z * 0.5);
        tint = GhostRemove.rgb;
        tintMix = GhostRemove.a + (band < 0.5 ? 0.3 : 0.0);
    }
    rgb = mix(rgb, tint, clamp(tintMix, 0.0, 1.0));

    // Soft rim toward grazing angles: the ghost reads as a glassy hologram, not a pasted texture.
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPos);
    float rim = 1.0 - clamp(abs(dot(n, v)), 0.0, 1.0);
    rim = rim * rim * rim;
    rgb = mix(rgb, tint, rim * GhostParams.w);

    float alpha = tex.a * vertexColor.a * GhostParams.x;
    alpha = min(1.0, alpha + rim * GhostParams.w * 0.35 * vertexColor.a);
    fragColor = linear_fog(vec4(rgb, alpha), vertexDistance, FogStart, FogEnd, FogColor) * ColorModulator;
}
