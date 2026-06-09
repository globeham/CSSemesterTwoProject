/**
 * Authors: Abhineet Bhardwaj, Rohan Nyman, Ishan Singh
 * Date: 6/9/2026
 * Function: Generates the circuit geometry using Catmull-Rom splines, calculates 
 * inner/outer track boundaries, determines valid driving surfaces, 
 * and renders visual track layers.
 */
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Single closed race circuit.
 * Shape: wide horizontal oval with a chicane on the top straight.
 * All control points are ordered consistently counter-clockwise so
 * the Catmull-Rom spline never crosses itself.
 *
 * Layers: grass → concrete run-off → asphalt (even-odd donut) →
 *         infield grass → kerbing → center line → checkpoints →
 *         start/finish → boost pads.
 */
public class Track {

    static final int TRACK_HALF_WIDTH = 55;
    private static final int  RUNOFF_EXTRA      = 24;
    private static final long BOOST_COOLDOWN_MS = 700;

    private final List<Point2D.Double> centerline   = new ArrayList<>();
    private final List<Point2D.Double> outerPoints  = new ArrayList<>();
    private final List<Point2D.Double> innerPoints  = new ArrayList<>();
    private final List<Point2D.Double> runoffPoints = new ArrayList<>();

    private final int[][] boostPads;
    private final long[]  lastBoostTime;
    private final int[]   checkpointIndices;

    // =========================================================================
    // Construction
    // =========================================================================

    public Track() {
        buildCenterline();
        buildBoundaryPoints();
        boostPads     = placeBoostPads();
        lastBoostTime = new long[boostPads.length];
        int n = centerline.size();
        checkpointIndices = new int[]{ n / 4, n / 2, 3 * n / 4 };
    }

    /**
     * Control points for a single, non-self-intersecting oval with a
     * top-straight chicane.  Points go clockwise starting at the
     * start/finish line (top-left area of the straight).
     *
     * Canvas is 800 × 600.  Track stays within x 60–740, y 60–540.
     */
    private void buildCenterline() {
        double[][] ctrl = {
    {120, 150},
    {250, 120},
    {400, 110},
    {550, 120},
    {680, 150},

    {730, 230},
    {740, 300},
    {730, 370},

    {680, 450},
    {550, 480},
    {400, 490},
    {250, 480},
    {120, 450},

    {70, 370},
    {60, 300},
    {70, 230}
};

        int n = ctrl.length;
        int samplesPerSeg = 8;

        for (int s = 0; s < n; s++) {
            double[] p0 = ctrl[(s - 1 + n) % n];
            double[] p1 = ctrl[s];
            double[] p2 = ctrl[(s + 1) % n];
            double[] p3 = ctrl[(s + 2) % n];

            for (int k = 0; k < samplesPerSeg; k++) {
                double t  = k / (double) samplesPerSeg;
                double t2 = t * t, t3 = t2 * t;

                double x = 0.5 * ( (-t3 + 2*t2 - t)      * p0[0]
                                 + (3*t3 - 5*t2 + 2)      * p1[0]
                                 + (-3*t3 + 4*t2 + t)     * p2[0]
                                 + (t3 - t2)              * p3[0] );

                double y = 0.5 * ( (-t3 + 2*t2 - t)      * p0[1]
                                 + (3*t3 - 5*t2 + 2)      * p1[1]
                                 + (-3*t3 + 4*t2 + t)     * p2[1]
                                 + (t3 - t2)              * p3[1] );

                centerline.add(new Point2D.Double(x, y));
            }
        }
    }

