package com.gameexpert.engine.redstone;

import static com.gameexpert.terrain.Blocks.RAIL;
import static com.gameexpert.engine.redstone.RedstoneState.isRedstoneRail;

import java.util.ArrayList;
import java.util.List;

/** [REDSTONE] 바닐라 RailState. 정적판 RedstoneRailShape와 동일한 연결·분기·경사 순서. */
public final class RedstoneRailShape {
    private final RedstoneEngine world;
    private final int x, y, z, id;
    private final boolean straight;
    private int state;
    private List<int[]> connections = new ArrayList<>();

    public RedstoneRailShape(RedstoneEngine world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.id = world.blockAt(x, y, z);
        this.state = world.stateAt(x, y, z);
        this.straight = this.id != RAIL;
        updateConnections(shape());
    }

  private int shape() { return this.state & (this.straight ? 7 : 15); }
  private boolean railAt(int x, int y, int z) {
    int id = this.world.blockAt(x, y, z);
    return id == RAIL || isRedstoneRail(id);
  }
  private RedstoneRailShape getRail(int x, int y, int z) {
    for (int dy : new int[] {0, 1, -1}) {
      if (this.railAt(x, y + dy, z)) return new RedstoneRailShape(this.world, x, y + dy, z);
    }
    return null;
  }
  private void updateConnections(int shape) {

    switch (shape) {
      case 0: this.connections = new ArrayList<>(List.of(new int[] {x, y, z - 1}, new int[] {x, y, z + 1})); break;
      case 1: this.connections = new ArrayList<>(List.of(new int[] {x - 1, y, z}, new int[] {x + 1, y, z})); break;
      case 2: this.connections = new ArrayList<>(List.of(new int[] {x - 1, y, z}, new int[] {x + 1, y + 1, z})); break;
      case 3: this.connections = new ArrayList<>(List.of(new int[] {x - 1, y + 1, z}, new int[] {x + 1, y, z})); break;
      case 4: this.connections = new ArrayList<>(List.of(new int[] {x, y + 1, z - 1}, new int[] {x, y, z + 1})); break;
      case 5: this.connections = new ArrayList<>(List.of(new int[] {x, y, z - 1}, new int[] {x, y + 1, z + 1})); break;
      case 6: this.connections = new ArrayList<>(List.of(new int[] {x + 1, y, z}, new int[] {x, y, z + 1})); break;
      case 7: this.connections = new ArrayList<>(List.of(new int[] {x - 1, y, z}, new int[] {x, y, z + 1})); break;
      case 8: this.connections = new ArrayList<>(List.of(new int[] {x - 1, y, z}, new int[] {x, y, z - 1})); break;
      case 9: this.connections = new ArrayList<>(List.of(new int[] {x + 1, y, z}, new int[] {x, y, z - 1})); break;
    }
  }
  private boolean hasConnection(int x, int z) {
    for (int[] p : this.connections) if (p[0] == x && p[2] == z) return true;
    return false;
  }
  private void removeSoftConnections() {
    for (int i = 0; i < this.connections.size(); i++) {
      int[] p = this.connections.get(i);
      RedstoneRailShape rail = this.getRail(p[0], p[1], p[2]);
      if (rail != null && rail.hasConnection(this.x, this.z)) {
        this.connections.set(i, new int[] {rail.x, rail.y, rail.z});
      } else this.connections.remove(i--);
    }
  }
  private boolean canConnectTo(RedstoneRailShape other) {
    return this.hasConnection(other.x, other.z) || this.connections.size() != 2;
  }
  public int countPotentialConnections() {
    int count = 0;
    for (int[] d : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
      int dx = d[0], dz = d[1];
      if (this.getRail(this.x + dx, this.y, this.z + dz) != null) count++;
    }
    return count;
  }
  private boolean hasNeighborRail(int x, int z) {
    RedstoneRailShape other = this.getRail(x, this.y, z);
    if (other == null) return false;
    other.removeSoftConnections();
    return other.canConnectTo(this);
  }
  private int ascend(int shape) {
    if (shape == 0) {
      if (this.railAt(this.x, this.y + 1, this.z - 1)) shape = 4;
      if (this.railAt(this.x, this.y + 1, this.z + 1)) shape = 5;
    } else if (shape == 1) {
      if (this.railAt(this.x + 1, this.y + 1, this.z)) shape = 2;
      if (this.railAt(this.x - 1, this.y + 1, this.z)) shape = 3;
    }
    return shape;
  }
  private void writeShape(int shape) {
    this.state = (this.state & ~(this.straight ? 7 : 15)) | shape;
    this.world.setBlock(this.x, this.y, this.z, this.id, this.state, 3);
  }
  private void connectTo(RedstoneRailShape other) {
    this.connections.add(new int[] {other.x, other.y, other.z});
    boolean n = this.hasConnection(this.x, this.z - 1);
    boolean s = this.hasConnection(this.x, this.z + 1);
    boolean w = this.hasConnection(this.x - 1, this.z);
    boolean e = this.hasConnection(this.x + 1, this.z);
    int shape = -1;
    if (n || s) shape = 0;
    if (w || e) shape = 1;
    if (!this.straight) {
      if (s && e && !n && !w) shape = 6;
      if (s && w && !n && !e) shape = 7;
      if (n && w && !s && !e) shape = 8;
      if (n && e && !s && !w) shape = 9;
    }
    shape = this.ascend(shape);
    this.writeShape(shape < 0 ? 0 : shape);
  }
  public int place(boolean hasSignal, boolean first, int defaultShape) {
    boolean n = this.hasNeighborRail(this.x, this.z - 1);
    boolean s = this.hasNeighborRail(this.x, this.z + 1);
    boolean w = this.hasNeighborRail(this.x - 1, this.z);
    boolean e = this.hasNeighborRail(this.x + 1, this.z);
    int shape = -1;
    boolean ns = n || s, we = w || e;
    if (ns && !we) shape = 0;
    if (we && !ns) shape = 1;
    if (!this.straight) {
      if (s && e && !n && !w) shape = 6;
      if (s && w && !n && !e) shape = 7;
      if (n && w && !s && !e) shape = 8;
      if (n && e && !s && !w) shape = 9;
    }
    if (shape < 0) {
      if (ns && we) shape = defaultShape;
      else if (ns) shape = 0;
      else if (we) shape = 1;
      if (!this.straight) {
        if (hasSignal) {
          if (s && e) shape = 6;
          if (s && w) shape = 7;
          if (n && e) shape = 9;
          if (n && w) shape = 8;
        } else {
          if (n && w) shape = 8;
          if (n && e) shape = 9;
          if (s && w) shape = 7;
          if (s && e) shape = 6;
        }
      }
    }
    shape = this.ascend(shape);
    if (shape < 0) shape = defaultShape;
    this.updateConnections(shape);
    int next = (this.state & ~(this.straight ? 7 : 15)) | shape;
    if (first || this.world.stateAt(this.x, this.y, this.z) != next) {
      this.writeShape(shape);
      for (int[] p : this.connections) {
        RedstoneRailShape other = this.getRail(p[0], p[1], p[2]);
        if (other != null) {
          other.removeSoftConnections();
          if (other.canConnectTo(this)) other.connectTo(this);
        }
      }
    }
    return this.state;
  }
}
