#version 330
uniform sampler2D Source;
layout(std140) uniform BlurSettings { vec2 Direction; float Radius; float Padding; };
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 color = texture(Source, texCoord + Direction * Radius).rgb;
    for (float r = -Radius + 0.5; r <= Radius - 1.5; r += 2.0) {
        color += texture(Source, texCoord + Direction * r).rgb * 2.0;
    }
    fragColor = vec4(color / (Radius * 2.0 + 1.0), 1.0);
}
