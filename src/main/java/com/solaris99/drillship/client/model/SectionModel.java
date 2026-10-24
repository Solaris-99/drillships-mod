package com.solaris99.drillship.client.model;

import javax.annotation.Nullable;

import com.solaris99.drillship.client.model.MeshBuilder.Affine;
import com.solaris99.drillship.ship.TeslaTurret;

/**
 * The exterior of a drillhead or a wagon, built from its size so every tier (and longer sections later) gets the same
 * design. Local coordinates are the renderer's: y up from the bottom, z forward, origin at the bottom centre, and x
 * towards the ship's <em>left</em> (ShipShape.Frame's x is mirrored: it points right).
 * Everything stays inside the tunnel the drill cuts (|x| <= width / 2, 0 <= y <= height): the body is inset a little
 * so tracks, door, rails and lamps have room around it, and nothing on the roof rises above the tunnel's ceiling.
 * <p>
 * Parts map to the interior: the door is where the interior door is, the periscope sits above the helm, the exhaust
 * stack above the generator, and the coupling mouths wrap the passage to the next section.
 */
public final class SectionModel {
    public static final String PLATE = "exterior/plate" + MeshBuilder.BIG;
    public static final String ROOF = "exterior/roof";
    public static final String TRIM = "exterior/trim";
    public static final String DRILL_STEEL = "exterior/drill_steel";
    public static final String BELLY = "exterior/belly";
    public static final String TRACK_BELT = "exterior/track_belt";
    public static final String TRACK_SIDE = "exterior/track_side";
    public static final String WHEEL = "exterior/wheel";
    public static final String HAZARD = "exterior/hazard";
    public static final String DOOR_LOWER = "exterior/door_lower";
    public static final String DOOR_UPPER = "exterior/door_upper";
    public static final String GLASS = "exterior/glass";
    public static final String LAMP = "exterior/lamp";
    public static final String LAMP_ON = "exterior/lamp_on";
    public static final String BEACON = "exterior/beacon";
    public static final String BEACON_ON = "exterior/beacon_on";
    public static final String PIPE = "exterior/pipe";
    public static final String VENT = "exterior/vent";
    public static final String HATCH = "exterior/hatch";
    public static final String BELLOWS = "exterior/bellows";
    public static final String SHUTTER = "exterior/shutter";
    public static final String SOOT = "exterior/soot";

    private static final float SIDE_INSET = 0.125F;
    private static final float ROOF_INSET = 0.375F;
    private static final float BEVEL = 1.0F;
    // How far the drill's housing reaches back from the hull's front face.
    private static final float SHROUD = 0.5F;
    private static final float TRACK_HEIGHT = 1.0F;
    private static final float TRACK_END_RADIUS = 0.5F;
    private static final float TRACK_END_GAP = 0.4F;
    private static final float BELLY_BOTTOM = 0.25F;
    // The interior door: 2 blocks above the floor, in the third block from the back of the room, on the right.
    private static final float DOOR_FROM_BACK = 2.0F;
    // The ship's right side, where the door and the generator are: -x here.
    private static final int RIGHT = -1;

    /**
     * Sizes of one section. {@code taper} is how much the ends narrow over their last {@code taper} blocks, so
     * coupled sections can bend without touching. The passage is the interior opening to the next section; the
     * coupling mouths and bellows wrap it.
     */
    public record Spec(boolean drillhead, int width, int height, int length, float back, float taper, float trackWidth,
            int passageWidth, int passageHeight) {
        public float front() {
            return back + length;
        }

        float halfWidth() {
            return width / 2.0F;
        }

        float bodyHalfWidth() {
            return halfWidth() - SIDE_INSET;
        }

        float roof() {
            return height - ROOF_INSET;
        }

        /** Where the body ends at the front: the drill's housing on a drillhead, the coupling taper on a wagon. */
        float bodyFront() {
            return drillhead ? front() - SHROUD : front() - taper;
        }

        float trackFront() {
            return drillhead ? front() - SHROUD - 0.05F : front() - TRACK_END_GAP;
        }
    }