    private void buildBoundaryPoints() {
        int n = centerline.size();
        for (int i = 0; i < n; i++) {
            Point2D.Double prev = centerline.get((i - 1 + n) % n);
            Point2D.Double curr = centerline.get(i);
            Point2D.Double next = centerline.get((i + 1) % n);

            // Average tangent from prev→curr and curr→next
            double dx1 = curr.x - prev.x, dy1 = curr.y - prev.y;
            double dx2 = next.x - curr.x, dy2 = next.y - curr.y;
            double l1  = Math.max(1, Math.hypot(dx1, dy1));
            double l2  = Math.max(1, Math.hypot(dx2, dy2));

            double tx = dx1/l1 + dx2/l2;
            double ty = dy1/l1 + dy2/l2;
            double tl = Math.hypot(tx, ty);
            if (tl == 0) { tx = dx2/l2; ty = dy2/l2; tl = 1; }
            tx /= tl; ty /= tl;

            // Normal points outward (left side of travel direction = outside for CW loop)
            double nx = -ty, ny = tx;

            outerPoints .add(new Point2D.Double(curr.x + nx * TRACK_HALF_WIDTH,
                                                curr.y + ny * TRACK_HALF_WIDTH));
            innerPoints .add(new Point2D.Double(curr.x - nx * TRACK_HALF_WIDTH,
                                                curr.y - ny * TRACK_HALF_WIDTH));
            runoffPoints.add(new Point2D.Double(curr.x + nx * (TRACK_HALF_WIDTH + RUNOFF_EXTRA),
                                                curr.y + ny * (TRACK_HALF_WIDTH + RUNOFF_EXTRA)));
        }
    }

