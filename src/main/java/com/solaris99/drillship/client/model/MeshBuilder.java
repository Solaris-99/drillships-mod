package com.solaris99.drillship.client.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects textured quads for the procedural ship models. Every face is cut along a 1-block grid in its own plane and
 * each cell gets the whole texture, so textures keep the world's 16 pixels per block whatever the size of the part.
 * Pure geometry, no Minecraft classes, so models can be previewed outside the game.
 */
public final class MeshBuilder {
    /** Corners in counter-clockwise order seen from outside. Degenerate quads (two equal corners) are triangles. */
    public record Quad(String texture, float[] xyz, float[] uv, float nx, float ny, float nz, float shade, boolean emissive) {
    }

    /** Suffix for textures drawn as 2x2-block panels. */
    public static final String BIG = "@2x2";

    public final List<Quad> quads = new ArrayList<>();
    private Affine transform = Affine.IDENTITY;
    private float shade = 1.0F;
    private boolean emissive;
    private boolean fit;

    public MeshBuilder shade(float shade) {
        this.shade = shade;
        return this;
    }

    /** While on, each face shows its texture once, stretched over the face, instead of once per block. For small parts. */
    public MeshBuilder fit(boolean fit) {
        this.fit = fit;
        return this;
    }

    public MeshBuilder emissive(boolean emissive) {
        this.emissive = emissive;
        return this;
    }

    /** Runs {@code body} with {@code affine} applied on top of the current transform. */
    public MeshBuilder with(Affine affine, Runnable body) {
        Affine previous = transform;
        transform = previous.then(affine);
        try {
            body.run();
        } finally {
            transform = previous;
        }
        return this;
    }

    // Planar faces

