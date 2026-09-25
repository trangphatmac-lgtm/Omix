#version 330
uniform sampler2D MaskTexture;
uniform sampler2D OverlayTexture;
layout(std140) uniform MaskSettings {
    vec4 OutlineColor;
    vec4 MaskInfo;
};
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // The old stencil rejected every fragment inside the union of all target silhouettes.
    if (texture(MaskTexture, texCoord).a > 0.01) discard;
    vec4 overlay = texture(OverlayTexture, texCoord);
    float edge = 0.0;
    if (MaskInfo.x > 0.0) {
        vec2 pixel = MaskInfo.x / vec2(textureSize(MaskTexture, 0));
        for (int y = -1; y <= 1; ++y)
            for (int x = -1; x <= 1; ++x)
                edge = max(edge, texture(MaskTexture, texCoord + vec2(x, y) * pixel).a);
    }
    float alpha = edge * OutlineColor.a;
    fragColor = vec4(OutlineColor.rgb * alpha, alpha) + overlay * (1.0 - alpha);
}