    /** The opening of a coupling, in section-local coordinates: the rectangle the bellows are attached to. */
    public record Mouth(float z, float halfWidth, float bottom, float top) {
    }

    public static Mouth backMouth(Spec spec) {
        return mouth(spec, spec.back());
    }

    public static Mouth frontMouth(Spec spec) {
        return mouth(spec, spec.front());
    }

    // The passage plus a margin, kept inside the narrowed end face.
    private static Mouth mouth(Spec spec, float z) {
        float halfWidth = Math.min(spec.bodyHalfWidth() - spec.taper() - 0.1F, spec.passageWidth() / 2.0F + 0.6F);
        float top = Math.min(spec.roof() - spec.taper() - 0.1F, TRACK_HEIGHT + spec.passageHeight() + 0.6F);
        return new Mouth(z, halfWidth, TRACK_HEIGHT + 0.1F, top);
    }

    // Static parts: built once per tier and section type.

    public static MeshBuilder buildStatic(Spec spec) {
        MeshBuilder mesh = new MeshBuilder();
        body(mesh, spec);
        belly(mesh, spec);
        for (int side = -1; side <= 1; side += 2) {
            trackFrame(mesh, spec, side);
        }
        doorFrame(mesh, spec);
        sideDetails(mesh, spec);
        roofRails(mesh, spec);
        couplingFrame(mesh, spec, spec.back(), false);
        if (spec.drillhead()) {
            shroud(mesh, spec);
            periscope(mesh, spec);
            exhaust(mesh, spec);
            lampHousings(mesh, spec);
        } else {
            couplingFrame(mesh, spec, spec.front(), true);
            wagonDetails(mesh, spec);
        }
        return mesh;
    }

    // Upper body: rectangle with bevelled top edges, sitting on the tracks.
    static float[][] profile(Spec spec) {
        float hb = spec.bodyHalfWidth(), roof = spec.roof();
        return new float[][] {
                {-hb, TRACK_HEIGHT}, {hb, TRACK_HEIGHT}, {hb, roof - BEVEL}, {hb - BEVEL, roof}, {-hb + BEVEL, roof}, {-hb, roof - BEVEL}};
    }

    // The end of a section: the profile narrowed by the taper on the sides and top, the bottom kept.
    static float[][] endProfile(Spec spec) {
        float t = spec.taper();
        return MeshBuilder.inset(profile(spec), new float[] {0, t, t, t, t, t});
    }

    private static void body(MeshBuilder mesh, Spec spec) {
        float[][] full = profile(spec), end = endProfile(spec);
        float back = spec.back(), t = spec.taper();
        mesh.loft(PLATE, end, back, full, back + t);
        mesh.cap(PLATE, end, back, false);
        if (spec.drillhead()) {
            mesh.loft(PLATE, full, back + t, full, spec.bodyFront());
            mesh.cap(PLATE, full, spec.bodyFront(), true);
        } else {
            float front = spec.front();
            mesh.loft(PLATE, full, back + t, full, front - t);
            mesh.loft(PLATE, full, front - t, end, front);
            mesh.cap(PLATE, end, front, true);
        }
        roofPlates(mesh, spec);
    }

    // The flat part of the roof gets its own walkway texture, laid just above the body's top face.
    private static void roofPlates(MeshBuilder mesh, Spec spec) {
        float x = spec.bodyHalfWidth() - BEVEL, y = spec.roof() + 0.002F;
        float z0 = spec.back() + spec.taper(), z1 = spec.drillhead() ? spec.bodyFront() : spec.front() - spec.taper();
        mesh.box(ROOF, -x, y - 0.01F, z0, x, y, z1, "U");
    }

    private static void belly(MeshBuilder mesh, Spec spec) {
        float x = spec.halfWidth() - spec.trackWidth();
        float z0 = spec.back() + spec.taper(), z1 = spec.drillhead() ? spec.bodyFront() : spec.front() - spec.taper();
        mesh.box(BELLY, -x, BELLY_BOTTOM, z0, x, TRACK_HEIGHT, z1, "NSDEW");
    }

