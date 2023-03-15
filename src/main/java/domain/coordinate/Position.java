package domain.coordinate;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Position {

    private static final List<List<Position>> CACHE;

    private final int x;
    private final int y;

    static {
        CACHE = new ArrayList<>();
        addColumn();
    }

    private Position(final int x, final int y) {
        this.x = x;
        this.y = y;
    }

    public static Position of(final int x, final int y) {
        return CACHE.get(y)
                .get(x);
    }

    private static void addColumn() {
        for (int y = 0; y < 8; y++) {
            CACHE.add(new ArrayList<>());
            addRow(y);
        }
    }

    private static void addRow(final int y) {
        for (int x = 0; x < 8; x++) {
            CACHE.get(y).add(new Position(x, y));
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Position position = (Position) o;
        return x == position.x && y == position.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

}
