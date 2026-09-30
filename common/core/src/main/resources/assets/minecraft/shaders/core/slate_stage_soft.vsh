#version 150

// Slate stage: soft, stylized light for the things of a scene. Works on positions that still have to be moved by
// ModelViewMat (meshes) and on ones that already were (models posed on the CPU, where ModelViewMat is identity).

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 viewPos;
out vec3 viewNormal;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec4 p = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * p;
    viewPos = p.xyz;
    viewNormal = mat3(ModelViewMat) * Normal;
    vertexColor = Color;
    texCoord0 = UV0;
}