    // Tracks: the belt and wheels are animated (see buildMoving); the frame plates between them are static.
    private static void trackFrame(MeshBuilder mesh, Spec spec, int side) {
        float outer = side * spec.halfWidth(), inner = side * (spec.halfWidth() - spec.trackWidth());
        float z0 = spec.back() + TRACK_END_GAP, z1 = spec.trackFront();
        float[][] stadium = stadium(z0, z1, TRACK_END_RADIUS * 0.8F, 8);
        // Side plates: just inside the belt's edges, so the belt shows as a rim around them.
        float outerPlate = outer - side * 0.04F, innerPlate = inner + side * 0.04F;
        alongX(mesh, () -> mesh.cap(TRACK_SIDE, stadium, outerPlate, side > 0));
        alongX(mesh, () -> mesh.cap(TRACK_SIDE, stadium, innerPlate, side < 0));
    }

    // Runs body with its z axis turned onto x, so profiles in (u = -z, y) are side views and their z is the world x.
    private static void alongX(MeshBuilder mesh, Runnable body) {
        mesh.with(Affine.rotate(new float[] {0, 1, 0}, (float) (Math.PI / 2), new float[] {0, 0, 0}), body);
    }

    /** A stadium in the (u, y) plane of a side view, where u = -z, so it reads counter-clockwise seen from +x. */
    static float[][] stadium(float z0, float z1, float radius, int arcSegments) {
        float cy = TRACK_HEIGHT / 2;
        float[][] points = new float[2 * (arcSegments + 1)][];
        int k = 0;
        // Front end (u = -z1 + r) going down, then back end going up.
        for (int i = 0; i <= arcSegments; i++) {
            double a = Math.PI / 2 + Math.PI * i / arcSegments;
            points[k++] = new float[] {-(z1 - radius) + radius * (float) Math.cos(a), cy + radius * (float) Math.sin(a)};
        }
        for (int i = 0; i <= arcSegments; i++) {
            double a = -Math.PI / 2 + Math.PI * i / arcSegments;
            points[k++] = new float[] {-(z0 + radius) + radius * (float) Math.cos(a), cy + radius * (float) Math.sin(a)};
        }
        return points;
    }

    // A box against one side wall: xIn..xOut measured outwards from the middle; 'O' in faces is its outward face.
    private static void sideBox(MeshBuilder mesh, String texture, int side, float xIn, float xOut, float y0, float z0, float y1, float z1, String faces) {
        String mapped = faces.replace('O', side > 0 ? 'E' : 'W');
        if (side > 0) {
            mesh.box(texture, xIn, y0, z0, xOut, y1, z1, mapped);
        } else {
            mesh.box(texture, -xOut, y0, z0, -xIn, y1, z1, mapped);
        }
    }

    private static void doorFrame(MeshBuilder mesh, Spec spec) {
        float x = spec.bodyHalfWidth(), z0 = spec.back() + DOOR_FROM_BACK, z1 = z0 + 1;
        float y0 = TRACK_HEIGHT, y1 = TRACK_HEIGHT + 2;
        float o = 0.06F, w = 0.125F;
        // Jambs, lintel and a sill step that rests on the track.
        sideBox(mesh, TRIM, RIGHT, x, x + o, y0, z0 - w, y1 + w, z0, "OUDNS");
        sideBox(mesh, TRIM, RIGHT, x, x + o, y0, z1, y1 + w, z1 + w, "OUDNS");
        sideBox(mesh, TRIM, RIGHT, x, x + o, y1, z0, y1 + w, z1, "OUD");
        sideBox(mesh, TRIM, RIGHT, x, x + 0.12F, y0 - 0.06F, z0 - w, y0, z1 + w, "OUNS");
        // The doorway behind the sliding door: a dark recess.
        mesh.shade(0.35F);
        sideBox(mesh, TRIM, RIGHT, x + 0.005F, x + 0.006F, y0, z0, y1, z1, "O");
        mesh.shade(1.0F);
    }

