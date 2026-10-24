package com.lhbdhome.test;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 贪吃蛇小游戏。
 *
 * <p>操作：方向键 / WASD 控制方向，空格暂停或继续，R 重新开始。
 */
public class SnakeGame extends JFrame {

    public SnakeGame() {
        setTitle("贪吃蛇");
        GamePanel panel = new GamePanel();
        setContentPane(panel);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        panel.requestFocusInWindow();
    }

    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SnakeGame().setVisible(true));
    }

    private enum State { READY, RUNNING, PAUSED, OVER, WIN }

    private enum Dir {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        final int dx;
        final int dy;

        Dir(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        boolean isOpposite(Dir other) {
            return dx + other.dx == 0 && dy + other.dy == 0;
        }
    }

    private static final class GamePanel extends JPanel {

        private static final int COLS = 20;
        private static final int ROWS = 20;
        private static final int CELL = 30;
        private static final int HUD = 66;
        private static final int BOARD_W = COLS * CELL;
        private static final int BOARD_H = ROWS * CELL;

        private static final double BASE_STEP = 0.135;
        private static final double MIN_STEP = 0.070;
        private static final double SPEED_UP = 0.0035;

        private static final Color BG_TOP = Color.WHITE;
        private static final Color BG_BOTTOM = new Color(0xF1, 0xF4, 0xFA);
        private static final Color GRID = new Color(0xE3, 0xE9, 0xF3);
        private static final Color BORDER = new Color(0x4C, 0x7C, 0xFF, 150);
        private static final Color HEAD = new Color(0x2F, 0xC2, 0x6D);
        private static final Color TAIL = new Color(0x0D, 0x76, 0x43);
        private static final Color FOOD = new Color(0xE5, 0x3A, 0x3A);
        private static final Color TEXT = new Color(0x1C, 0x24, 0x33);
        private static final Color MUTED = new Color(0x6B, 0x7A, 0x91);
        private static final Color ACCENT_BEST = new Color(0xC4, 0x86, 0x00);
        private static final Color ACCENT_LEN = new Color(0x1F, 0x9A, 0x5B);

        private final Deque<Point> snake = new ArrayDeque<>();
        private final Deque<Dir> pending = new ArrayDeque<>();
        private final Random random = new Random();

        private State state = State.READY;
        private Dir dir = Dir.RIGHT;
        private Point food = new Point(-1, -1);
        private int score;
        private int best;
        private double stepSeconds = BASE_STEP;
        private double accumulator;
        private double animTime;
        private long lastNanos = System.nanoTime();

        GamePanel() {
            setPreferredSize(new Dimension(BOARD_W, BOARD_H + HUD));
            setBackground(BG_TOP);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false);
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    handleKey(e.getKeyCode());
                }
            });
            reset();
            new Timer(16, e -> loop()).start();
        }

        private void reset() {
            snake.clear();
            pending.clear();
            int cx = COLS / 2;
            int cy = ROWS / 2;
            for (int i = 0; i < 3; i++) {
                snake.addLast(new Point(cx - i, cy));
            }
            dir = Dir.RIGHT;
            score = 0;
            stepSeconds = BASE_STEP;
            accumulator = 0;
            state = State.READY;
            spawnFood();
            repaint();
        }

        private void spawnFood() {
            List<Point> free = new ArrayList<>(COLS * ROWS);
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < COLS; x++) {
                    Point p = new Point(x, y);
                    if (!snake.contains(p)) {
                        free.add(p);
                    }
                }
            }
            if (free.isEmpty()) {
                state = State.WIN;
                food = new Point(-1, -1);
                return;
            }
            food = free.get(random.nextInt(free.size()));
        }

        private void loop() {
            long now = System.nanoTime();
            double dt = Math.min((now - lastNanos) / 1e9, 0.1);
            lastNanos = now;
            animTime += dt;

            if (state == State.RUNNING) {
                accumulator += dt;
                while (accumulator >= stepSeconds) {
                    accumulator -= stepSeconds;
                    step();
                    if (state != State.RUNNING) {
                        accumulator = 0;
                        break;
                    }
                }
            }
            repaint();
        }

        private void step() {
            Dir next = pending.pollFirst();
            if (next != null) {
                dir = next;
            }

            Point head = snake.peekFirst();
            Point newHead = new Point(head.x + dir.dx, head.y + dir.dy);

            if (newHead.x < 0 || newHead.x >= COLS || newHead.y < 0 || newHead.y >= ROWS) {
                state = State.OVER;
                return;
            }

            boolean grow = newHead.equals(food);
            Point tail = snake.peekLast();
            boolean hitsBody = !newHead.equals(tail) && snake.contains(newHead);
            if (!grow && hitsBody) {
                state = State.OVER;
                return;
            }
            if (grow && snake.contains(newHead)) {
                state = State.OVER;
                return;
            }

            snake.addFirst(newHead);
            if (grow) {
                score += 10;
                best = Math.max(best, score);
                stepSeconds = Math.max(MIN_STEP, stepSeconds - SPEED_UP);
                spawnFood();
            } else {
                snake.removeLast();
            }
        }

        private void handleKey(int code) {
            Dir d = switch (code) {
                case KeyEvent.VK_UP, KeyEvent.VK_W -> Dir.UP;
                case KeyEvent.VK_DOWN, KeyEvent.VK_S -> Dir.DOWN;
                case KeyEvent.VK_LEFT, KeyEvent.VK_A -> Dir.LEFT;
                case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> Dir.RIGHT;
                default -> null;
            };

            if (d != null) {
                if (state == State.OVER || state == State.WIN) {
                    return;
                }
                if (state == State.READY) {
                    state = State.RUNNING;
                    accumulator = 0;
                }
                Dir last = pending.peekLast() != null ? pending.peekLast() : dir;
                if (!d.isOpposite(last) && d != last && pending.size() < 3) {
                    pending.addLast(d);
                }
                return;
            }

            switch (code) {
                case KeyEvent.VK_SPACE -> {
                    if (state == State.RUNNING) {
                        state = State.PAUSED;
                    } else if (state == State.PAUSED) {
                        state = State.RUNNING;
                        accumulator = 0;
                    }
                }
                case KeyEvent.VK_R -> reset();
                default -> { }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            paintBackground(g2);
            paintFood(g2);
            paintSnake(g2);
            paintHud(g2);
            paintOverlay(g2);

            g2.dispose();
        }

        private void paintBackground(Graphics2D g2) {
            g2.setPaint(new GradientPaint(0, HUD, BG_TOP, 0, HUD + BOARD_H, BG_BOTTOM));
            g2.fillRect(0, 0, BOARD_W, BOARD_H + HUD);

            g2.setColor(GRID);
            g2.setStroke(new BasicStroke(1f));
            for (int x = 1; x < COLS; x++) {
                g2.drawLine(x * CELL, HUD, x * CELL, HUD + BOARD_H);
            }
            for (int y = 1; y < ROWS; y++) {
                g2.drawLine(0, HUD + y * CELL, BOARD_W, HUD + y * CELL);
            }

            g2.setColor(BORDER);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(1, HUD + 1, BOARD_W - 3, BOARD_H - 3);
        }

        private void paintSnake(Graphics2D g2) {
            int index = 0;
            int total = snake.size();
            for (Point p : snake) {
                float t = total <= 1 ? 0f : (float) index / (total - 1);
                Color c = blend(HEAD, TAIL, t);
                int x = p.x * CELL;
                int y = HUD + p.y * CELL;

                // 光晕
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 38));
                g2.fill(new RoundRectangle2D.Float(x - 3f, y - 3f, CELL + 5f, CELL + 5f, 16f, 16f));

                g2.setColor(c);
                g2.fill(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, CELL - 3f, CELL - 3f, 12f, 12f));

                if (index == 0) {
                    paintEyes(g2, x, y);
                }
                index++;
            }
        }

        private void paintEyes(Graphics2D g2, int x, int y) {
            int cx = x + CELL / 2;
            int cy = y + CELL / 2;
            int along = 5;
            int side = 5;
            int ex1;
            int ey1;
            int ex2;
            int ey2;
            switch (dir) {
                case UP -> {
                    ex1 = cx - side; ey1 = cy - along;
                    ex2 = cx + side; ey2 = cy - along;
                }
                case DOWN -> {
                    ex1 = cx - side; ey1 = cy + along;
                    ex2 = cx + side; ey2 = cy + along;
                }
                case LEFT -> {
                    ex1 = cx - along; ey1 = cy - side;
                    ex2 = cx - along; ey2 = cy + side;
                }
                default -> {
                    ex1 = cx + along; ey1 = cy - side;
                    ex2 = cx + along; ey2 = cy + side;
                }
            }
            g2.setColor(new Color(0x0B, 0x11, 0x1C));
            g2.fillOval(ex1 - 3, ey1 - 3, 6, 6);
            g2.fillOval(ex2 - 3, ey2 - 3, 6, 6);
            g2.setColor(Color.WHITE);
            g2.fillOval(ex1 - 1, ey1 - 2, 2, 2);
            g2.fillOval(ex2 - 1, ey2 - 2, 2, 2);
        }

        private void paintFood(Graphics2D g2) {
            if (food.x < 0) {
                return;
            }
            float pulse = (float) (0.5 + 0.5 * Math.sin(animTime * 5.5));
            float cx = food.x * CELL + CELL / 2f;
            float cy = HUD + food.y * CELL + CELL / 2f;
            float radius = CELL * 0.30f + pulse * 2.2f;

            g2.setPaint(new RadialGradientPaint(new Point((int) cx, (int) cy),
                    radius * 2.4f, new float[]{0f, 1f},
                    new Color[]{new Color(229, 58, 58, 80), new Color(229, 58, 58, 0)}));
            g2.fillOval((int) (cx - radius * 2.4f), (int) (cy - radius * 2.4f),
                    (int) (radius * 4.8f), (int) (radius * 4.8f));

            g2.setColor(FOOD);
            g2.fillOval((int) (cx - radius), (int) (cy - radius), (int) (radius * 2), (int) (radius * 2));
            g2.setColor(new Color(255, 255, 255, 190));
            g2.fillOval((int) (cx - radius * 0.45f), (int) (cy - radius * 0.55f),
                    (int) (radius * 0.55f), (int) (radius * 0.55f));
        }

        private void paintHud(Graphics2D g2) {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, BOARD_W, HUD);
            g2.setColor(new Color(0x4C, 0x7C, 0xFF, 120));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(0, HUD - 1, BOARD_W, HUD - 1);

            Font big = new Font("Microsoft YaHei UI", Font.BOLD, 20);
            Font small = new Font("Microsoft YaHei UI", Font.PLAIN, 13);

            g2.setFont(small);
            g2.setColor(MUTED);
            g2.drawString("分数", 20, 24);
            g2.drawString("最高分", 130, 24);
            g2.drawString("长度", 250, 24);

            g2.setFont(big);
            g2.setColor(TEXT);
            g2.drawString(String.valueOf(score), 20, 50);
            g2.setColor(ACCENT_BEST);
            g2.drawString(String.valueOf(best), 130, 50);
            g2.setColor(ACCENT_LEN);
            g2.drawString(String.valueOf(snake.size()), 250, 50);

            String tip = "方向键/WASD 移动   空格 暂停   R 重开";
            g2.setFont(small);
            g2.setColor(MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(tip, BOARD_W - 20 - fm.stringWidth(tip), 38);
        }

        private void paintOverlay(Graphics2D g2) {
            if (state == State.RUNNING) {
                return;
            }
            g2.setColor(new Color(255, 255, 255, 220));
            g2.fillRect(0, HUD, BOARD_W, BOARD_H);

            String title;
            String sub;
            Color titleColor = TEXT;
            switch (state) {
                case READY -> {
                    title = "贪 吃 蛇";
                    sub = "按 方向键 或 WASD 开始游戏";
                    titleColor = new Color(0x17, 0xA3, 0x4A);
                }
                case PAUSED -> {
                    title = "已 暂 停";
                    sub = "按 空格 继续";
                    titleColor = new Color(0xC4, 0x86, 0x00);
                }
                case OVER -> {
                    title = "游 戏 结 束";
                    sub = "本局得分 " + score + "    按 R 重新开始";
                    titleColor = new Color(0xD9, 0x33, 0x33);
                }
                case WIN -> {
                    title = "通 关 啦";
                    sub = "你占满了整个棋盘    按 R 再来一局";
                    titleColor = new Color(0x1D, 0x6F, 0xD0);
                }
                default -> {
                    return;
                }
            }

            int cx = BOARD_W / 2;
            int cy = HUD + BOARD_H / 2;

            Font titleFont = new Font("Microsoft YaHei UI", Font.BOLD, 40);
            Font subFont = new Font("Microsoft YaHei UI", Font.PLAIN, 15);
            FontMetrics tfm = getFontMetrics(titleFont);
            FontMetrics sfm = getFontMetrics(subFont);

            g2.setFont(titleFont);
            g2.setColor(titleColor);
            g2.drawString(title, cx - tfm.stringWidth(title) / 2, cy - 6);

            g2.setFont(subFont);
            g2.setColor(new Color(0x3A, 0x46, 0x58));
            g2.drawString(sub, cx - sfm.stringWidth(sub) / 2, cy + 34);
        }

        private static Color blend(Color from, Color to, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return new Color(
                    Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                    Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                    Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t));
        }
    }
}
