import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

public class Track {
    private static final int TRACK_HALF_WIDTH = 70; // half-width on each side of centerline (used for start line visuals)
    // Index in centerline[] that acts as the start/finish line.
    // Must sit on the horizontal stretch of row 1, well to the right of col 2,
    // so that cars coming from col 2 (x≈176) have negative signed distance.
    private static final int START_LINE_INDEX = 3;
    private final List<Point2D.Double> centerline;
    private final List<Point2D.Double> outerPoints;
    private final List<Point2D.Double> innerPoints;
    private int[][] boostPads;
    private long[] lastBoostTime;
    private static final long BOOST_COOLDOWN_MS = 700;
    // Tile images
    private BufferedImage grassTile;
    private BufferedImage[] roadMainTiles;
    // Grid-based track
    private int tileSize = 64;
    private int gridWidth = 12;
    private int gridHeight = 9;
    // 0 = grass, 1 = road
    private int[][] grid;
    // World offset for the grid (computed once in buildGridTrack)
    private int gridOffsetX, gridOffsetY;

    public Track() {
        centerline = new ArrayList<>();
        outerPoints = new ArrayList<>();
        innerPoints = new ArrayList<>();

        grid = new int[gridWidth][gridHeight];
        buildGridTrack();

        buildBoundaryPoints();
        boostPads = placeboostPadsOnTrack();
        lastBoostTime = new long[boostPads.length];
        loadTiles();
    }

    // Load tile images from the sibling "Racetrack Tiled" folder.
    private void loadTiles() {
        try {
            // Get the working directory and build paths from there
            String workingDir = System.getProperty("user.dir");
            String base = workingDir + File.separator + "Racetrack Tiled";
            File bg = new File(base + File.separator + "Background_Tiles" + File.separator + "Grass_Tile.png");
            System.out.println("Looking for grass tile at: " + bg.getAbsolutePath());
            if (bg.exists()) {
                grassTile = ImageIO.read(bg);
                System.out.println("Successfully loaded grass tile");
            } else {
                System.err.println("Grass tile not found at: " + bg.getAbsolutePath());
            }
            File roadDir = new File(base + File.separator + "Road_01");
            System.out.println("Looking for road tiles at: " + roadDir.getAbsolutePath());
            // Expect folders Road_01_Tile_01 .. Road_01_Tile_08
            roadMainTiles = new BufferedImage[9];
            if (roadDir.exists() && roadDir.isDirectory()) {
                for (int id = 1; id <= 8; id++) {
                    String folder = String.format("Road_01_Tile_%02d", id);
                    File d = new File(roadDir, folder);
                    BufferedImage img = null;
                    // Prefer Layers/Road_Main.png
                    File layers = new File(d, "Layers");
                    File main = new File(layers, "Road_Main.png");
                    if (main.exists()) {
                        try { img = ImageIO.read(main); } catch (IOException e) { img = null; }
                    }
                    // Fallback: any PNG directly in the folder
                    if (img == null && d.exists() && d.isDirectory()) {
                        File[] pngs = d.listFiles((f) -> f.getName().toLowerCase().endsWith(".png"));
                        if (pngs != null && pngs.length > 0) {
                            try { img = ImageIO.read(pngs[0]); } catch (IOException e) { img = null; }
                        }
                    }
                    roadMainTiles[id] = img;
                }
                // find a default tile if some are missing
                BufferedImage defaultTile = null;
                for (int i=1;i<roadMainTiles.length;i++) if (roadMainTiles[i] != null) { defaultTile = roadMainTiles[i]; break; }
                if (defaultTile != null) {
                    for (int i=1;i<roadMainTiles.length;i++) if (roadMainTiles[i] == null) roadMainTiles[i] = defaultTile;
                }
            }
        } catch (Exception e) {
            // silent fallback to procedural rendering
            grassTile = null;
            roadMainTiles = null;
        }
    }