    // Structure on both sides: a rubbing strip above the tracks, stiffening ribs every third block, and a ladder to
    // the roof just ahead of the door.
    private static void sideDetails(MeshBuilder mesh, Spec spec) {
        float hb = spec.bodyHalfWidth(), o = 0.05F;
        float z0 = spec.back() + spec.taper(), z1 = spec.drillhead() ? spec.bodyFront() : spec.front() - spec.taper();
        float doorBack = spec.back() + DOOR_FROM_BACK - 0.2F, doorFront = doorBack + 1.4F;
        float ladder = doorFront + 0.3F, top = spec.roof() - BEVEL;
        for (int side = -1; side <= 1; side += 2) {
            // The strip stops at the door.
            if (side == RIGHT) {
                sideBox(mesh, TRIM, side, hb, hb + o, TRACK_HEIGHT, z0, TRACK_HEIGHT + 0.18F, doorBack, "OUDNS");
                sideBox(mesh, TRIM, side, hb, hb + o, TRACK_HEIGHT, doorFront, TRACK_HEIGHT + 0.18F, z1, "OUDNS");
            } else {
                sideBox(mesh, TRIM, side, hb, hb + o, TRACK_HEIGHT, z0, TRACK_HEIGHT + 0.18F, z1, "OUDNS");
            }
            for (float z = z0 + 1.5F; z < z1 - 0.5F; z += 3) {
                if (side == RIGHT && z > doorBack - 0.3F && z < ladder + 0.8F) {
                    continue;
                }
                sideBox(mesh, TRIM, side, hb, hb + o, TRACK_HEIGHT + 0.18F, z - 0.08F, top, z + 0.08F, "OUDNS");
            }
        }
        if (ladder + 0.5F < z1) {
            float x0 = hb, x1 = hb + 0.07F;
            sideBox(mesh, TRIM, RIGHT, x0, x1, TRACK_HEIGHT + 0.2F, ladder, top + 0.15F, ladder + 0.06F, "OUDNS");
            sideBox(mesh, TRIM, RIGHT, x0, x1, TRACK_HEIGHT + 0.2F, ladder + 0.44F, top + 0.15F, ladder + 0.5F, "OUDNS");
            for (float y = TRACK_HEIGHT + 0.5F; y < top; y += 0.4F) {
                sideBox(mesh, TRIM, RIGHT, x0 + 0.01F, x1, y, ladder + 0.06F, y + 0.05F, ladder + 0.44F, "OUD");
            }
        }
    }

    private static void roofRails(MeshBuilder mesh, Spec spec) {
        float x = spec.bodyHalfWidth() - BEVEL + 0.1F, y = spec.roof(), top = spec.height - 0.06F;
        float z0 = spec.back() + spec.taper() + 0.2F;
        float z1 = (spec.drillhead() ? spec.bodyFront() - 1.2F : spec.front() - spec.taper()) - 0.2F;
        for (int side = -1; side <= 1; side += 2) {
            float xs = side * x;
            int posts = Math.max(2, Math.round((z1 - z0) / 1.5F) + 1);
            for (int i = 0; i < posts; i++) {
                float z = z0 + (z1 - z0) * i / (posts - 1);
                mesh.box(TRIM, xs - 0.04F, y, z - 0.04F, xs + 0.04F, top, z + 0.04F, "NSEW");
            }
            mesh.box(TRIM, xs - 0.05F, top - 0.08F, z0 - 0.05F, xs + 0.05F, top, z1 + 0.05F);
        }
    }

    // A raised frame around a coupling mouth, on an end face.
    private static void couplingFrame(MeshBuilder mesh, Spec spec, float z, boolean front) {
        Mouth m = mouth(spec, z);
        float d = front ? 0.05F : -0.05F, w = 0.15F;
        float za = Math.min(z, z + d), zb = Math.max(z, z + d);
        String faces = front ? "SUDEW" : "NUDEW";
        mesh.box(HAZARD, -m.halfWidth() - w, m.bottom() - w, za, -m.halfWidth(), m.top() + w, zb, faces);
        mesh.box(HAZARD, m.halfWidth(), m.bottom() - w, za, m.halfWidth() + w, m.top() + w, zb, faces);
        mesh.box(HAZARD, -m.halfWidth(), m.top(), za, m.halfWidth(), m.top() + w, zb, faces);
        mesh.box(HAZARD, -m.halfWidth(), m.bottom() - w, za, m.halfWidth(), m.bottom(), zb, faces);
        // Inside the frame: a shutter, closed off when no section is coupled here (the bellows cover it otherwise).
        float zm = z + (front ? 0.004F : -0.004F);
        mesh.box(SHUTTER, -m.halfWidth(), m.bottom(), Math.min(z, zm), m.halfWidth(), m.top(), Math.max(z, zm), front ? "S" : "N");
    }