    /**
     * A convex planar polygon, counter-clockwise seen from outside. The texture's u runs along {@code uAxis} and v
     * along {@code vAxis} (both unit, in the plane; v points down the texture), with one repeat per block counted from
     * {@code origin} and shifted by {@code uOffset} / {@code vOffset}.
     */
    public MeshBuilder polygon(String texture, float[][] corners, float[] uAxis, float[] vAxis, float[] origin, float uOffset, float vOffset) {
        int n = corners.length;
        double[][] uv = new double[n][2];
        double minU = Double.MAX_VALUE, minV = Double.MAX_VALUE, maxU = -Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float[] d = sub(corners[i], origin);
            uv[i][0] = dot(d, uAxis) + uOffset;
            uv[i][1] = dot(d, vAxis) + vOffset;
            minU = Math.min(minU, uv[i][0]);
            maxU = Math.max(maxU, uv[i][0]);
            minV = Math.min(minV, uv[i][1]);
            maxV = Math.max(maxV, uv[i][1]);
        }
        float[] normal = normal(corners);
        if (fit) {
            List<double[]> whole = new ArrayList<>();
            double spanU = Math.max(1.0E-6, maxU - minU), spanV = Math.max(1.0E-6, maxV - minV);
            for (int i = 0; i < n; i++) {
                whole.add(new double[] {corners[i][0], corners[i][1], corners[i][2], (uv[i][0] - minU) / spanU, (uv[i][1] - minV) / spanV});
            }
            emitFan(texture, whole, 0, 0, normal);
            return this;
        }
        for (int cu = (int) Math.floor(minU + 1.0E-4); cu < maxU - 1.0E-4; cu++) {
            for (int cv = (int) Math.floor(minV + 1.0E-4); cv < maxV - 1.0E-4; cv++) {
                List<double[]> cell = clip(corners, uv, cu, cv);
                if (cell.size() >= 3) {
                    emitFan(texture, cell, cu, cv, normal);
                }
            }
        }
        return this;
    }

    /**
     * A polygon whose texture is upright as seen from outside: v points down the face, or towards the back on flat
     * faces. The grid starts at the first corner.
     */
    public MeshBuilder face(String texture, float[]... corners) {
        float[] normal = normal(corners);
        float[] up = Math.abs(normal[1]) > 0.9F ? new float[] {0, 0, 1} : new float[] {0, 1, 0};
        float[] uAxis = normalize(cross(up, normal));
        float[] vAxis = normalize(cross(uAxis, normal));
        return polygon(texture, corners, uAxis, vAxis, corners[0], 0, 0);
    }

    /** An axis-aligned box; {@code faces} is a string of the faces to draw out of "NSEWUD" (z-, z+, x+, x-, y+, y-). */
    public MeshBuilder box(String texture, float x0, float y0, float z0, float x1, float y1, float z1, String faces) {
        if (faces.indexOf('S') >= 0) face(texture, p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1));
        if (faces.indexOf('N') >= 0) face(texture, p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0));
        if (faces.indexOf('E') >= 0) face(texture, p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1));
        if (faces.indexOf('W') >= 0) face(texture, p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0));
        if (faces.indexOf('U') >= 0) face(texture, p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0), p(x0, y1, z0));
        if (faces.indexOf('D') >= 0) face(texture, p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1));
        return this;
    }

    public MeshBuilder box(String texture, float x0, float y0, float z0, float x1, float y1, float z1) {
        return box(texture, x0, y0, z0, x1, y1, z1, "NSEWUD");
    }

    // Shapes built along the z axis from a convex profile in the xy plane (counter-clockwise seen from +z)

    /** The side walls between two profiles with the same number of corners, at {@code zBack} and {@code zFront}. */
    public MeshBuilder loft(String texture, float[][] back, float zBack, float[][] front, float zFront) {
        int n = back.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float[] a = p(back[i][0], back[i][1], zBack), b = p(back[j][0], back[j][1], zBack);
            float[] c = p(front[j][0], front[j][1], zFront), d = p(front[i][0], front[i][1], zFront);
            if (dist(a, b) < 1.0E-4F && dist(c, d) < 1.0E-4F) {
                continue;
            }
            // Counter-clockwise from outside for a profile that is counter-clockwise seen from +z.
            if (dist(a, b) < 1.0E-4F) {
                face(texture, a, c, d);
            } else if (dist(c, d) < 1.0E-4F) {
                face(texture, a, b, c);
            } else {
                face(texture, a, b, c, d);
            }
        }
        return this;
    }

    /** A flat cap at {@code z} facing +z ({@code front}) or -z. */
    public MeshBuilder cap(String texture, float[][] profile, float z, boolean front) {
        int n = profile.length;
        float[][] corners = new float[n][];
        for (int i = 0; i < n; i++) {
            float[] q = profile[front ? i : n - 1 - i];
            corners[i] = p(q[0], q[1], z);
        }
        float[] uAxis = front ? new float[] {1, 0, 0} : new float[] {-1, 0, 0};
        return polygon(texture, corners, uAxis, new float[] {0, -1, 0}, new float[] {0, 0, z}, 0, 0);
    }

    /** A cylinder along z centred on (cx, cy). The texture runs around it, one repeat per block of circumference. */
    public MeshBuilder cylinder(String texture, String capTexture, float cx, float cy, float radius, float z0, float z1, int segments, boolean capBack, boolean capFront) {
        float[][] ring = circle(cx, cy, radius, segments);
        float arc = (float) (2 * Math.PI * radius / segments);
        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % segments;
            float[] a = p(ring[i][0], ring[i][1], z0), b = p(ring[j][0], ring[j][1], z0);
            float[] c = p(ring[j][0], ring[j][1], z1), d = p(ring[i][0], ring[i][1], z1);
            float[] edge = normalize(sub(b, a));
            polygon(texture, new float[][] {a, b, c, d}, edge, new float[] {0, 0, -1}, a, (i * arc) % 1, -(z1 - (float) Math.floor(z1)));
        }
        if (capBack) cap(capTexture, ring, z0, false);
        if (capFront) cap(capTexture, ring, z1, true);
        return this;
    }

    public static float[][] circle(float cx, float cy, float radius, int segments) {
        float[][] ring = new float[segments][];
        for (int i = 0; i < segments; i++) {
            double a = 2 * Math.PI * i / segments;
            ring[i] = new float[] {cx + radius * (float) Math.cos(a), cy + radius * (float) Math.sin(a)};
        }
        return ring;
    }

    /**
     * Moves every edge of a convex profile inwards by its own distance and returns the corners where the moved edges
     * meet. Edge i runs from corner i to corner i + 1.
     */
    public static float[][] inset(float[][] profile, float[] distances) {
        int n = profile.length;
        double[][] lines = new double[n][3];
        for (int i = 0; i < n; i++) {
            float[] a = profile[i], b = profile[(i + 1) % n];
            double dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy);
            // Inward normal of a counter-clockwise polygon.
            double nx = -dy / len, ny = dx / len;
            lines[i] = new double[] {nx, ny, nx * a[0] + ny * a[1] + distances[i]};
        }
        float[][] result = new float[n][];
        for (int i = 0; i < n; i++) {
            double[] l1 = lines[(i + n - 1) % n], l2 = lines[i];
            double det = l1[0] * l2[1] - l1[1] * l2[0];
            if (Math.abs(det) < 1.0E-9) {
                result[i] = new float[] {profile[i][0] + (float) (l2[0] * distances[i]), profile[i][1] + (float) (l2[1] * distances[i])};
            } else {
                result[i] = new float[] {(float) ((l1[2] * l2[1] - l1[1] * l2[2]) / det), (float) ((l1[0] * l2[2] - l1[2] * l2[0]) / det)};
            }
        }
        return result;
    }

    // Internals

    private List<double[]> clip(float[][] corners, double[][] uv, int cu, int cv) {
        List<double[]> poly = new ArrayList<>();
        for (int i = 0; i < corners.length; i++) {
            poly.add(new double[] {corners[i][0], corners[i][1], corners[i][2], uv[i][0], uv[i][1]});
        }
        poly = clipAxis(poly, 3, cu, true);
        poly = clipAxis(poly, 3, cu + 1, false);
        poly = clipAxis(poly, 4, cv, true);
        poly = clipAxis(poly, 4, cv + 1, false);
        return poly;
    }

    // Sutherland-Hodgman against one grid line: keeps the side >= limit (keepAbove) or <= limit.
    private static List<double[]> clipAxis(List<double[]> poly, int axis, double limit, boolean keepAbove) {
        List<double[]> out = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            boolean inA = keepAbove ? a[axis] >= limit : a[axis] <= limit;
            boolean inB = keepAbove ? b[axis] >= limit : b[axis] <= limit;
            if (inA) {
                out.add(a);
            }
            if (inA != inB) {
                double t = (limit - a[axis]) / (b[axis] - a[axis]);
                double[] m = new double[5];
                for (int k = 0; k < 5; k++) {
                    m[k] = a[k] + (b[k] - a[k]) * t;
                }
                out.add(m);
            }
        }
        return out;
    }

    private void emitFan(String texture, List<double[]> cell, int cu, int cv, float[] normal) {
        int n = cell.size();
        for (int k = 1; k + 1 < n; k += 2) {
            double[] a = cell.get(0), b = cell.get(k), c = cell.get(k + 1), d = cell.get(Math.min(k + 2, n - 1));
            if (k + 2 >= n) {
                d = c;
            }
            emit(texture, new double[][] {a, b, c, d}, cu, cv, normal);
        }
    }

    // A texture id ending in "@2x2" is a 2x2-block panel split into four textures, _00 to _11, picked by the cell.
    private void emit(String texture, double[][] corners, int cu, int cv, float[] normal) {
        if (texture.endsWith(BIG)) {
            texture = texture.substring(0, texture.length() - BIG.length()) + "_" + Math.floorMod(cu, 2) + Math.floorMod(cv, 2);
        }
        float[] xyz = new float[12];
        float[] uv = new float[8];
        for (int i = 0; i < 4; i++) {
            float[] world = transform.apply(new float[] {(float) corners[i][0], (float) corners[i][1], (float) corners[i][2]});
            System.arraycopy(world, 0, xyz, i * 3, 3);
            uv[i * 2] = clamp01((float) (corners[i][3] - cu));
            uv[i * 2 + 1] = clamp01((float) (corners[i][4] - cv));
        }
        float[] n = normalize(transform.applyDirection(normal));
        quads.add(new Quad(texture, xyz, uv, n[0], n[1], n[2], shade, emissive));
    }

    private static float clamp01(float value) {
        return Math.max(0, Math.min(1, value));
    }

    // Vector helpers

    public static float[] p(float x, float y, float z) {
        return new float[] {x, y, z};
    }

    static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    static float[] scale(float[] a, float s) {
        return new float[] {a[0] * s, a[1] * s, a[2] * s};
    }

    static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float dist(float[] a, float[] b) {
        float[] d = sub(a, b);
        return (float) Math.sqrt(dot(d, d));
    }

    static float[] normalize(float[] a) {
        float len = (float) Math.sqrt(dot(a, a));
        return len < 1.0E-9F ? new float[] {0, 0, 1} : scale(a, 1 / len);
    }

    // Newell's method: robust for polygons with repeated corners.
    static float[] normal(float[][] corners) {
        float nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < corners.length; i++) {
            float[] a = corners[i], b = corners[(i + 1) % corners.length];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        return normalize(new float[] {nx, ny, nz});
    }

    /** A rotation (3x3, row-major) followed by a translation. */
    public record Affine(float[] m, float[] t) {
        public static final Affine IDENTITY = new Affine(new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, new float[] {0, 0, 0});

        public static Affine translate(float x, float y, float z) {
            return new Affine(IDENTITY.m, new float[] {x, y, z});
        }

        /** Rotation by {@code radians} about an axis through {@code pivot}. */
        public static Affine rotate(float[] axis, float radians, float[] pivot) {
            float[] k = normalize(axis);
            float c = (float) Math.cos(radians), s = (float) Math.sin(radians), v = 1 - c;
            float[] m = {
                    k[0] * k[0] * v + c, k[0] * k[1] * v - k[2] * s, k[0] * k[2] * v + k[1] * s,
                    k[1] * k[0] * v + k[2] * s, k[1] * k[1] * v + c, k[1] * k[2] * v - k[0] * s,
                    k[2] * k[0] * v - k[1] * s, k[2] * k[1] * v + k[0] * s, k[2] * k[2] * v + c};
            float[] rotatedPivot = new Affine(m, new float[3]).apply(pivot);
            return new Affine(m, sub(pivot, rotatedPivot));
        }

        /** Builds the transform from a frame: its origin and its right, up and forward axes. */
        public static Affine frame(float[] origin, float[] right, float[] up, float[] forward) {
            return new Affine(new float[] {right[0], up[0], forward[0], right[1], up[1], forward[1], right[2], up[2], forward[2]}, origin);
        }

        public float[] apply(float[] p) {
            float[] r = applyDirection(p);
            return new float[] {r[0] + t[0], r[1] + t[1], r[2] + t[2]};
        }

        public float[] applyDirection(float[] d) {
            return new float[] {
                    m[0] * d[0] + m[1] * d[1] + m[2] * d[2],
                    m[3] * d[0] + m[4] * d[1] + m[5] * d[2],
                    m[6] * d[0] + m[7] * d[1] + m[8] * d[2]};
        }

        /** This transform applied after {@code inner}: p -> this(inner(p)). */
        public Affine then(Affine inner) {
            float[] m2 = new float[9];
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    m2[r * 3 + c] = m[r * 3] * inner.m[c] + m[r * 3 + 1] * inner.m[3 + c] + m[r * 3 + 2] * inner.m[6 + c];
                }
            }
            return new Affine(m2, apply(inner.t));
        }
    }
}
