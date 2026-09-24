#version 150

// Slate Building placement ghost. Terrain-style vertices (DefaultVertexFormat.BLOCK) with two extras packed in:
// the ghost style rides in the high byte of UV2.x (0 place, 1 replace, 2 remove) and the per-ghost fade in the
// vertex alpha. Positions are relative to a per-draw origin; GhostOffset (origin - camera) makes them
// camera-relative, exactly like the chunk renderer's ChunkOffset, so fog distances are right.

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 GhostOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightColor;
out vec2 texCoord0;
out vec3 viewPos;
out vec3 viewNormal;
flat out int ghostStyle;

void main() {
    vec3 pos = Position + GhostOffset;
    vec4 view = ModelViewMat * vec4(pos, 1.0);
    gl_Position = ProjMat * view;

    vertexDistance = fog_distance(pos, FogShape);
    ghostStyle = UV2.x >> 8;
    lightColor = minecraft_sample_lightmap(Sampler2, ivec2(UV2.x & 255, UV2.y));
    vertexColor = Color;
    texCoord0 = UV0;
    viewPos = view.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
}