    // The drill's housing: a ring that bridges the square body and the round cutter head.
    private static void shroud(MeshBuilder mesh, Spec spec) {
        float r = spec.halfWidth() - 0.05F, cy = spec.height / 2.0F;
        mesh.cylinder(HAZARD, TRIM, 0, cy, r, spec.bodyFront(), spec.front(), 32, false, true);
    }

    // Above the helm, at the front of the roof: the housing and its forward window.
    private static void periscope(MeshBuilder mesh, Spec spec) {
        float y0 = spec.roof(), y1 = spec.height - 0.04F, z1 = spec.bodyFront() - 0.2F, z0 = z1 - 0.7F;
        mesh.box(TRIM, -0.32F, y0, z0, 0.32F, y1, z1, "NEWU");
        mesh.box(TRIM, -0.32F, y0, z1 - 0.001F, 0.32F, y0 + 0.08F, z1, "S");
        mesh.fit(true).box(GLASS, -0.32F, y0 + 0.08F, z1 - 0.001F, 0.32F, y1, z1, "S").fit(false);
    }

    // Above the generator (right wall, third block from the front): a stack rising from the right bevel.
    private static void exhaust(MeshBuilder mesh, Spec spec) {
        float x = RIGHT * (spec.bodyHalfWidth() - 0.35F), z = spec.bodyFront() - 2.0F;
        float base = spec.roof() - BEVEL + 0.35F - 0.05F, top = spec.height - 0.05F;
        mesh.with(Affine.rotate(new float[] {1, 0, 0}, (float) (-Math.PI / 2), new float[] {x, base, z}),
                () -> mesh.cylinder(PIPE, PIPE, x, base, 0.16F, z, z + (top - base) - 0.12F, 10, false, false));
        mesh.fit(true).with(Affine.rotate(new float[] {1, 0, 0}, (float) (-Math.PI / 2), new float[] {x, top - 0.12F, z}),
                () -> mesh.cylinder(TRIM, SOOT, x, top - 0.12F, 0.2F, z, z + 0.12F, 10, true, true)).fit(false);
    }

    /** Where the exhaust stack's top is, for smoke. */
    public static float[] exhaustTop(Spec spec) {
        return new float[] {RIGHT * (spec.bodyHalfWidth() - 0.35F), spec.height - 0.05F, spec.bodyFront() - 2.0F};
    }

    // Headlights live in the corners the bevels leave free, facing forward.
    private static void lampHousings(MeshBuilder mesh, Spec spec) {
        for (int side = -1; side <= 1; side += 2) {
            float[] c = lampCenter(spec, side);
            mesh.box(TRIM, c[0] - 0.28F, c[1] - 0.22F, c[2] - 0.45F, c[0] + 0.28F, c[1] + 0.22F, c[2], "NEWUD");
        }
    }

    static float[] lampCenter(Spec spec, int side) {
        float hb = spec.bodyHalfWidth();
        return new float[] {side * (hb - 0.32F), spec.roof() - 0.3F, spec.bodyFront()};
    }

    private static void wagonDetails(MeshBuilder mesh, Spec spec) {
        float hb = spec.bodyHalfWidth(), y = spec.roof() + 0.004F;
        // A roof hatch in the middle, where a turret would go.
        float zc = (spec.back() + spec.front()) / 2;
        mesh.fit(true).box(HATCH, -0.5F, spec.roof(), zc - 0.5F, 0.5F, y + 0.08F, zc + 0.5F, "UNSEW");
        // Vents along both sides, every third block, mirrored about the middle.
        float y0 = spec.roof() - BEVEL - 1.0F;
        for (float z = zc; z + 0.5F < spec.front() - spec.taper() - 0.5F; z += 3) {
            for (float zz : new float[] {z, 2 * zc - z}) {
                if (Math.abs(zz - (spec.back() + DOOR_FROM_BACK + 0.5F)) < 1.2F) {
                    continue;
                }
                mesh.box(VENT, hb, y0, zz - 0.4F, hb + 0.01F, y0 + 0.6F, zz + 0.4F, "E");
                mesh.box(VENT, -hb - 0.01F, y0, zz - 0.4F, -hb, y0 + 0.6F, zz + 0.4F, "W");
                if (z == zc) {
                    break;
                }
            }
        }
        mesh.fit(false);
    }