    private int[][] placeBoostPads() {
        int n = centerline.size(), count = 3;
        int[][] pads = new int[count][2];
        for (int i = 0; i < count; i++) {
            int idx = (n / count) * i + n / (count * 3);
            pads[i] = new int[]{
                (int) centerline.get(idx).x,
                (int) centerline.get(idx).y
            };
        }
        return pads;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public List<Point2D.Double> getCenterline()        { return centerline; }
    public int[]                getCheckpointIndices() { return checkpointIndices; }

    /** Lateral start positions, spread across the track width. */
    public int[] getStartPosition(int playerIdx, int totalPlayers) {
        Point2D.Double p = centerline.get(0);
        double a  = getStartAngle();
        // Perpendicular to travel direction
        double px = -Math.sin(a), py = Math.cos(a);
        double spacing = (TRACK_HALF_WIDTH * 1.2) / Math.max(1, totalPlayers - 1);
        double offset  = playerIdx * spacing - (totalPlayers - 1) * spacing / 2.0;
        return new int[]{ (int)(p.x + px * offset), (int)(p.y + py * offset) };
    }

    public double getStartAngle() {
        return Math.atan2(
            centerline.get(1).y - centerline.get(0).y,
            centerline.get(1).x - centerline.get(0).x);
    }

    public boolean isOnTrack(double x, double y) {
        int n = centerline.size();
        for (int i = 0; i < n; i++) {
            Point2D.Double a = centerline.get(i);
            Point2D.Double b = centerline.get((i + 1) % n);
            if (ptSegDist(x, y, a.x, a.y, b.x, b.y) <= TRACK_HALF_WIDTH) return true;
        }
        return false;
    }

    public int getNearestCenterlineIdx(double x, double y) {
        double minD = Double.MAX_VALUE;
        int best = 0, n = centerline.size();
        for (int i = 0; i < n; i++) {
            double d = Math.hypot(x - centerline.get(i).x, y - centerline.get(i).y);
            if (d < minD) { minD = d; best = i; }
        }
        return best;
    }

    /** Returns a push vector moving the point back inside the track boundary. */
    public double[] getWallPush(double x, double y) {
        int n = centerline.size();
        double minDist = Double.MAX_VALUE;
        Point2D.Double nearPt = null;

        for (int i = 0; i < n; i++) {
            Point2D.Double a = centerline.get(i);
            Point2D.Double b = centerline.get((i + 1) % n);
            double d = ptSegDist(x, y, a.x, a.y, b.x, b.y);
            if (d < minDist) {
                minDist = d;
                nearPt  = nearestOnSeg(x, y, a.x, a.y, b.x, b.y);
            }
        }

        if (nearPt == null || minDist <= TRACK_HALF_WIDTH) return new double[]{0, 0};

        double excess = minDist - TRACK_HALF_WIDTH;
        double dx = nearPt.x - x, dy = nearPt.y - y;
        double dist = Math.hypot(dx, dy);
        if (dist < 1) return new double[]{0, 0};
        return new double[]{ dx / dist * (excess + 2), dy / dist * (excess + 2) };
    }

    public boolean checkBoost(double x, double y) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < boostPads.length; i++) {
            int[] p = boostPads[i];
            if (Math.hypot(x - p[0], y - p[1]) < 28
                    && now - lastBoostTime[i] > BOOST_COOLDOWN_MS) {
                lastBoostTime[i] = now;
                return true;
            }
        }
        return false;
    }

    public boolean crossedStartLine(double prevX, double prevY,
                                    double x, double y, double carAngle) {
        Point2D.Double a = centerline.get(0);
        Point2D.Double b = centerline.get(1);
        double tx = b.x - a.x, ty = b.y - a.y;
        double tl = Math.hypot(tx, ty);
        if (tl == 0) return false;
        tx /= tl; ty /= tl;
        double nx = -ty, ny = tx;

        double prevS = (prevX - a.x) * nx + (prevY - a.y) * ny;
        double currS = (x     - a.x) * nx + (y     - a.y) * ny;

        if (prevS < 0 && currS >= 0) {
            // Must be near the start line segment
            if (ptSegDist(x, y,
                          outerPoints.get(0).x, outerPoints.get(0).y,
                          innerPoints.get(0).x, innerPoints.get(0).y) > TRACK_HALF_WIDTH + 10) {
                return false;
            }
            // Must be travelling in the correct direction
            return Math.cos(carAngle) * tx + Math.sin(carAngle) * ty > 0;
        }
        return false;
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Outer grass
        g2.setColor(new Color(38, 148, 42));
        g2.fillRect(0, 0, 800, 600);
        g2.setColor(new Color(32, 135, 36, 80));
        for (int y = 0; y < 600; y += 24) g2.fillRect(0, y, 800, 12);

        // 2. Concrete run-off
        g2.setColor(new Color(155, 152, 143));
        g2.fill(buildPath(runoffPoints));

        // 3. Asphalt track (even-odd donut: outer minus inner)
        GeneralPath trackShape = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
        trackShape.append(buildPath(outerPoints), false);
        trackShape.append(buildPath(innerPoints), false);
        g2.setColor(new Color(50, 50, 50));
        g2.fill(trackShape);

        // 4. Infield grass
        g2.setColor(new Color(32, 138, 36));
        g2.fill(buildPath(innerPoints));
        g2.setColor(new Color(26, 120, 30, 70));
        for (int y = 0; y < 600; y += 20) g2.fillRect(0, y, 800, 10);
        g2.setColor(new Color(32, 138, 36));
        g2.fill(buildPath(innerPoints));

        // 5. Outer kerbing: red base + white dashes
        GeneralPath outerPath = buildPath(outerPoints);
        g2.setColor(new Color(195, 28, 28));
        g2.setStroke(new BasicStroke(9f));
        g2.draw(outerPath);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                     1f, new float[]{18f, 18f}, 9f));
        g2.draw(outerPath);

        // 6. Inner kerbing: yellow base + white dashes
        GeneralPath innerPath = buildPath(innerPoints);
        g2.setColor(new Color(225, 185, 0));
        g2.setStroke(new BasicStroke(5f));
        g2.draw(innerPath);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                     1f, new float[]{14f, 14f}, 7f));
        g2.draw(innerPath);

        // 7. Dashed center line
        g2.setColor(new Color(255, 255, 255, 90));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                     1f, new float[]{14f, 10f}, 0f));
        g2.draw(buildPath(centerline));

        // 8. Checkpoint dashes
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                     1f, new float[]{8f, 6f}, 0f));
        g2.setColor(new Color(120, 200, 255, 110));
        for (int ci : checkpointIndices) {
            g2.drawLine((int) outerPoints.get(ci).x, (int) outerPoints.get(ci).y,
                        (int) innerPoints.get(ci).x, (int) innerPoints.get(ci).y);
        }

        // 9. Start/finish line
        drawStartLine(g2);

        // 10. Boost pads
        drawBoostPads(g2);
    }

    private GeneralPath buildPath(List<Point2D.Double> pts) {
        GeneralPath p = new GeneralPath();
        for (int i = 0; i < pts.size(); i++) {
            if (i == 0) p.moveTo(pts.get(i).x, pts.get(i).y);
            else        p.lineTo(pts.get(i).x, pts.get(i).y);
        }
        p.closePath();
        return p;
    }

    private void drawStartLine(Graphics2D g2) {
        Point2D.Double o  = outerPoints.get(0);
        Point2D.Double in = innerPoints.get(0);
        double ang = getStartAngle();
        double dx = Math.cos(ang) * 6, dy = Math.sin(ang) * 6;
        int tiles = 10;
        for (int i = 0; i < tiles; i++) {
            double t0 = i       / (double) tiles;
            double t1 = (i + 1) / (double) tiles;
            double x0 = o.x + (in.x - o.x) * t0, y0 = o.y + (in.y - o.y) * t0;
            double x1 = o.x + (in.x - o.x) * t1, y1 = o.y + (in.y - o.y) * t1;
            GeneralPath tile = new GeneralPath();
            tile.moveTo(x0 - dx, y0 - dy); tile.lineTo(x1 - dx, y1 - dy);
            tile.lineTo(x1 + dx, y1 + dy); tile.lineTo(x0 + dx, y0 + dy);
            tile.closePath();
            g2.setColor(i % 2 == 0 ? Color.WHITE : Color.BLACK);
            g2.fill(tile);
        }
    }

    private void drawBoostPads(Graphics2D g2) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < boostPads.length; i++) {
            int[] p      = boostPads[i];
            boolean active = (now - lastBoostTime[i]) > BOOST_COOLDOWN_MS;

            if (active) {
                long  phase = (now / 80) % 20;
                int   glow  = (int)(Math.sin(phase * Math.PI / 10.0) * 20 + 40);
                g2.setColor(new Color(255, 220, 60, glow));
                g2.fillOval(p[0] - 22, p[1] - 22, 44, 44);
            }

            g2.setColor(active ? new Color(255, 210, 40) : new Color(110, 90, 18));
            g2.fillOval(p[0] - 13, p[1] - 13, 26, 26);
            g2.setColor(active ? new Color(255, 250, 160) : new Color(70, 60, 10));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(p[0] - 13, p[1] - 13, 26, 26);

            // Arrow
            g2.setColor(active ? new Color(40, 30, 0) : new Color(60, 50, 0));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(p[0] - 5, p[1],     p[0] + 5, p[1]);
            g2.drawLine(p[0] + 1, p[1] - 5, p[0] + 5, p[1]);
            g2.drawLine(p[0] + 1, p[1] + 5, p[0] + 5, p[1]);
        }
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    private double ptSegDist(double px, double py,
                              double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double ls = dx*dx + dy*dy;
        if (ls == 0) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px-x1)*dx + (py-y1)*dy) / ls));
        return Math.hypot(px - (x1 + t*dx), py - (y1 + t*dy));
    }

    private Point2D.Double nearestOnSeg(double px, double py,
                                         double x1, double y1, double x2, double y2) {
        double dx = x2-x1, dy = y2-y1, ls = dx*dx + dy*dy;
        if (ls == 0) return new Point2D.Double(x1, y1);
        double t = Math.max(0, Math.min(1, ((px-x1)*dx + (py-y1)*dy) / ls));
        return new Point2D.Double(x1 + t*dx, y1 + t*dy);
    }
}