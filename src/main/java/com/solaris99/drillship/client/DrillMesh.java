package com.solaris99.drillship.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The drill on the front of a drillhead: a round cutter plate with teeth on its rim, and a cone wrapped in spiral
 * flights. Local coordinates: the spin axis is +z and z = 0 is the hull's front face. Spinning it by a positive angle
 * about +z makes the flights run backwards, like an auger carrying spoil to the ship. Built once per tier.
 */
final class DrillMesh {
    static final String PLATE_TEXTURE = "exterior/drill_plate";
    static final String TOOTH_TEXTURE = "exterior/drill_tooth";
    static final String CONE_TEXTURE = "exterior/drill_steel";

    private static final float PLATE = 0.3F;
    // The cone's base leaves a ring of the plate free for the teeth.
    private static final float CONE_RADIUS = 0.8F;
    // Cone length relative to the plate's radius.
    private static final float CONE_LENGTH = 1.35F;
    private static final int FLIGHTS = 3;
    // Turns each flight makes from the base to the tip.
    private static final float TURNS = 0.5F;
    // Radius of the cone between flights, relative to the flights' edge.
    private static final float CORE = 0.6F;
    private static final float TOOTH_SPACING = 1.0F;
    private static final int SEGMENTS = 72;
    private static final int RINGS = 16;
    // The texture repeats every this many segments and rings.
    private static final int TILE = 4;

    /** Corners in counter-clockwise order seen from outside, so the quad can be drawn with back-face culling. */
    record Quad(String texture, float[] xyz, float[] uv, float nx, float ny, float nz, float shade) {
    }

    final List<Quad> quads = new ArrayList<>();
    private final float radius;
    /** From the hull's front face to the tip. */
    final float length;

    /** {@code radius} is the outermost reach of the drill. */
    DrillMesh(float radius) {
        this.radius = radius;
        this.length = PLATE + radius * CONE_LENGTH;
        plate();
        teeth();
        cone();
    }

    private void plate() {
        float[] center = {0, 0, PLATE};
        for (int s = 0; s < SEGMENTS; s++) {
            float[] backA = rim(s, 0), backB = rim(s + 1, 0), frontA = rim(s, PLATE), frontB = rim(s + 1, PLATE);
            quad(PLATE_TEXTURE, backA, backB, frontB, frontA, tiled(s, 0), 0.8F);
            quad(PLATE_TEXTURE, center, frontA, frontB, center, new float[] {
                    0.5F, 0.5F, planarU(frontA), planarV(frontA), planarU(frontB), planarV(frontB), 0.5F, 0.5F}, 0.7F);
        }
    }