    // Moving parts: rebuilt every frame from the section's state.

    /**
     * @param travel     how far the section has driven, in blocks (forward positive): scrolls the belts and turns the wheels
     * @param doorOpen   0 closed .. 1 open; the door slides back along the hull
     * @param lightsOn   headlights lit
     * @param beaconOn   warning beacon lit (blinking is up to the caller)
     */
    public static MeshBuilder buildMoving(Spec spec, float travel, float doorOpen, boolean lightsOn, boolean beaconOn, int upgrades,
            @Nullable TeslaTurret turret, float turretRise) {
        MeshBuilder mesh = new MeshBuilder();
        if (spec.drillhead()) {
            upgrades(mesh, spec, upgrades);
        }
        if (turret != null) {
            turret(mesh, spec, turret, turretRise);
        }
        for (int side = -1; side <= 1; side += 2) {
            belt(mesh, spec, side, travel);
            wheels(mesh, spec, side, travel);
        }
        door(mesh, spec, doorOpen);
        if (spec.drillhead()) {
            for (int side = -1; side <= 1; side += 2) {
                float[] c = lampCenter(spec, side);
                mesh.fit(true).emissive(lightsOn).box(lightsOn ? LAMP_ON : LAMP, c[0] - 0.2F, c[1] - 0.15F, c[2] - 0.45F, c[0] + 0.2F, c[1] + 0.15F, c[2] + 0.02F, "S");
            }
            float z = spec.back() + spec.taper() + 0.6F, y = spec.roof();
            String beacon = beaconOn ? BEACON_ON : BEACON;
            mesh.emissive(beaconOn).with(Affine.rotate(new float[] {1, 0, 0}, (float) (-Math.PI / 2), new float[] {0, y, z}),
                    () -> mesh.cylinder(beacon, beacon, 0, y, 0.18F, z, z + 0.3F, 8, false, true)).emissive(false).fit(false);
        }
        return mesh;
    }

    // Installed upgrades show outside (bits as ShipUpgrade's): the radar's sonar pod under the nose and its search coil
    // under the tail, the radio's antenna on the roof.
    private static final int RADAR_BIT = 1;
    private static final int RADIO_BIT = 2;

    private static void upgrades(MeshBuilder mesh, Spec spec, int upgrades) {
        if ((upgrades & RADAR_BIT) != 0) {
            float z1 = spec.bodyFront() - 0.6F, z0 = z1 - 1.0F;
            mesh.box(TRIM, -0.45F, 0.05F, z0, 0.45F, BELLY_BOTTOM, z1, "NSEW");
            mesh.fit(true).box(GLASS, -0.45F, 0.05F, z0, 0.45F, BELLY_BOTTOM, z1, "D").fit(false);
        }
        if ((upgrades & RADAR_BIT) != 0) {
            float z = spec.back() + spec.taper() + 0.9F, y = 0.08F;
            mesh.with(Affine.rotate(new float[] {1, 0, 0}, (float) (-Math.PI / 2), new float[] {0, y, z}),
                    () -> mesh.cylinder(HAZARD, TRIM, 0, y, 0.45F, z, z + 0.06F, 12, true, false));
            mesh.box(TRIM, -0.04F, y + 0.06F, z - 0.04F, 0.04F, BELLY_BOTTOM, z + 0.04F, "NSEW");
        }
        // The tunnel leaves no room for a mast, so the whip antenna lies folded back along the roof, tied to a clip.
        if ((upgrades & RADIO_BIT) != 0) {
            float x = spec.bodyHalfWidth() - BEVEL - 0.35F, y = spec.roof(), z0 = spec.bodyFront() - 1.6F;
            float z1 = Math.max(spec.back() + spec.taper() + 0.3F, z0 - 3.0F), rod = spec.height - 0.12F;
            mesh.box(TRIM, x - 0.12F, y, z0 - 0.12F, x + 0.12F, rod, z0 + 0.12F, "NSEWU");
            mesh.box(TRIM, x - 0.03F, rod - 0.06F, z1, x + 0.03F, rod, z0, "EWUDN");
            mesh.box(TRIM, x - 0.06F, y, z1 + 0.2F, x + 0.06F, rod - 0.06F, z1 + 0.3F, "NSEW");
            mesh.fit(true).box(BEACON_ON, x - 0.05F, rod - 0.08F, z1 - 0.1F, x + 0.05F, rod + 0.02F, z1, "NSEWUD").fit(false);
        }
    }

