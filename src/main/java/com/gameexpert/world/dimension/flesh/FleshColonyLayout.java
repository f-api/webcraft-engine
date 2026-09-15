package com.gameexpert.world.dimension.flesh;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure deterministic macro layout for one flesh-nether colony cell. */
public final class FleshColonyLayout {
    public static final int GRID = 256;

    private static final int HEART_BASE_Y = 64;
    private static final int WALK_FLOOR_Y = 65;
    private static final int WALK_AIR_Y = 66;
    private static final int PORTAL_CENTER_X = 8;
    private static final int PORTAL_CENTER_Z = 11;
    private static final int PORTAL_SAFETY_RADIUS = 32;

    private static final int CENTER_X_SALT = 0x71010000;
    private static final int CENTER_Z_SALT = 0x71020000;
    private static final int RADIUS_SALT = 0x71030000;
    private static final int TYPE_SALT = 0x71040000;
    private static final int HEART_WIDTH_SALT = 0x71050000;
    private static final int HEART_HEIGHT_SALT = 0x71060000;
    private static final int SHELL_SALT = 0x71070000;
    private static final int HEART_DEPTH_SALT = 0x71080000;
    private static final int ROTATION_SALT = 0x71090000;
    private static final int CORRIDOR_WIDTH_SALT = 0x710a0000;
    private static final int GUARDIAN_WIDTH_SALT = 0x710b0000;
    private static final int ARTERY_MODE_SALT = 0x71100003;
    private static final int BRANCH_LENGTH_SALT = 0x71200001;
    private static final int BRANCH_WIDTH_SALT = 0x71200002;
    private static final int BRANCH_HEIGHT_SALT = 0x71200003;
    private static final int LARGE_COCOON_STAGE_SALT = 0x71250000;
    private static final int TISSUE_SALT = 0x71300000;
    private static final int NORMAL_COCOON_SALT = 0x71400000;
    private static final int NORMAL_COCOON_STAGE_SALT = 0x71500000;
    private static final int LARGE_COCOON_SALT = 0x71600000;

    private FleshColonyLayout() { }

    public enum ColonyType { EXPOSED, RUPTURED, FUSED }

    public enum TissueKind {
        AIR, FLESH, MYOCARDIUM, ARTERY, ANCHOR, FAT, MEMBRANE, BONE, NECROSIS,
        CORE, NORMAL_COCOON, LARGE_COCOON
    }

    public static final class Point {
        private final int x;
        private final int y;
        private final int z;

