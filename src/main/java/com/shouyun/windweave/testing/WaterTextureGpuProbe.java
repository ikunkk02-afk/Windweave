package com.shouyun.windweave.testing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;

/** Pixel readback of the real water fragment program, using original Minecraft frame strips. */
final class WaterTextureGpuProbe {
    private WaterTextureGpuProbe() {}

    static String verify(ShaderInstance water) throws IOException {
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), oldRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] viewport = new int[4], bindings = new int[3];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        for (int i = 0; i < 3; i++) { GL13.glActiveTexture(GL13.GL_TEXTURE0 + i); bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); }
        int[] capabilities = {GL11.GL_BLEND, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE, GL11.GL_SCISSOR_TEST};
        boolean[] enabled = new boolean[capabilities.length];
        for (int i = 0; i < capabilities.length; i++) { enabled[i] = GL11.glIsEnabled(capabilities[i]); GL11.glDisable(capabilities[i]); }
        int program = GL20.glCreateProgram(), vertex = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        int vao = GL30.glGenVertexArrays(), framebuffer = GL30.glGenFramebuffers(), color = GL11.glGenTextures();
        try {
            int[] count = new int[1], attached = new int[2];
            GL20.glGetAttachedShaders(water.getId(), count, attached);
            for (int i = 0; i < count[0]; i++) if (GL20.glGetShaderi(attached[i], GL20.GL_SHADER_TYPE) == GL20.GL_FRAGMENT_SHADER) GL20.glAttachShader(program, attached[i]);
            GL20.glShaderSource(vertex, """
                    #version 150
                    uniform vec2 TestUV;
                    uniform float TestStrength;
                    out float vertexDistance;
                    out vec4 vertexColor;
                    out vec2 texCoord0;
                    out vec3 windWorldPos;
                    out float windWaterStrength;
                    out float windWaterMaterial;
                    void main() {
                        vec2 p = gl_VertexID == 0 ? vec2(-1,-1) : gl_VertexID == 1 ? vec2(3,-1) : vec2(-1,3);
                        gl_Position = vec4(p, 0, 1);
                        vertexDistance = 0;
                        vertexColor = vec4(1);
                        texCoord0 = TestUV;
                        windWorldPos = vec3(0);
                        windWaterStrength = TestStrength;
                        windWaterMaterial = 1;
                    }
                    """);
            GL20.glCompileShader(vertex);
            require(GL20.glGetShaderi(vertex, GL20.GL_COMPILE_STATUS) != 0, GL20.glGetShaderInfoLog(vertex));
            GL20.glAttachShader(program, vertex);
            GL20.glLinkProgram(program);
            require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            require(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE, "Water readback framebuffer incomplete");
            GL11.glViewport(0, 0, 1, 1);
            var textures = Minecraft.getInstance().getTextureManager();
            int still = textures.getTexture(ResourceLocation.withDefaultNamespace("textures/block/water_still.png")).getId();
            int flow = textures.getTexture(ResourceLocation.withDefaultNamespace("textures/block/water_flow.png")).getId();
            GL13.glActiveTexture(GL13.GL_TEXTURE0); GL11.glBindTexture(GL11.GL_TEXTURE_2D, still);
            GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glBindTexture(GL11.GL_TEXTURE_2D, still);
            GL13.glActiveTexture(GL13.GL_TEXTURE2); GL11.glBindTexture(GL11.GL_TEXTURE_2D, flow);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "Sampler0"), 0);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "WindStillFrames"), 1);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "WindFlowFrames"), 2);
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "ColorModulator"), 1, 1, 1, 1);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "FogStart"), 10);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "FogEnd"), 100);
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "WindFlowRect"), 0, 0, 1, 1);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "TestStrength"), 0);
            StringBuilder report = new StringBuilder();
            for (int mode = 0; mode < 2; mode++) {
                GL20.glUniform4f(GL20.glGetUniformLocation(program, "WindStillRect"), mode == 0 ? 0 : -2, mode == 0 ? 0 : -2, 1, 1);
                float maxChange = 0;
                String diagnostic = "";
                float duration = mode == 0 ? .1F : .05F;
                int point = 0;
                for (float[] uv : changedTexels(mode == 0 ? "water_still" : "water_flow")) {
                    GL20.glUniform2f(GL20.glGetUniformLocation(program, "TestUV"), uv[0], uv[1]);
                    float[] a = pixel(program, 0), b = pixel(program, duration);
                    if (point == 0) diagnostic = java.util.Arrays.toString(a) + " -> " + java.util.Arrays.toString(b);
                    point++;
                    for (int channel = 0; channel < 4; channel++) maxChange = Math.max(maxChange, Math.abs(a[channel] - b[channel]));
                    for (int step = 1; step < 8; step++) {
                        float alpha = step / 8F;
                        float[] p = pixel(program, duration * alpha);
                        for (int channel = 0; channel < 4; channel++) require(Math.abs(p[channel] - (a[channel] * (1 - alpha) + b[channel] * alpha)) < .0041F,
                                "Water frames must blend continuously at sub-tick times");
                    }
                }
                require(maxChange > .003F, "Water frame strips must provide distinct animation frames: mode=" + mode + " textures=" + still + "," + flow + " pixels=" + diagnostic);
                report.append("PASS: GPU pixel readback confirms continuous sub-tick blending of ").append(mode == 0 ? "still" : "flowing").append(" water texture frames\n");
            }
            GL20.glUniform4f(GL20.glGetUniformLocation(program, "WindStillRect"), 0, 0, 1, 1);
            float motionDifference = 0;
            for (float[] uv : changedTexels("water_still")) {
                GL20.glUniform2f(GL20.glGetUniformLocation(program, "TestUV"), uv[0], uv[1]);
                GL20.glUniform1f(GL20.glGetUniformLocation(program, "TestStrength"), 1);
                GL20.glUniform2f(GL20.glGetUniformLocation(program, "WindTravel"), 0, 0);
                float[] a = pixel(program, 0);
                GL20.glUniform2f(GL20.glGetUniformLocation(program, "WindTravel"), 2, 0);
                float[] b = pixel(program, 0);
                for (int channel = 0; channel < 3; channel++) motionDifference += Math.abs(a[channel] - b[channel]);
                GL20.glUniform1f(GL20.glGetUniformLocation(program, "TestStrength"), 0);
                float[] calmA = pixel(program, 0);
                GL20.glUniform2f(GL20.glGetUniformLocation(program, "WindTravel"), 0, 0);
                float[] calmB = pixel(program, 0);
                for (int channel = 0; channel < 4; channel++) require(Math.abs(calmA[channel] - calmB[channel]) < .001F,
                        "Sheltered/calm water must not acquire wind UV drift");
            }
            motionDifference /= changedTexels("water_still").size() * 3F;
            require(motionDifference > .015F, "Wind must visibly move the water pattern with texture-frame time frozen: " + motionDifference);
            report.append("PASS: Wind moves water texture independently of vanilla frame animation; mean RGB displacement=").append(motionDifference).append('\n');
            report.append("PASS: Sheltered/calm water has no wind-driven texture translation\n");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Water pixel test left an OpenGL error");
            return report.toString();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDraw);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldRead);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            for (int i = 0; i < 3; i++) { GL13.glActiveTexture(GL13.GL_TEXTURE0 + i); GL11.glBindTexture(GL11.GL_TEXTURE_2D, bindings[i]); }
            GL13.glActiveTexture(oldActive);
            for (int i = 0; i < capabilities.length; i++) if (enabled[i]) GL11.glEnable(capabilities[i]);
            GL20.glUseProgram(oldProgram);
            GL30.glBindVertexArray(oldVao);
            GL20.glDeleteProgram(program); GL20.glDeleteShader(vertex);
            GL30.glDeleteVertexArrays(vao); GL30.glDeleteFramebuffers(framebuffer); GL11.glDeleteTextures(color);
        }
    }

    private static float[] pixel(int program, float time) {
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "WindTime"), time);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        float[] rgba = new float[4];
        GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, rgba);
        return rgba;
    }
    private static java.util.List<float[]> changedTexels(String name) throws IOException {
        var result = new java.util.ArrayList<float[]>();
        var location = ResourceLocation.withDefaultNamespace("textures/block/" + name + ".png");
        try (var stream = Minecraft.getInstance().getResourceManager().open(location); var image = NativeImage.read(stream)) {
            int width = image.getWidth();
            for (int y = 0; y < width && result.size() < 9; y++) for (int x = 0; x < width && result.size() < 9; x++) {
                int a = image.getPixelRGBA(x, y), b = image.getPixelRGBA(x, y + width);
                int difference = 0;
                for (int shift = 0; shift < 32; shift += 8) difference = Math.max(difference, Math.abs(((a >> shift) & 255) - ((b >> shift) & 255)));
                if (difference >= 4) result.add(new float[]{(x + .5F) / width, (y + .5F) / width});
            }
        }
        require(!result.isEmpty(), "Source water animation must have changing texels");
        return result;
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