    /**
     * A tesla turret in the middle of the roof: a hatch flush with it, from which a coil with a crown of rings and a
     * ball on top rises when there's room (see ShipSectionEntity.getTurretRise). The medium turret is bigger.
     */
    private static void turret(MeshBuilder mesh, Spec spec, TeslaTurret turret, float rise) {
        float scale = turret == TeslaTurret.MEDIUM ? 1.3F : 1.0F;
        float z = (spec.back() + spec.front()) / 2, y = spec.roof(), half = 0.55F * scale;
        mesh.box(HATCH, -half, y, z - half, half, y + 0.05F, z + half, "NSEWU");
        if (rise <= 0.0F) {
            return;
        }
        float height = 1.1F * scale * rise, ball = 0.28F * scale;
        Runnable coil = () -> {
            mesh.cylinder(TRIM, TRIM, 0, y, 0.3F * scale, z, z + 0.12F, 10, false, true);
            mesh.cylinder(PIPE, PIPE, 0, y, 0.1F * scale, z, z + height, 8, false, false);
            for (int ring = 1; ring <= 3; ring++) {
                float at = z + height * ring / 4.0F;
                mesh.cylinder(TRIM, TRIM, 0, y, 0.2F * scale, at - 0.03F, at + 0.03F, 10, true, true);
            }
            mesh.cylinder(DRILL_STEEL, DRILL_STEEL, 0, y, ball, z + height, z + height + 2 * ball * 0.8F, 10, true, true);
        };
        mesh.with(Affine.rotate(new float[] {1, 0, 0}, (float) (-Math.PI / 2), new float[] {0, y, z}), coil);
    }

    // The belt runs around a stadium; its texture scrolls with travel.
    private static void belt(MeshBuilder mesh, Spec spec, int side, float travel) {
        float x0 = side > 0 ? spec.halfWidth() - spec.trackWidth() : -spec.halfWidth();
        float x1 = x0 + spec.trackWidth();
        float[][] loop = stadium(spec.back() + TRACK_END_GAP, spec.trackFront(), TRACK_END_RADIUS, 8);
        float along = 0;
        for (int i = 0; i < loop.length; i++) {
            float[] a = loop[i], b = loop[(i + 1) % loop.length];
            // Back to section coordinates: u = -z.
            float[] pa0 = {x0, a[1], -a[0]}, pa1 = {x1, a[1], -a[0]}, pb1 = {x1, b[1], -b[0]}, pb0 = {x0, b[1], -b[0]};
            float length = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (length > 1.0E-4F) {
                float[] dir = {0, (b[1] - a[1]) / length, -(b[0] - a[0]) / length};
                // The loop runs clockwise seen from +x; this corner order faces outwards.
                mesh.polygon(TRACK_BELT, new float[][] {pa0, pb0, pb1, pa1}, new float[] {1, 0, 0}, dir, pa0, 0, along + travel);
            }
            along += length;
        }
    }