    // Cutter bits on the plate's rim, tapering to a blunt point.
    private void teeth() {
        int count = Math.max(8, Math.round((float) (2 * Math.PI * radius / TOOTH_SPACING)));
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * (i + 0.5) / count;
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float r = radius - 0.2F;
            float[] base = {r * cos, r * sin, PLATE};
            float[] radial = {cos, sin, 0}, tangent = {-sin, cos, 0}, forward = {0, 0, 1};
            taperedBox(base, radial, tangent, forward, 0.2F, 0.2F, 0.55F, 0.4F, 1.0F);
        }
    }

    // The segments twist along with the flights, so each flight's edge follows a row of quads instead of a staircase.
    private void cone() {
        for (int s = 0; s < SEGMENTS; s++) {
            float shade = 0.5F + 0.5F * flight(2 * Math.PI * (s + 0.5) / SEGMENTS);
            for (int ring = 0; ring < RINGS; ring++) {
                quad(CONE_TEXTURE, conePoint(s, ring), conePoint(s + 1, ring), conePoint(s + 1, ring + 1), conePoint(s, ring + 1), tiled(s, ring), shade);
            }
        }
    }

    private float[] conePoint(int segment, int ring) {
        double baseAngle = 2 * Math.PI * segment / SEGMENTS;
        float t = (float) ring / RINGS;
        double angle = baseAngle + 2 * Math.PI * TURNS * t;
        // The flights' edge bulges slightly outwards on its way to the tip.
        float r = radius * CONE_RADIUS * (float) Math.pow(1 - t, 0.8) * (CORE + (1 - CORE) * flight(baseAngle));
        return new float[] {(float) (r * Math.cos(angle)), (float) (r * Math.sin(angle)), PLATE + (length - PLATE) * t};
    }

    /** 1 on top of a flight, 0 in the groove between flights, with steep sides. Takes the angle at the cone's base. */
    private static float flight(double baseAngle) {
        return (float) Math.min(1, Math.max(0, (Math.cos(FLIGHTS * baseAngle) - 0.45) * 4));
    }

    private float[] rim(int segment, float z) {
        double angle = 2 * Math.PI * segment / SEGMENTS;
        return new float[] {(float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)), z};
    }

    private float planarU(float[] p) {
        return 0.5F + p[0] / (2 * radius);
    }

    private float planarV(float[] p) {
        return 0.5F - p[1] / (2 * radius);
    }

    private static float[] tiled(int segment, int ring) {
        float u0 = (float) (segment % TILE) / TILE, u1 = u0 + 1.0F / TILE;
        float v1 = 1 - (float) (ring % TILE) / TILE, v0 = v1 - 1.0F / TILE;
        return new float[] {u0, v1, u1, v1, u1, v0, u0, v0};
    }

    /**
     * A box standing on {@code base}, {@code height} long along {@code forward}, whose front face is {@code taper} times
     * the size of its back face.
     */
    private void taperedBox(float[] base, float[] radial, float[] tangent, float[] forward,
            float halfRadial, float halfTangent, float height, float taper, float shade) {
        float[][] corners = new float[8][];
        for (int i = 0; i < 8; i++) {
            float scale = (i & 4) == 0 ? 1 : taper;
            float a = ((i & 1) == 0 ? -halfRadial : halfRadial) * scale;
            float b = ((i & 2) == 0 ? -halfTangent : halfTangent) * scale;
            float c = (i & 4) == 0 ? 0 : height;
            corners[i] = new float[3];
            for (int k = 0; k < 3; k++) {
                corners[i][k] = base[k] + radial[k] * a + tangent[k] * b + forward[k] * c;
            }
        }
        float[] center = new float[3];
        for (int k = 0; k < 3; k++) {
            center[k] = base[k] + forward[k] * height / 2;
        }
        // No back face: it lies on the plate.
        int[][] faces = {{4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        float[] uv = {0, 1, 1, 1, 1, 0, 0, 0};
        for (int[] face : faces) {
            float[] a = corners[face[0]], b = corners[face[1]], c = corners[face[2]], d = corners[face[3]];
            float[] normal = normal(a, b, c, d);
            float outwards = 0;
            for (int k = 0; k < 3; k++) {
                outwards += normal[k] * ((a[k] + c[k]) / 2 - center[k]);
            }
            if (outwards < 0) {
                quad(TOOTH_TEXTURE, d, c, b, a, uv, shade);
            } else {
                quad(TOOTH_TEXTURE, a, b, c, d, uv, shade);
            }
        }
    }

    private void quad(String texture, float[] a, float[] b, float[] c, float[] d, float[] uv, float shade) {
        float[] normal = normal(a, b, c, d);
        quads.add(new Quad(texture, new float[] {a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2]},
                uv, normal[0], normal[1], normal[2], shade));
    }

    // Normal of the triangle a, b, c, or of a, c, d when the first one is degenerate (the cone's tip, the plate's center).
    private static float[] normal(float[] a, float[] b, float[] c, float[] d) {
        float[] n = cross(a, b, c);
        float length = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (length < 1.0E-6F) {
            n = cross(a, c, d);
            length = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        }
        return length < 1.0E-6F ? new float[] {0, 0, 1} : new float[] {n[0] / length, n[1] / length, n[2] / length};
    }

    private static float[] cross(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        return new float[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }
}