    // Build sampled oval centerline with a couple of localized twists
    private List<Point2D.Double> buildOvalWithTwists(int cx, int cy, int rx, int ry, int samples) {
        List<Point2D.Double> pts = new ArrayList<>();
        for (int i = 0; i < samples; i++) {
            double t = (i / (double) samples) * Math.PI * 2;
            double x = cx + rx * Math.cos(t);
            double y = cy + ry * Math.sin(t);

            // First twist near angle ~0.9*pi
            double a1 = Math.abs(normalizeAngle(t - Math.PI * 0.9));
            if (a1 < 0.6) {
                double twist = Math.sin((0.6 - a1) * 3) * 18;
                double nx = Math.cos(t);
                double ny = Math.sin(t);
                x += nx * twist * 0.3;
                y += ny * twist * 0.3;
            }

            // Second twist near angle ~1.9*pi
            double a2 = Math.abs(normalizeAngle(t - Math.PI * 1.9));
            if (a2 < 0.6) {
                double twist = -Math.sin((0.6 - a2) * 3) * 12;
                double nx = Math.cos(t);
                double ny = Math.sin(t);
                x += nx * twist * 0.3;
                y += ny * twist * 0.3;
            }

            pts.add(new Point2D.Double(x, y));
        }
        return pts;
    }

    // Build a winding track on the grid and populate `centerline` in order.
    //
    // Track shape (12-wide x 9-tall grid, 0=grass, 1=road):
    //   row 1: cols 2-8  (top horizontal)
    //   col 8: rows 1-3  (right drop)
    //   row 3: cols 8-10 (mid-right horizontal)
    //   col10: rows 3-7  (far-right vertical)
    //   row 7: cols 4-10 (bottom horizontal)
    //   col 4: rows 5-7  (left bump vertical)
    //   row 5: cols 2-4  (mid-left horizontal)
    //   col 2: rows 1-5  (left vertical)
    //
    // Clockwise centerline walk starts at (2,1) which is in the top-left corner.
    // The start/finish line is placed at START_LINE_INDEX=3 (cell 5,1) so that
    // cars coming from col 2 (x≈176) have negative signed distance and trigger
    // the crossing detector on every lap.
    private void buildGridTrack() {
        centerline.clear();
        int worldW = 800, worldH = 600;
        gridOffsetX = (worldW - gridWidth * tileSize) / 2;
        gridOffsetY = (worldH - gridHeight * tileSize) / 2;

        for (int x = 0; x < gridWidth; x++) for (int y = 0; y < gridHeight; y++) grid[x][y] = 0;

        // top horizontal
        for (int x = 2; x <= 8; x++) grid[x][1] = 1;
        // right drop (row 1 already set)
        for (int y = 2; y <= 3; y++) grid[8][y] = 1;
        // mid-right horizontal (col 8 row 3 already set)
        for (int x = 9; x <= 10; x++) grid[x][3] = 1;
        // far-right vertical (row 3 already set)
        for (int y = 4; y <= 7; y++) grid[10][y] = 1;
        // bottom horizontal (col 10 already set)
        for (int x = 4; x <= 9; x++) grid[x][7] = 1;
        // left bump vertical (row 7 already set)
        for (int y = 5; y <= 6; y++) grid[4][y] = 1;
        // mid-left horizontal (col 4 already set)
        for (int x = 2; x <= 3; x++) grid[x][5] = 1;
        // left vertical (row 5 already set)
        for (int y = 1; y <= 4; y++) grid[2][y] = 1;

        // Ordered clockwise centerline walk
        for (int x = 2; x <= 8; x++) addCellCenter(x, 1);           // top →
        for (int y = 2; y <= 3; y++) addCellCenter(8, y);            // right drop ↓
        for (int x = 9; x <= 10; x++) addCellCenter(x, 3);           // mid-right →
        for (int y = 4; y <= 7; y++) addCellCenter(10, y);           // far-right ↓
        for (int x = 9; x >= 4; x--) addCellCenter(x, 7);            // bottom ←
        for (int y = 6; y >= 5; y--) addCellCenter(4, y);            // left bump ↑
        for (int x = 3; x >= 2; x--) addCellCenter(x, 5);            // mid-left ←
        for (int y = 4; y >= 2; y--) addCellCenter(2, y);            // left vertical ↑
        // (2,1) is already index 0 — loop closed
    }

