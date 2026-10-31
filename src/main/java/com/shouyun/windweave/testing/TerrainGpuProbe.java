package com.shouyun.windweave.testing;

import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import com.shouyun.windweave.client.WindAnimationClock;
import net.minecraft.world.phys.Vec3;

/** Reads transformed positions from the exact linked terrain vertex shader, on the actual GPU.
 * Excluded from the player JAR. No duplicate CPU implementation of the deformation formulas. */
final class TerrainGpuProbe {
    private TerrainGpuProbe() {}

    static String verify(ShaderInstance terrain) {
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int oldVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        int oldIndexedBuffer = GL30.glGetIntegeri(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING, 0);
        boolean discard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        int program = GL20.glCreateProgram(), vao = GL30.glGenVertexArrays(), buffer = GL15.glGenBuffers();
        StringBuilder report = new StringBuilder();
        try {
            int[] count = new int[1], attached = new int[2];
            GL20.glGetAttachedShaders(terrain.getId(), count, attached);
            int vertexShader = 0;
            for (int i = 0; i < count[0]; i++) {
                if (GL20.glGetShaderi(attached[i], GL20.GL_SHADER_TYPE) == GL20.GL_VERTEX_SHADER) vertexShader = attached[i];
            }
            require(vertexShader != 0, "Terrain program must expose its linked vertex shader");
            GL20.glAttachShader(program, vertexShader);
            GL30.glTransformFeedbackVaryings(program, new CharSequence[]{"gl_Position"}, GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(program);
            require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, "GPU probe linked: " + GL20.glGetProgramInfoLog(program));
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, buffer);
            GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 16, GL15.GL_DYNAMIC_READ);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, buffer);
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ModelViewMat"), false, identity);
            GL20.glUniformMatrix4fv(GL20.glGetUniformLocation(program, "ProjMat"), false, identity);
            GL20.glUniform3f(GL20.glGetUniformLocation(program, "ChunkOffset"), 0, 0, 0);
            GL20.glUniform3f(GL20.glGetUniformLocation(program, "WindCamera"), 0, 0, 0);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "WindTime"), 3.2F);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "WindTravel"), 0, 0);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "WindShelter"), 1);
            GL20.glUniform3f(GL20.glGetUniformLocation(program, "WindEffects"), 1, 1, 1);
            int position = GL20.glGetAttribLocation(program, "Position");
            GL20.glVertexAttrib3f(position, 1, 1, 1);

            check(report, unmoved(probe(program, 0, 31, 15, 12)), "GPU leaves unmarked terrain fixed");
            check(report, unmoved(probe(program, 1, 0, 15, 12)), "GPU keeps grass roots anchored");
            check(report, probe(program, 1, 31, 15, 12)[0] > 1.04F, "GPU bends grass tips downwind");
            check(report, probe(program, 1, 31, 15, -12)[0] < .96F, "GPU reverses grass bend with the wind");
            check(report, !unmoved(probe(program, 2, 31, 15, 12)), "GPU animates leaf vertices");
            check(report, probe(program, 3, 31, 15, 12)[0] > 1.1F, "GPU leans fire geometry downwind");
            check(report, Math.abs(probe(program, 4, 31, 15, 12)[1] - 1) > .001F, "GPU raises and lowers water surface vertices");
            for (int kind = 1; kind <= 4; kind++) {
                require(unmoved(probe(program, kind, 31, 15, 0)), "Calm must stop terrain kind " + kind);
                require(unmoved(probe(program, kind, 31, 0, 12)), "Shelter must stop terrain kind " + kind);
            }
            report.append("PASS: GPU calm and full shelter stop all four terrain kinds\n");
            float fullBend = probe(program, 1, 31, 15, 12)[0] - 1;
            for (int exposure = 0; exposure <= 31; exposure++) {
                int packed = exposure | exposure << 5 | exposure << 10 | exposure << 15;
                float bend = capture(program, 1, 31, packed)[0] - 1;
                require(Math.abs(bend / fullBend - exposure / 31F) < .0001F, "Fractional shelter must remain fractional on the GPU");
            }
            report.append("PASS: GPU preserves all 32 graded shelter levels\n");
            verifyFrameContinuity(program, report);
            GL20.glUniform3f(GL20.glGetUniformLocation(program, "WindEffects"), 0, 0, 0);
            for (int kind = 1; kind <= 4; kind++) require(unmoved(probe(program, kind, 31, 15, 12)), "Visual switch must stop terrain kind " + kind);
            report.append("PASS: GPU visual switches disable all four terrain kinds\n");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "GPU probe must leave no OpenGL error");
            return report.toString();
        } finally {
            if (!discard) GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, oldIndexedBuffer);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, oldBuffer);
            GL30.glBindVertexArray(oldVao);
            GL20.glUseProgram(oldProgram);
            GL15.glDeleteBuffers(buffer);
            GL30.glDeleteVertexArrays(vao);
            GL20.glDeleteProgram(program);
        }
    }

    private static float[] probe(int program, int kind, int weight, int mask, float speed) {
        GL20.glUniform2f(GL20.glGetUniformLocation(program, "Wind"), speed, 0);
        int packed = 0;
        for (int direction = 0; direction < 4; direction++) if ((mask & (1 << direction)) != 0) packed |= 31 << (direction * 5);
        return capture(program, kind, weight, packed);
    }

    private static float[] capture(int program, int kind, int weight, int exposure) {
        int light = GL20.glGetAttribLocation(program, "UV2");
        GL30.glVertexAttribI2i(light, kind == 0 ? 240 : 0x4000 | weight << 8, 240 | kind << 12);
        GL20.glVertexAttrib3f(GL20.glGetAttribLocation(program, "Normal"),
                (exposure & 127) / 127F, ((exposure >> 7) & 127) / 127F, ((exposure >> 14) & 127) / 127F);
        GL30.glBeginTransformFeedback(GL11.GL_POINTS);
        GL11.glDrawArrays(GL11.GL_POINTS, 0, 1);
        GL30.glEndTransformFeedback();
        float[] output = new float[4];
        GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, output);
        return output;
    }

    private static void verifyFrameContinuity(int program, StringBuilder report) {
        var clock = new WindAnimationClock();
        long start = 1_000_000_000L;
        clock.advance(start, false, new Vec3(12, 0, 0));
        for (int i = 1; i <= 40000; i++) clock.advance(start + i * 50_000_000L, false, new Vec3(12, 0, 0));
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "WindCamera"), 10000, 0, 10000);
        float[][] previous = new float[4][];
        double[] maxJump = new double[4];
        int changed = 0;
        for (int frame = 0; frame < 240; frame++) {
            double angle = frame < 60 ? .08 : frame < 120 ? -.08 : frame < 180 ? .12 : 0;
            double speed = frame < 120 ? 13 : 11;
            Vec3 target = new Vec3(Math.cos(angle) * speed, 0, Math.sin(angle) * speed);
            clock.advance(start + 2_000_000_000_000L + Math.round((frame + 1) * 1E9 / 120), false, target);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "WindTime"), (float) clock.seconds());
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "WindTravel"), (float) clock.travelX(), (float) clock.travelZ());
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "Wind"), (float) clock.wind().x, (float) clock.wind().z);
            for (int kind = 1; kind <= 4; kind++) {
                float[] p = capture(program, kind, 31, 0xFFFFF);
                if (previous[kind - 1] != null) {
                    double delta = Math.sqrt(Math.pow(p[0] - previous[kind - 1][0], 2) + Math.pow(p[1] - previous[kind - 1][1], 2)
                            + Math.pow(p[2] - previous[kind - 1][2], 2));
                    maxJump[kind - 1] = Math.max(maxJump[kind - 1], delta);
                    if (kind == 1 && delta > 1E-6) changed++;
                }
                previous[kind - 1] = p;
            }
        }
        require(changed > 230, "Animation must change at 120 render frames/sec, not hold game-tick poses");
        require(maxJump[0] < .012 && maxJump[1] < .008 && maxJump[2] < .025 && maxJump[3] < .003,
                "Gust updates must not jump GPU geometry: " + java.util.Arrays.toString(maxJump));
        report.append("PASS: GPU renders 120 Hz continuous motion after 2000 seconds and changing gusts; maximum per-frame displacement=")
                .append(java.util.Arrays.toString(maxJump)).append('\n');
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "WindCamera"), 0, 0, 0);
    }

    private static boolean unmoved(float[] p) { return Math.abs(p[0] - 1) < 1E-6 && Math.abs(p[1] - 1) < 1E-6 && Math.abs(p[2] - 1) < 1E-6; }
    private static void check(StringBuilder report, boolean value, String message) { require(value, message); report.append("PASS: ").append(message).append('\n'); }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
