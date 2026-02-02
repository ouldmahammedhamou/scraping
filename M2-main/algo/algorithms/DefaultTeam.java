package algorithms;

import java.awt.Point;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 计算几何图（阈值连边）上的支配集的贪心启发式实现。
 * - 小规模：O(n^2) 构图
 * - 大规模：网格划分（spatial hashing）加速近邻搜索
 * - 覆盖计算使用 BitSet，优先队列懒更新
 */
public class DefaultTeam {

  public ArrayList<Point> calculDominatingSet(ArrayList<Point> points, int edgeThreshold) {
    if (points == null || points.isEmpty()) return new ArrayList<>();

    final int n = points.size();
    final long D2 = 1L * edgeThreshold * edgeThreshold;

    // 选择构图策略：经验阈值，可按需要调整
    final boolean useGrid = n >= 4000;
    BitSet[] neighbors = useGrid
        ? buildAdjacencyWithGrid(points, edgeThreshold)     // 更适合中大规模
        : buildAdjacencyQuadratic(points, D2);              // 小规模简单直接

    return greedyDominatingSet(points, neighbors);
  }

  /* ===================== 贪心主流程（BitSet + 懒更新堆） ===================== */

  private static class Node {
    final int id;
    final int gain; // 当时估计能覆盖的“未覆盖顶点”数量
    Node(int id, int gain) { this.id = id; this.gain = gain; }
  }

  private static ArrayList<Point> greedyDominatingSet(List<Point> pts, BitSet[] neighbors) {
    final int n = pts.size();
    ArrayList<Point> result = new ArrayList<>();
    BitSet uncovered = new BitSet(n);
    uncovered.set(0, n); // 初始全部未覆盖

    // 最大堆：按覆盖增益降序
    PriorityQueue<Node> pq = new PriorityQueue<>(Comparator.comparingInt((Node a) -> a.gain).reversed());
    for (int i = 0; i < n; i++) {
      // 初始增益即该点的闭邻域大小
      pq.add(new Node(i, neighbors[i].cardinality()));
    }

    while (uncovered.cardinality() > 0 && !pq.isEmpty()) {
      Node cur = pq.poll();
      int id = cur.id;

      int actual = intersectionCardinality(neighbors[id], uncovered);
      if (actual == 0) continue; // 该点此刻不再能带来新覆盖，跳过
      if (actual < cur.gain) {
        // 懒更新：数值过期，更新后重新入堆
        pq.add(new Node(id, actual));
        continue;
      }

      // 接受该点
      result.add(pts.get(id));
      // 从未覆盖集合中移除它所能覆盖的所有点（闭邻域）
      uncovered.andNot(neighbors[id]);
    }
    return result;
  }

  // 计算 BitSet 交集基数（复制一份后 AND，再数位数）
  private static int intersectionCardinality(BitSet a, BitSet b) {
    BitSet t = (BitSet) a.clone();
    t.and(b);
    return t.cardinality();
  }

  /* ===================== 构图：O(n^2) 版本 ===================== */

  private static BitSet[] buildAdjacencyQuadratic(List<Point> pts, long D2) {
    final int n = pts.size();
    BitSet[] adj = new BitSet[n];
    for (int i = 0; i < n; i++) {
      adj[i] = new BitSet(n);
      adj[i].set(i); // 闭邻域包含自己（支配集定义：点在集内则自我覆盖）
    }
    for (int i = 0; i < n; i++) {
      final int xi = pts.get(i).x;
      final int yi = pts.get(i).y;
      for (int j = i + 1; j < n; j++) {
        final long dx = (long) xi - (long) pts.get(j).x;
        final long dy = (long) yi - (long) pts.get(j).y;
        final long d2 = dx * dx + dy * dy;
        if (d2 <= D2) {
          adj[i].set(j);
          adj[j].set(i);
        }
      }
    }
    return adj;
  }

  /* ===================== 构图：网格加速版本（适合中大规模） ===================== */

  private static BitSet[] buildAdjacencyWithGrid(List<Point> pts, int edgeThreshold) {
    final int n = pts.size();
    final long D2 = 1L * edgeThreshold * edgeThreshold;
    final int cell = Math.max(edgeThreshold, 1); // 单元格边长

    BitSet[] adj = new BitSet[n];
    for (int i = 0; i < n; i++) {
      adj[i] = new BitSet(n);
      adj[i].set(i); // 闭邻域包含自己
    }

    // 网格：key = (gx, gy) 打包到 long
    Map<Long, ArrayList<Integer>> grid = new HashMap<>(n * 2);
    int[] gx = new int[n], gy = new int[n];

    for (int i = 0; i < n; i++) {
      int x = pts.get(i).x, y = pts.get(i).y;
      gx[i] = Math.floorDiv(x, cell);
      gy[i] = Math.floorDiv(y, cell);
      long key = pack(gx[i], gy[i]);
      grid.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
    }

    for (int i = 0; i < n; i++) {
      final int xi = pts.get(i).x, yi = pts.get(i).y;
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          long key = pack(gx[i] + dx, gy[i] + dy);
          ArrayList<Integer> bucket = grid.get(key);
          if (bucket == null) continue;
          for (int j : bucket) {
            if (j <= i) continue; // 避免重复/自比较
            final long ddx = (long) xi - (long) pts.get(j).x;
            final long ddy = (long) yi - (long) pts.get(j).y;
            final long d2 = ddx * ddx + ddy * ddy;
            if (d2 <= D2) {
              adj[i].set(j);
              adj[j].set(i);
            }
          }
        }
      }
    }
    return adj;
  }

  private static long pack(int a, int b) {
    return (((long) a) << 32) ^ (b & 0xffffffffL);
  }

  /* ===================== 文件读写（更稳的实现，可选） ===================== */

  private void saveToFile(String baseFilename, ArrayList<Point> result) {
    int index = 0;
    while (true) {
      String name = baseFilename + index + ".points";
      Path path = Paths.get(name);
      if (!Files.exists(path)) {
        printToFile(name, result);
        return;
      }
      index++;
    }
  }

  private void printToFile(String filename, ArrayList<Point> points) {
    try (PrintStream output = new PrintStream(new FileOutputStream(filename))) {
      for (Point p : points) {
        output.println(p.x + " " + p.y);
      }
    } catch (FileNotFoundException e) {
      System.err.println("I/O exception: unable to create " + filename);
    }
  }

  private ArrayList<Point> readFromFile(String filename) {
    ArrayList<Point> points = new ArrayList<>();
    try (BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(filename)))) {
      String line;
      while ((line = input.readLine()) != null) {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length >= 2) {
          int x = Integer.parseInt(tokens[0]);
          int y = Integer.parseInt(tokens[1]);
          points.add(new Point(x, y));
        }
      }
    } catch (FileNotFoundException e) {
      System.err.println("Input file not found: " + filename);
    } catch (IOException e) {
      System.err.println("Exception: interrupted I/O.");
    }
    return points;
  }
}