    private void addCellCenter(int gx, int gy) {
        addCellCenterToCenterline(gx, gy, gridOffsetX, gridOffsetY);
    }

    private void addCellCenterToCenterline(int gx, int gy, int offsetX, int offsetY) {
        double cx = offsetX + gx * tileSize + tileSize / 2.0;
        double cy = offsetY + gy * tileSize + tileSize / 2.0;
        Point2D.Double p = new Point2D.Double(cx, cy);
        // avoid adding nearly-duplicate consecutive points
        if (centerline.isEmpty()) centerline.add(p);
        else {
            Point2D.Double last = centerline.get(centerline.size() - 1);
            if (Math.hypot(last.x - p.x, last.y - p.y) > 1.0) centerline.add(p);
        }
    }

    // Compute neighbor mask and map to a road tile id (1..8). Bits: up=1,right=2,down=4,left=8
    private int getTileIdForCell(int gx, int gy) {
        int mask = 0;
        if (gy - 1 >= 0 && grid[gx][gy - 1] == 1) mask |= 1; // up
        if (gx + 1 < gridWidth && grid[gx + 1][gy] == 1) mask |= 2; // right
        if (gy + 1 < gridHeight && grid[gx][gy + 1] == 1) mask |= 4; // down
        if (gx - 1 >= 0 && grid[gx - 1][gy] == 1) mask |= 8; // left

        switch (mask) {
            case 10: // left+right
                return 1; // Tile_01 -> horizontal
            case 5: // up+down
                return 2; // Tile_02 -> vertical
            case 3: // up+right
                return 3; // corner
            case 6: // right+down
                return 4;
            case 12: // down+left
                return 5;
            case 9: // left+up
                return 6;
            case 7: // up+right+down
                return 7; // T-join
            case 13: // up+down+left
                return 8;
            default:
                // single connection or isolated: default to straight (1)
                if (mask == 2 || mask == 8) return 1;
                if (mask == 1 || mask == 4) return 2;
                return 1;
        }
    }

    // Return {tileId, rotationDegrees} where tileId uses the loaded roadMainTiles index.
    // We'll use tile 3 as straight and tile 1 as corner (rotated).
    private int[] getTileAndRotation(int gx, int gy) {
        int mask = 0;
        if (gy - 1 >= 0 && grid[gx][gy - 1] == 1) mask |= 1; // up
        if (gx + 1 < gridWidth && grid[gx + 1][gy] == 1) mask |= 2; // right
        if (gy + 1 < gridHeight && grid[gx][gy + 1] == 1) mask |= 4; // down
        if (gx - 1 >= 0 && grid[gx - 1][gy] == 1) mask |= 8; // left

        // Straight horizontal
        if (mask == 10) return new int[]{3, 0}; // tile_03 horizontal
        // Straight vertical
        if (mask == 5) return new int[]{3, 90}; // tile_03 rotated 90

        // Corners: use tile_01 rotated accordingly
        if (mask == 3) return new int[]{1, 0};   // up + right
        if (mask == 6) return new int[]{1, 90};  // right + down
        if (mask == 12) return new int[]{1, 180}; // down + left
        if (mask == 9) return new int[]{1, 270}; // left + up

        // Fallbacks: for T-joins or single connections, prefer straight
        if ((mask & 3) == 3 || (mask & 6) == 6 || (mask & 12) == 12 || (mask & 9) == 9) {
            return new int[]{1, 0};
        }
        if ((mask & 10) == 10) return new int[]{3, 0};
        if ((mask & 5) == 5) return new int[]{3, 90};

        // default to straight horizontal
        return new int[]{3, 0};
    }