        public Point(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Point point)) return false;
            return x == point.x && y == point.y && z == point.z;
        }

        @Override public int hashCode() { return Objects.hash(x, y, z); }

        @Override public String toString() { return "Point[" + x + "," + y + "," + z + "]"; }
    }

    public static final class Bounds {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        private Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public int minX() { return minX; }
        public int minY() { return minY; }
        public int minZ() { return minZ; }
        public int maxX() { return maxX; }
        public int maxY() { return maxY; }
        public int maxZ() { return maxZ; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Bounds bounds)) return false;
            return minX == bounds.minX && minY == bounds.minY && minZ == bounds.minZ
                    && maxX == bounds.maxX && maxY == bounds.maxY && maxZ == bounds.maxZ;
        }

        @Override public int hashCode() {
            return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    public static final class Sample {
        private final TissueKind kind;
        private final int stage;

        private Sample(TissueKind kind, int stage) {
            this.kind = Objects.requireNonNull(kind);
            this.stage = stage;
        }

        public TissueKind kind() { return kind; }
        public int stage() { return stage; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Sample sample)) return false;
            return kind == sample.kind && stage == sample.stage;
        }

        @Override public int hashCode() { return Objects.hash(kind, stage); }
    }

    /** A secondary walkable room carved into the heart interior. */
    public static final class Chamber {
        private final Point center;
        private final int radiusX;
        private final int radiusZ;
        private final int floorY;
        private final int ceilingY;

        private Chamber(Point center, int radiusX, int radiusZ, int floorY, int ceilingY) {
            this.center = center;
            this.radiusX = radiusX;
            this.radiusZ = radiusZ;
            this.floorY = floorY;
            this.ceilingY = ceilingY;
        }

        public Point center() { return center; }
        public int radiusX() { return radiusX; }
        public int radiusZ() { return radiusZ; }
        public int floorY() { return floorY; }
        public int ceilingY() { return ceilingY; }
    }

    public static final class Layout {
        private final int seed;
        private final int cellX;
        private final int cellZ;
        private final String cellKey;
        private final int centerX;
        private final int centerZ;
        private final int radius;
        private final ColonyType type;
        private final Bounds bounds;
        private final Point core;
        private final List<Point> anchors;
        private final int heartWidth;
        private final int heartHeight;
        private final int shellThickness;
        private final int corridorWidth;
        private final int guardianWidth;
        private final int mainChamberHeight;
        private final int heartRadiusZ;
        private final int rotation;
        private final List<Chamber> sideChambers;
        private final List<ArteryRoute> arteryRoutes;

        private Layout(int seed, int cellX, int cellZ, int centerX, int centerZ, int radius,
                ColonyType type, Bounds bounds, Point core, List<Point> anchors,
                int heartWidth, int heartHeight, int shellThickness, int corridorWidth,
                int guardianWidth, int mainChamberHeight, int heartRadiusZ, int rotation,
                List<Chamber> sideChambers, List<ArteryRoute> arteryRoutes) {
            this.seed = seed;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.cellKey = cellX + ":" + cellZ;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.type = Objects.requireNonNull(type);
            this.bounds = Objects.requireNonNull(bounds);
            this.core = Objects.requireNonNull(core);
            this.anchors = List.copyOf(anchors);
            this.heartWidth = heartWidth;
            this.heartHeight = heartHeight;
            this.shellThickness = shellThickness;
            this.corridorWidth = corridorWidth;
            this.guardianWidth = guardianWidth;
            this.mainChamberHeight = mainChamberHeight;
            this.heartRadiusZ = heartRadiusZ;
            this.rotation = rotation;
            this.sideChambers = List.copyOf(sideChambers);
            this.arteryRoutes = List.copyOf(arteryRoutes);
        }

        public int seed() { return seed; }
        public int cellX() { return cellX; }
        public int cellZ() { return cellZ; }
        public String cellKey() { return cellKey; }
        public int centerX() { return centerX; }
        public int centerZ() { return centerZ; }
        public int radius() { return radius; }
        public ColonyType type() { return type; }
        public Bounds bounds() { return bounds; }
        public Point core() { return core; }
        public List<Point> anchors() { return anchors; }
        public int heartWidth() { return heartWidth; }
        public int heartHeight() { return heartHeight; }
        public int shellThickness() { return shellThickness; }
        public int corridorWidth() { return corridorWidth; }
        public int guardianWidth() { return guardianWidth; }
        public int mainChamberHeight() { return mainChamberHeight; }
        public List<Chamber> sideChambers() { return sideChambers; }
        public int heartTopY() { return HEART_BASE_Y + heartHeight - 1; }
        public int mainChamberCeilingY() { return heartTopY() - shellThickness; }

        public Sample sample(int x, int y, int z) {
            return FleshColonyLayout.sample(this, x, y, z);
        }
    }

    private static final class ArteryRoute {
        private final int startX;
        private final int startZ;
        private final int endX;
        private final int endZ;
        private final int width;
        private final int bottomY;
        private final int topY;
        private final int branchEndX;
        private final int branchEndZ;
        private final int branchWidth;
        private final int branchTopY;

        private ArteryRoute(int startX, int startZ, int endX, int endZ, int width,
                int bottomY, int topY, int branchEndX, int branchEndZ,
                int branchWidth, int branchTopY) {
            this.startX = startX;
            this.startZ = startZ;
            this.endX = endX;
            this.endZ = endZ;
            this.width = width;
            this.bottomY = bottomY;
            this.topY = topY;
            this.branchEndX = branchEndX;
            this.branchEndZ = branchEndZ;
            this.branchWidth = branchWidth;
            this.branchTopY = branchTopY;
        }

        private int midpointX() { return midpoint(startX, endX); }
        private int midpointZ() { return midpoint(startZ, endZ); }
    }

    private static final class VerticalRange {
        private int bottomY = 64;
        private int topY = Integer.MIN_VALUE;

        private void include(int bottomY, int topY) {
            this.bottomY = Math.min(this.bottomY, bottomY);
            this.topY = Math.max(this.topY, topY);
        }

        private boolean present() { return topY != Integer.MIN_VALUE; }
    }

    static long hash(int seed, int x, int z, int salt) {
        int value = seed ^ x * 0x9e3779b9 ^ z * 0x85ebca6b ^ salt;
        value = (value ^ (value >>> 16)) * 0x7feb352d;
        value = (value ^ (value >>> 15)) * 0x846ca68b;
        return Integer.toUnsignedLong(value ^ (value >>> 16));
    }

    private static int roll(int seed, int x, int z, int salt, int bound) {
        return (int) (hash(seed, x, z, salt) % bound);
    }

    public static Layout forCoordinate(int seed, int x, int z) {
        return forCell(seed, Math.floorDiv(x, GRID), Math.floorDiv(z, GRID));
    }

    public static Layout forCell(int seed, int cellX, int cellZ) {
        int centerX;
        int centerZ;
        int radius;
        ColonyType type;
        if (cellX == 0 && cellZ == 0) {
            centerX = 96;
            centerZ = 96;
            radius = 80;
            type = ColonyType.EXPOSED;
        } else {
            centerX = checkedCellCenter(cellX, roll(seed, cellX, cellZ, CENTER_X_SALT, 33));
            centerZ = checkedCellCenter(cellZ, roll(seed, cellX, cellZ, CENTER_Z_SALT, 33));
            radius = 72 + roll(seed, cellX, cellZ, RADIUS_SALT, 25);
            int typeRoll = roll(seed, cellX, cellZ, TYPE_SALT, 100);
            type = typeRoll < 45 ? ColonyType.EXPOSED
                    : typeRoll < 80 ? ColonyType.RUPTURED : ColonyType.FUSED;
        }

        int heartWidth = 30 + roll(seed, cellX, cellZ, HEART_WIDTH_SALT, 21);
        int heartHeight = 20 + roll(seed, cellX, cellZ, HEART_HEIGHT_SALT, 11);
        int shellThickness = 2 + roll(seed, cellX, cellZ, SHELL_SALT, 3);
        int corridorWidth = 3 + roll(seed, cellX, cellZ, CORRIDOR_WIDTH_SALT, 3);
        int guardianWidth = 8 + roll(seed, cellX, cellZ, GUARDIAN_WIDTH_SALT, 3);
        int heartRadiusX = heartWidth / 2;
        int heartRadiusZ = Math.max(12,
                heartRadiusX - roll(seed, cellX, cellZ, HEART_DEPTH_SALT, 5));
        int rotation = roll(seed, cellX, cellZ, ROTATION_SALT, 4);
        int mainChamberHeight = heartHeight - shellThickness - 2;
        int heartTopY = HEART_BASE_Y + heartHeight - 1;
        int mainCeilingY = heartTopY - shellThickness;

        List<Point> anchors = createAnchors(centerX, centerZ, heartRadiusX, heartRadiusZ, rotation);
        List<Chamber> sideChambers = createSideChambers(centerX, centerZ, heartRadiusX,
                heartRadiusZ, shellThickness, rotation, mainCeilingY);
        List<ArteryRoute> arteryRoutes = createArteryRoutes(seed, cellX, cellZ, centerX,
                centerZ, radius, guardianWidth, rotation, anchors);
        Bounds bounds = new Bounds(centerX - radius, 62, centerZ - radius,
                centerX + radius, Math.max(75, HEART_BASE_Y + heartHeight + 2), centerZ + radius);

        return new Layout(seed, cellX, cellZ, centerX, centerZ, radius, type, bounds,
                new Point(centerX, WALK_FLOOR_Y, centerZ), anchors, heartWidth, heartHeight,
                shellThickness, corridorWidth, guardianWidth, mainChamberHeight,
                heartRadiusZ, rotation, sideChambers, arteryRoutes);
    }

    private static int checkedCellCenter(int cell, int jitterRoll) {
        int cellBase = Math.multiplyExact(cell, GRID);
        return Math.addExact(cellBase, 112 + jitterRoll);
    }

    private static List<Point> createAnchors(int centerX, int centerZ, int radiusX,
            int radiusZ, int rotation) {
        List<Point> anchors = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            int direction = (rotation + index) & 3;
            int extent = (direction & 1) == 0 ? radiusZ : radiusX;
            anchors.add(new Point(centerX + directionX(direction) * (extent - 1),
                    WALK_FLOOR_Y, centerZ + directionZ(direction) * (extent - 1)));
        }
        return anchors;
    }

    private static List<Chamber> createSideChambers(int centerX, int centerZ, int radiusX,
            int radiusZ, int shellThickness, int rotation, int mainCeilingY) {
        List<Chamber> chambers = new ArrayList<>(2);
        int mainRadiusX = mainChamberRadiusX(radiusX, shellThickness);
        int mainRadiusZ = mainChamberRadiusZ(radiusZ, shellThickness);
        int heartTopY = mainCeilingY + shellThickness;
        for (int index = 0; index < 2; index++) {
            int direction = (rotation + (index == 0 ? 1 : 3)) & 3;
            boolean northSouth = (direction & 1) == 0;
            int innerPrimaryRadius = (northSouth ? radiusZ : radiusX) - shellThickness;
            int mainPrimaryRadius = northSouth ? mainRadiusZ : mainRadiusX;
            int primaryRadius = index == 0
                    ? Math.min(4, Math.max(3, innerPrimaryRadius - mainPrimaryRadius + 1))
                    : 3;
            int crossRadius = index == 0 ? 3 : 4;
            int maximumOffset = Math.max(3, innerPrimaryRadius - primaryRadius);
            int offset = Math.min(maximumOffset, mainPrimaryRadius + 2 + index);
            int lateralDirection = (direction + 1) & 3;
            int lateralShift = index == 0 ? 1 : -1;
            int chamberX = centerX + directionX(direction) * offset
                    + directionX(lateralDirection) * lateralShift;
            int chamberZ = centerZ + directionZ(direction) * offset
                    + directionZ(lateralDirection) * lateralShift;
            int radiusAlongX = northSouth ? crossRadius : primaryRadius;
            int radiusAlongZ = northSouth ? primaryRadius : crossRadius;
            int roomRoofThickness = Math.min(4, shellThickness + index);
            int ceilingY = heartTopY - roomRoofThickness;
            chambers.add(new Chamber(new Point(chamberX, WALK_FLOOR_Y, chamberZ),
                    radiusAlongX, radiusAlongZ, WALK_FLOOR_Y, ceilingY));
        }
        return chambers;
    }

    private static List<ArteryRoute> createArteryRoutes(int seed, int cellX, int cellZ,
            int centerX, int centerZ, int colonyRadius, int guardianWidth, int rotation,
            List<Point> anchors) {
        List<ArteryRoute> routes = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            int direction = (rotation + index) & 3;
            Point anchor = anchors.get(index);
            int endX = centerX + directionX(direction) * (colonyRadius - 8);
            int endZ = centerZ + directionZ(direction) * (colonyRadius - 8);
            int mode = roll(seed, cellX, cellZ, ARTERY_MODE_SALT + index * 17, 6);
            int bottomY = mode == 2 ? 62 : 64;
            int topY = mode == 1 ? 67 : 65;
            int midpointX = midpoint(anchor.x(), endX);
            int midpointZ = midpoint(anchor.z(), endZ);
            int branchDirection = (direction + 1) & 3;
            int branchLength = 8 + roll(seed, cellX, cellZ,
                    BRANCH_LENGTH_SALT + index * 19, 9);
            int branchEndX = midpointX + directionX(branchDirection) * branchLength;
            int branchEndZ = midpointZ + directionZ(branchDirection) * branchLength;
            int branchWidth = 4 + roll(seed, cellX, cellZ,
                    BRANCH_WIDTH_SALT + index * 19, 4);
            int branchTopY = 64 + roll(seed, cellX, cellZ,
                    BRANCH_HEIGHT_SALT + index * 19, 4);
            routes.add(new ArteryRoute(anchor.x(), anchor.z(), endX, endZ, guardianWidth,
                    bottomY, topY, branchEndX, branchEndZ, branchWidth, branchTopY));
        }
        return routes;
    }

    public static Sample sample(Layout layout, int x, int y, int z) {
        if (!insideBounds(layout.bounds, x, y, z)) return air();
        if (isPortalSafetyColumn(x, z)) return air();

        long offsetX = (long) x - layout.centerX;
        long offsetZ = (long) z - layout.centerZ;
        if (offsetX * offsetX + offsetZ * offsetZ > (long) layout.radius * layout.radius) {
            return air();
        }

        if (x == layout.core.x() && y == layout.core.y() && z == layout.core.z()) {
            return sample(TissueKind.CORE, 0);
        }
        for (Point anchor : layout.anchors) {
            if (x == anchor.x() && y == anchor.y() && z == anchor.z()) {
                return sample(TissueKind.ANCHOR, 0);
            }
        }

        VerticalRange arteryRange = arteryRangeAt(layout, x, z);
        boolean heartAtFloor = containsHeart(layout, x, WALK_FLOOR_Y, z, 0);
        if (arteryRange.present() && !heartAtFloor) {
            long cocoonRoll = hash(layout.seed, x, z, LARGE_COCOON_SALT);
            if (cocoonRoll % 521 == 0 && y > arteryRange.topY && y <= arteryRange.topY + 3) {
                return sample(TissueKind.LARGE_COCOON,
                        (int) (hash(layout.seed, x, z, LARGE_COCOON_STAGE_SALT) % 3));
            }
            if (hash(layout.seed, x, z, NORMAL_COCOON_SALT) % 53 == 0
                    && y == arteryRange.topY + 1) {
                return sample(TissueKind.NORMAL_COCOON,
                        (int) (hash(layout.seed, x, z, NORMAL_COCOON_STAGE_SALT) % 3));
            }
        }

        boolean heartAtCell = containsHeart(layout, x, y, z, 0);
        if (arteryRange.present() && y >= arteryRange.bottomY && y <= arteryRange.topY
                && (!heartAtCell || y <= WALK_FLOOR_Y)) {
            return sample(TissueKind.ARTERY, 0);
        }

        if (!heartAtCell || isCavityAir(layout, x, y, z)
                && (y <= 70 || containsHeart(layout,x,y,z,layout.shellThickness)) || isRupture(layout, x, y, z)) {
            return air();
        }
        if (!containsHeart(layout, x, y, z, layout.shellThickness)) {
            long surface=hash(layout.seed ^ Math.floorDiv(y,5)*0x27d4eb2d,
                    Math.floorDiv(x,5),Math.floorDiv(z,5),TISSUE_SALT);
            if(layout.type==ColonyType.RUPTURED&&surface%7==0)return sample(TissueKind.NECROSIS,0);
            if(layout.type==ColonyType.FUSED&&surface%9==0)return sample(TissueKind.BONE,0);
            return sample(surface%7==0?TissueKind.FAT:surface%5==0?TissueKind.MEMBRANE:TissueKind.MYOCARDIUM,0);
        }

        long tissueRoll = hash(layout.seed ^ y * 0x27d4eb2d, x, z, TISSUE_SALT);
        if (layout.type == ColonyType.RUPTURED && tissueRoll % 7 == 0) {
            return sample(TissueKind.NECROSIS, 0);
        }
        if (layout.type == ColonyType.FUSED && tissueRoll % 9 == 0) {
            return sample(TissueKind.BONE, 0);
        }
        if (tissueRoll % 11 == 0) return sample(TissueKind.FAT, 0);
        return sample(tissueRoll % 4 == 0 ? TissueKind.FLESH : TissueKind.MYOCARDIUM, 0);
    }

    private static boolean insideBounds(Bounds bounds, int x, int y, int z) {
        return x >= bounds.minX && x <= bounds.maxX && y >= bounds.minY && y <= bounds.maxY
                && z >= bounds.minZ && z <= bounds.maxZ;
    }

    static boolean isPortalSafetyColumn(int x, int z) {
        long offsetX = (long) x - PORTAL_CENTER_X;
        long offsetZ = (long) z - PORTAL_CENTER_Z;
        if (offsetX < -PORTAL_SAFETY_RADIUS || offsetX > PORTAL_SAFETY_RADIUS
                || offsetZ < -PORTAL_SAFETY_RADIUS || offsetZ > PORTAL_SAFETY_RADIUS) {
            return false;
        }
        return offsetX * offsetX + offsetZ * offsetZ
                <= (long) PORTAL_SAFETY_RADIUS * PORTAL_SAFETY_RADIUS;
    }

    private static VerticalRange arteryRangeAt(Layout layout, int x, int z) {
        VerticalRange range = new VerticalRange();
        for (ArteryRoute route : layout.arteryRoutes) {
            if (onOrthogonalPath(x, z, route.startX, route.startZ,
                    route.endX, route.endZ, route.width)) {
                range.include(route.bottomY, route.topY);
            }
            if (onOrthogonalPath(x, z, route.midpointX(), route.midpointZ(),
                    route.branchEndX, route.branchEndZ, route.branchWidth)) {
                range.include(64, route.branchTopY);
            }
        }
        return range;
    }

    private static boolean containsHeart(Layout layout, int x, int y, int z, int inset) {
        int minimumY = HEART_BASE_Y + inset;
        int maximumY = layout.heartTopY() - inset;
        if (y < minimumY || y > maximumY) return false;
        int rx=layout.heartWidth/2-inset,rz=layout.heartRadiusZ-inset;
        // Keep the entrance-height collar grounded underneath the rounded upper mass.
        if(y<=70)return insideEllipse(x-layout.centerX,z-layout.centerZ,rx,rz);
        if(insideOrganLobe(x-layout.centerX,y,z-layout.centerZ,rx,rz,minimumY,maximumY))return true;
        for(var chamber:layout.sideChambers) {
            int crx=Math.min(chamber.radiusX+layout.shellThickness+2,layout.heartWidth/2-Math.abs(chamber.center.x-layout.centerX))-inset;
            int crz=Math.min(chamber.radiusZ+layout.shellThickness+2,layout.heartRadiusZ-Math.abs(chamber.center.z-layout.centerZ))-inset;
            if(insideOrganLobe(x-chamber.center.x,y,z-chamber.center.z,crx,crz,minimumY,maximumY))return true;
        }
        return false;
    }

    private static boolean insideOrganLobe(int x,int y,int z,int rx,int rz,int minY,int maxY) {
        if(rx<1||rz<1||y<minY||y>maxY)return false;
        long h=maxY-minY,v=2L*y-minY-maxY,xx=(long)rx*rx,zz=(long)rz*rz,hh=h*h;
        return ((long)x*x*zz+(long)z*z*xx)*hh+v*v*xx*zz<=xx*zz*hh;
    }

    private static boolean isCavityAir(Layout layout, int x, int y, int z) {
        if (y < WALK_AIR_Y) return false;
        if (y <= layout.mainChamberCeilingY()
                && insideEllipse(x - layout.centerX, z - layout.centerZ,
                        mainChamberRadiusX(layout.heartWidth / 2, layout.shellThickness),
                        mainChamberRadiusZ(layout.heartRadiusZ, layout.shellThickness))) {
            return true;
        }

        for (Chamber chamber : layout.sideChambers) {
            if (y > chamber.floorY && y <= chamber.ceilingY
                    && insideEllipse(x - chamber.center.x(), z - chamber.center.z(),
                            chamber.radiusX, chamber.radiusZ)) {
                return true;
            }
            if (y <= Math.min(70, chamber.ceilingY)
                    && onOrthogonalPath(x, z, layout.centerX, layout.centerZ,
                            chamber.center.x(), chamber.center.z(), layout.corridorWidth)) {
                return true;
            }
        }

        if (y <= 70) {
            for (Point anchor : layout.anchors) {
                if (onOrthogonalPath(x, z, layout.centerX, layout.centerZ,
                        anchor.x(), anchor.z(), layout.guardianWidth)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int mainChamberRadiusX(int radiusX, int shellThickness) {
        return Math.max(5, radiusX - shellThickness - 5);
    }

    private static int mainChamberRadiusZ(int radiusZ, int shellThickness) {
        return Math.max(4, radiusZ - shellThickness - 4);
    }

    private static boolean isRupture(Layout layout, int x, int y, int z) {
        if (layout.type != ColonyType.RUPTURED
                || y < HEART_BASE_Y + layout.heartHeight / 3) {
            return false;
        }
        int direction = layout.rotation;
        int edge = (direction & 1) == 0 ? layout.heartRadiusZ : layout.heartWidth / 2;
        int forward = (x - layout.centerX) * directionX(direction)
                + (z - layout.centerZ) * directionZ(direction);
        int sidewaysDirection = (direction + 1) & 3;
        int sideways = (x - layout.centerX) * directionX(sidewaysDirection)
                + (z - layout.centerZ) * directionZ(sidewaysDirection);
        return forward >= edge - 6 && Math.abs(sideways) <= 4;
    }

    private static boolean insideEllipse(int x, int z, int radiusX, int radiusZ) {
        if (radiusX < 1 || radiusZ < 1) return false;
        long radiusXSquared = (long) radiusX * radiusX;
        long radiusZSquared = (long) radiusZ * radiusZ;
        return (long) x * x * radiusZSquared + (long) z * z * radiusXSquared
                <= radiusXSquared * radiusZSquared;
    }

    private static boolean onOrthogonalPath(int x, int z, int startX, int startZ,
            int endX, int endZ, int width) {
        int lowOffset = -(width / 2);
        int highOffset = (width - 1) / 2;
        boolean horizontalLeg = x >= Math.min(startX, endX) && x <= Math.max(startX, endX)
                && z - startZ >= lowOffset && z - startZ <= highOffset;
        boolean verticalLeg = z >= Math.min(startZ, endZ) && z <= Math.max(startZ, endZ)
                && x - endX >= lowOffset && x - endX <= highOffset;
        return horizontalLeg || verticalLeg;
    }

    private static int midpoint(int start, int end) {
        return start + (end - start) / 2;
    }

    private static int directionX(int direction) {
        int normalized = direction & 3;
        return normalized == 1 ? 1 : normalized == 3 ? -1 : 0;
    }

    private static int directionZ(int direction) {
        int normalized = direction & 3;
        return normalized == 0 ? -1 : normalized == 2 ? 1 : 0;
    }

    private static Sample air() { return sample(TissueKind.AIR, 0); }

    private static Sample sample(TissueKind kind, int stage) { return new Sample(kind, stage); }
}