    private static void wheels(MeshBuilder mesh, Spec spec, int side, float travel) {
        float z0 = spec.back() + TRACK_END_GAP + TRACK_END_RADIUS, z1 = spec.trackFront() - TRACK_END_RADIUS;
        float x = side * (spec.halfWidth() - 0.02F);
        int count = Math.max(2, Math.round((z1 - z0) / 0.9F) + 1);
        for (int i = 0; i < count; i++) {
            float z = z0 + (z1 - z0) * i / (count - 1);
            boolean end = i == 0 || i == count - 1;
            float radius = end ? 0.42F : 0.34F, cy = end ? TRACK_HEIGHT / 2 : 0.42F;
            float angle = travel / radius;
            float[] center = {x, cy, z};
            // A thin disc facing outwards, turned about the axle.
            mesh.fit(true).with(Affine.rotate(new float[] {1, 0, 0}, angle, center), () -> mesh.with(
                    Affine.rotate(new float[] {0, 1, 0}, (float) (side * Math.PI / 2), center),
                    () -> mesh.cylinder(TRIM, WHEEL, x, cy, radius, z - 0.02F, z + 0.02F, 12, false, true))).fit(false);
        }
    }

    // A sliding door: it runs back along the hull, outside it, so it never leaves the tunnel's cross-section.
    private static void door(MeshBuilder mesh, Spec spec, float open) {
        float x = spec.bodyHalfWidth() + 0.02F, z0 = spec.back() + DOOR_FROM_BACK - 1.02F * open, z1 = z0 + 1;
        float y0 = TRACK_HEIGHT;
        mesh.fit(true);
        sideBox(mesh, DOOR_LOWER, RIGHT, x, x + 0.03F, y0, z0, y0 + 1, z1, "O");
        sideBox(mesh, DOOR_UPPER, RIGHT, x, x + 0.03F, y0 + 1, z0, y0 + 2, z1, "O");
        mesh.fit(false);
        sideBox(mesh, TRIM, RIGHT, x, x + 0.03F, y0, z0, y0 + 2, z1, "UNS");
        sideBox(mesh, TRIM, RIGHT, x + 0.03F, x + 0.08F, y0 + 0.95F, z1 - 0.25F, y0 + 1.1F, z1 - 0.12F, "OUDNS");
    }

    /**
     * The accordion between a section's front mouth and the back mouth of the section ahead, both given as four
     * corners (bottom-left, bottom-right, top-right, top-left seen from behind) in this section's coordinates.
     */
    public static MeshBuilder bellows(float[][] ownFront, float[][] leaderBack) {
        MeshBuilder mesh = new MeshBuilder();
        float gap = 0;
        for (int i = 0; i < 4; i++) {
            gap = Math.max(gap, MeshBuilder.dist(ownFront[i], leaderBack[i]));
        }
        int folds = Math.max(3, Math.round(gap * 5));
        float[][][] rings = new float[folds + 1][4][];
        for (int k = 0; k <= folds; k++) {
            float t = (float) k / folds;
            float[] centre = new float[3];
            for (int i = 0; i < 4; i++) {
                rings[k][i] = lerp(ownFront[i], leaderBack[i], t);
                for (int a = 0; a < 3; a++) {
                    centre[a] += rings[k][i][a] / 4;
                }
            }
            // Every other ring is pulled in: the folds.
            if (k % 2 == 1) {
                for (int i = 0; i < 4; i++) {
                    rings[k][i] = lerp(centre, rings[k][i], 0.86F);
                }
            }
        }
        // Each fold shows the whole texture, so its shading runs across the fold.
        mesh.fit(true);
        for (int k = 0; k < folds; k++) {
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                // Rings go from this section forward; corners run counter-clockwise seen from behind, so this order faces out.
                mesh.shade(k % 2 == 0 ? 0.8F : 1.0F).face(BELLOWS, rings[k][j], rings[k][i], rings[k + 1][i], rings[k + 1][j]);
            }
        }
        return mesh.shade(1.0F).fit(false);
    }

    /** The four corners of a mouth, bottom-left, bottom-right, top-right, top-left as seen from behind (looking +z). */
    public static float[][] mouthCorners(Mouth m) {
        return new float[][] {
                {m.halfWidth(), m.bottom(), m.z()}, {-m.halfWidth(), m.bottom(), m.z()},
                {-m.halfWidth(), m.top(), m.z()}, {m.halfWidth(), m.top(), m.z()}};
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private SectionModel() {
    }
}