    /**
     * Pre-compute smooth outer and inner boundary points using averaged
     * perpendicular normals, so adjacent segments don't flip direction.
     */
    private void buildBoundaryPoints() {
        int n = centerline.size();
        for (int i = 0; i < n; i++) {
            Point2D.Double prev = centerline.get((i - 1 + n) % n);
            Point2D.Double curr = centerline.get(i);
            Point2D.Double next = centerline.get((i + 1) % n);

            // Average of the two adjacent segment directions gives a smooth normal
            double dx1 = curr.x - prev.x, dy1 = curr.y - prev.y;
            double dx2 = next.x - curr.x, dy2 = next.y - curr.y;
            double len1 = Math.hypot(dx1, dy1), len2 = Math.hypot(dx2, dy2);
            if (len1 == 0) len1 = 1;
            if (len2 == 0) len2 = 1;

            // Unit tangent = average of normalized segment directions
            double tx = dx1 / len1 + dx2 / len2;
            double ty = dy1 / len1 + dy2 / len2;
            double tlen = Math.hypot(tx, ty);
            if (tlen == 0) { tx = dx2 / len2; ty = dy2 / len2; tlen = 1; }
            tx /= tlen; ty /= tlen;

            // Left-perpendicular normal (outer side)
            double nx = -ty, ny = tx;

            outerPoints.add(new Point2D.Double(curr.x + nx * TRACK_HALF_WIDTH,
                                               curr.y + ny * TRACK_HALF_WIDTH));
            innerPoints.add(new Point2D.Double(curr.x - nx * TRACK_HALF_WIDTH,
                                               curr.y - ny * TRACK_HALF_WIDTH));
        }
    }

    /**
     * Place boost pads on actual centerline points so they're always on-track.
     * Spreads them evenly around the loop.
     */
    private int[][] placeboostPadsOnTrack() {
        int n = centerline.size();
        int count = 3;
        int[][] pads = new int[count][2];
        for (int i = 0; i < count; i++) {
            int idx = (n / count) * i + n / (count * 2); // stagger from start line
            Point2D.Double p = centerline.get(idx);
            pads[i] = new int[]{ (int) p.x, (int) p.y };
        }
        return pads;
    }

    private double normalizeAngle(double a) {
        while (a > Math.PI)  a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the world position for a given player's start slot.
     * Players are spread sideways across the track width.
     */
    public int[] getStartPosition(int playerIdx, int totalPlayers) {
        // Place cars just before the start/finish line (one cell to the left of START_LINE_INDEX)
        Point2D.Double p = centerline.get(START_LINE_INDEX - 1);
        double angle = getStartAngle();
        double px = -Math.sin(angle);
        double py =  Math.cos(angle);
        // Tighter spread to fit two cars side-by-side in one tile
        double spacing = tileSize * 0.5;
        double offset = playerIdx * spacing - (totalPlayers - 1) * spacing / 2.0;
        return new int[]{ (int)(p.x + px * offset), (int)(p.y + py * offset) };
    }

    /** Angle of the track at the start/finish line (radians). */
    public double getStartAngle() {
        Point2D.Double a = centerline.get(START_LINE_INDEX);
        Point2D.Double b = centerline.get(START_LINE_INDEX + 1);
        return Math.atan2(b.y - a.y, b.x - a.x);
    }

    /**
     * Signed distance along the track's forward direction from the start/finish line.
     * Negative = before the line, positive = past it.
     */
    public double getStartLineSignedDistance(double x, double y) {
        Point2D.Double a = centerline.get(START_LINE_INDEX);
        Point2D.Double b = centerline.get(START_LINE_INDEX + 1);
        double tx = b.x - a.x, ty = b.y - a.y;
        double tlen = Math.hypot(tx, ty);
        if (tlen == 0) return 0;
        tx /= tlen; ty /= tlen;
        // Project onto the forward (tangent) direction — negative means "before the line"
        return (x - a.x) * tx + (y - a.y) * ty;
    }

    /** True if the grid cell at (x,y) is a road tile. */
    public boolean isOnTrack(double x, double y) {
        int gx = (int)((x - gridOffsetX) / tileSize);
        int gy = (int)((y - gridOffsetY) / tileSize);
        if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) return false;
        return grid[gx][gy] == 1;
    }


