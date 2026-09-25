#version 330
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    fragColor = vec4(texture(Sampler0, texCoord0).rgb * vertexColor.rgb, vertexColor.a);
}