    /**
     * Returns true (and records the time) when a car drives over a boost pad.
     * Pad recharges after BOOST_COOLDOWN_MS.
     */
    public boolean checkBoost(double x, double y) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < boostPads.length; i++) {
            int[] p = boostPads[i];
            if (Math.hypot(x - p[0], y - p[1]) < 28) {
                if (now - lastBoostTime[i] > BOOST_COOLDOWN_MS) {
                    lastBoostTime[i] = now;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Detects a forward crossing of the start/finish line.
     * Uses a signed-area test against the line's normal so direction matters.
     */
    public boolean crossedStartLine(double prevX, double prevY,
                                    double x, double y, double carAngle) {
        Point2D.Double a = centerline.get(START_LINE_INDEX);
        Point2D.Double b = centerline.get(START_LINE_INDEX + 1);

        // Forward direction (tangent) at the start/finish line
        double tx = b.x - a.x, ty = b.y - a.y;
        double tlen = Math.hypot(tx, ty);
        if (tlen == 0) return false;
        tx /= tlen; ty /= tlen;

        // Project positions onto the forward direction
        double prevSigned = (prevX - a.x) * tx + (prevY - a.y) * ty;
        double currSigned = (x    - a.x) * tx + (y    - a.y) * ty;

        if (prevSigned < 0 && currSigned >= 0) {
            // Car must be laterally close to the start/finish line
            double nx = -ty, ny = tx;
            double lateral = Math.abs((x - a.x) * nx + (y - a.y) * ny);
            if (lateral > tileSize) return false;
            // Car must be heading the right way
            double carDot = Math.cos(carAngle) * tx + Math.sin(carAngle) * ty;
            return carDot > 0;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // --- Background (tiled grass if available) ---
        if (grassTile != null) {
            int tw = grassTile.getWidth();
            int th = grassTile.getHeight();
            for (int x = 0; x < 800; x += tw) {
                for (int y = 0; y < 600; y += th) {
                    g2.drawImage(grassTile, x, y, null);
                }
            }
        } else {
            g2.setColor(new Color(34, 120, 34));
            g2.fillRect(0, 0, 800, 600);
        }

        // --- Build outer and inner closed paths ---
        GeneralPath outerPath = buildPath(outerPoints);
        GeneralPath innerPath = buildPath(innerPoints);

        // --- Road: draw tiles from the grid if available, otherwise procedural asphalt ---
        int worldW = 800, worldH = 600;
        int offsetX = (worldW - gridWidth * tileSize) / 2;
        int offsetY = (worldH - gridHeight * tileSize) / 2;

        if (roadMainTiles != null) {
            for (int gx = 0; gx < gridWidth; gx++) {
                for (int gy = 0; gy < gridHeight; gy++) {
                    int px = offsetX + gx * tileSize;
                    int py = offsetY + gy * tileSize;
                    if (grid[gx][gy] == 1) {
                        int[] tr = getTileAndRotation(gx, gy);
                        int id = tr[0]; int rot = tr[1];
                        BufferedImage tileImg = (id >= 1 && id < roadMainTiles.length) ? roadMainTiles[id] : null;
                        if (tileImg != null) {
                            int iw = tileImg.getWidth();
                            int ih = tileImg.getHeight();
                            int m = Math.max(4, Math.min(12, Math.min(iw, ih) / 16));
                            BufferedImage src = tileImg.getSubimage(m, m, iw - 2*m, ih - 2*m);
                            AffineTransform at = new AffineTransform();
                            at.translate(px + tileSize/2.0, py + tileSize/2.0);
                            at.rotate(Math.toRadians(rot));
                            at.scale(tileSize / (double) src.getWidth(), tileSize / (double) src.getHeight());
                            at.translate(-src.getWidth()/2.0, -src.getHeight()/2.0);
                            g2.drawImage(src, at, null);
                            
                        } else { g2.setColor(new Color(45,45,45)); g2.fillRect(px, py, tileSize, tileSize); }
                    }
                }
            }
        } else {
            // fallback: procedural asphalt and borders
            GeneralPath trackShape = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
            trackShape.append(outerPath, false);
            trackShape.append(innerPath, false);
            g2.setColor(new Color(45, 45, 45));
            g2.fill(trackShape);

            // no borders for tiled cohesive look
        }

        // (centerline rendering removed for a cohesive tiled look)

        // --- Start/finish line ---
        drawStartLine(g2);

        // --- Boost pads ---
        drawBoostPads(g2);
    }

    private GeneralPath buildPath(List<Point2D.Double> pts) {
        GeneralPath path = new GeneralPath();
        for (int i = 0; i < pts.size(); i++) {
            Point2D.Double p = pts.get(i);
            if (i == 0) path.moveTo(p.x, p.y);
            else        path.lineTo(p.x, p.y);
        }
        path.closePath();
        return path;
    }

    private void drawStartLine(Graphics2D g2) {
        Point2D.Double o = outerPoints.get(0);
        Point2D.Double in = innerPoints.get(0);

        // Draw a checkered start line between inner and outer boundary
        int tiles = 8;
        double ox = o.x, oy = o.y;
        double ix = in.x, iy = in.y;
        for (int i = 0; i < tiles; i++) {
            double t0 = i / (double) tiles;
            double t1 = (i + 1) / (double) tiles;
            double x0 = ox + (ix - ox) * t0, y0 = oy + (iy - oy) * t0;
            double x1 = ox + (ix - ox) * t1, y1 = oy + (iy - oy) * t1;

            // Perpendicular offset to give the stripe some depth
            double ang = getStartAngle();
            double dx = Math.cos(ang) * 6, dy = Math.sin(ang) * 6;

            GeneralPath tile = new GeneralPath();
            tile.moveTo(x0 - dx, y0 - dy);
            tile.lineTo(x1 - dx, y1 - dy);
            tile.lineTo(x1 + dx, y1 + dy);
            tile.lineTo(x0 + dx, y0 + dy);
            tile.closePath();
            g2.setColor(i % 2 == 0 ? Color.WHITE : Color.BLACK);
            g2.fill(tile);
        }
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(Color.WHITE);
    }

    private void drawBoostPads(Graphics2D g2) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < boostPads.length; i++) {
            int[] p = boostPads[i];
            boolean active = (now - lastBoostTime[i]) > BOOST_COOLDOWN_MS;
            Color fill   = active ? new Color(255, 220, 50)  : new Color(120, 100, 20);
            Color border = active ? new Color(255, 255, 180) : new Color(80,  70,  10);

            // Glow ring when active
            if (active) {
                g2.setColor(new Color(255, 240, 100, 60));
                g2.fillOval(p[0] - 18, p[1] - 18, 36, 36);
            }
            g2.setColor(fill);
            g2.fillOval(p[0] - 11, p[1] - 11, 22, 22);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(p[0] - 11, p[1] - 11, 22, 22);

            // Arrow symbol
            g2.setColor(active ? Color.BLACK : new Color(50, 45, 0));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(p[0] - 4, p[1], p[0] + 4, p[1]);
            g2.drawLine(p[0] + 1, p[1] - 4, p[0] + 4, p[1]);
            g2.drawLine(p[0] + 1, p[1] + 4, p[0] + 4, p[1]);
        }
    }
}